package dev.aldoborrero.peke.ui.main

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.data.model.HomeRowConfig
import dev.aldoborrero.peke.data.model.ServerUserConfig
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.HomePagePreferences
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.DatePlayedService
import dev.aldoborrero.peke.services.FavoriteWatchManager
import dev.aldoborrero.peke.services.HomePageResolvedSettings
import dev.aldoborrero.peke.services.HomeRowConfigDisplay
import dev.aldoborrero.peke.services.HomeSettingsService
import dev.aldoborrero.peke.services.LatestNextUpService
import dev.aldoborrero.peke.services.MediaManagementService
import dev.aldoborrero.peke.services.NavDrawerService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.ServerReportService
import dev.aldoborrero.peke.services.UserPreferencesService
import dev.aldoborrero.peke.services.deleteItem
import dev.aldoborrero.peke.ui.collectLatestIn
import dev.aldoborrero.peke.ui.combinePair
import dev.aldoborrero.peke.ui.data.RowColumn
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.ui.main.settings.Library
import dev.aldoborrero.peke.ui.showToast
import dev.aldoborrero.peke.util.ExceptionHandler
import dev.aldoborrero.peke.util.HomeRowLoadingState
import dev.aldoborrero.peke.util.LoadingState
import dev.aldoborrero.peke.util.PekeDispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/** Away from the home this long, it's read again whole on return, see [HomeViewModel.refreshWatching] */
private val RELOAD_AFTER = 30.minutes

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val serverReportService: ServerReportService,
        private val navDrawerService: NavDrawerService,
        private val homeSettingsService: HomeSettingsService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        private val userPreferencesService: UserPreferencesService,
        private val mediaManagementService: MediaManagementService,
        private val latestNextUpService: LatestNextUpService,
        private val datePlayedService: DatePlayedService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeState.EMPTY)
        val state: StateFlow<HomeState> = _state

        private var dataLoadingJob: Job? = null

        /** The libraries the last load used, so a refresh of the watching rows doesn't read them again */
        @Volatile private var libraries: List<Library>? = null

        /** Whether the home was left since it last started, see [refreshWatching] */
        private var left = false

        /** When the home was last left, on the elapsed realtime clock, and on which day, see [refreshWatching] */
        private var leftAt = 0L
        private var leftOn: LocalDate? = null

        init {
            serverRepository.currentUserDtoFlow
                .combinePair(homeSettingsService.currentSettings)
                .collectLatestIn(viewModelScope) { (userDto, settings) ->
                    Timber.v(
                        "Got new userDto & settings, userId=%s, settings?=%s",
                        userDto?.id,
                        settings != HomePageResolvedSettings.EMPTY,
                    )
//                    Timber.v("userDto=%s", userDto)
                    dataLoadingJob?.cancel()
                    if (userDto == null) {
                        Timber.d("UserDto is null")
                        _state.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (settings == HomePageResolvedSettings.EMPTY) {
                        Timber.d("Home settings are empty")
                        _state.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (userDto.id != settings.userId) {
                        Timber.d("User IDs don't match: %s vs %s", userDto?.id, settings.userId)
                        _state.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (state.value.settings.userId != settings.userId) {
                        Timber.d("User changed")
                        _state.update { HomeState.EMPTY }
                    }
                    dataLoadingJob =
                        viewModelScope.launchIO {
                            loadHomeRows(userDto, settings)
                        }
                }
        }

        fun init() {
            viewModelScope.launchIO {
                Timber.d("init HomeViewModel")
                serverRepository.currentUserDto?.let { userDto ->
                    if (dataLoadingJob?.isActive == false) {
                        val settings =
                            homeSettingsService.currentSettings.first { it != HomePageResolvedSettings.EMPTY }
                        if (userDto.id == settings.userId) {
                            dataLoadingJob =
                                viewModelScope.launchIO {
                                    loadHomeRows(userDto, settings)
                                }
                        } else {
                            Timber.d(
                                "Init user IDs don't match: %s vs %s",
                                userDto.id,
                                settings.userId,
                            )
                        }
                    } else {
                        Timber.v("Data loading job is active")
                    }
                }
            }
        }

        /**
         * The home's page was left (another page opened, or the app went to the background)
         */
        fun onLeft() {
            left = true
            leftAt = SystemClock.elapsedRealtime()
            leftOn = LocalDate.now()
        }

        /**
         * Coming back to the home: after a short while only the rows of what is being watched can have changed, so
         * only they are read again. After [RELOAD_AFTER] away, or on another day, every row is read again, and the
         * dates played too: new items, other suggestions. Without a loaded home this is [init]. The home's first start
         * isn't a return: the load is already running or has just finished.
         */
        fun refreshWatching() {
            if (!left) return
            left = false
            val longAway =
                SystemClock.elapsedRealtime() - leftAt > RELOAD_AFTER.inWholeMilliseconds || leftOn != LocalDate.now()
            viewModelScope.launchIO {
                val userDto = serverRepository.currentUserDto ?: return@launchIO
                val current = state.value
                if (current.loadingState != LoadingState.Success || current.settings.userId != userDto.id) {
                    init()
                    return@launchIO
                }
                if (longAway) {
                    Timber.d("Back after a long time: reloading the whole home")
                    datePlayedService.invalidateAll()
                    dataLoadingJob?.cancel()
                    dataLoadingJob = viewModelScope.launchIO { loadHomeRows(userDto, current.settings) }
                    return@launchIO
                }
                if (dataLoadingJob?.isActive == true) return@launchIO
                dataLoadingJob =
                    viewModelScope.launchIO {
                        val prefs = userPreferencesService.getCurrent().appPreferences.homePagePreferences
                        val libraries =
                            libraries ?: navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)
                        current.settings.rows
                            .withIndex()
                            .filter { isWatchingRow(it.value.config) }
                            .map { (index, row) ->
                                async(PekeDispatchers.IO) {
                                    val rowData = fetchRow(row, prefs, userDto, libraries, refresh = true)
                                    _state.update { state ->
                                        if (state.settings != current.settings) return@update state
                                        state.copy(homeRows = state.homeRows.toMutableList().apply { set(index, rowData) })
                                    }
                                }
                            }.awaitAll()
                    }
            }
        }

        private suspend fun fetchRow(
            row: HomeRowConfigDisplay,
            prefs: HomePagePreferences,
            userDto: ServerUserConfig,
            libraries: List<Library>,
            refresh: Boolean,
        ): HomeRowLoadingState {
            Timber.v("Fetching row: %s", row)
            return try {
                homeSettingsService.fetchDataForRow(
                    row = row.config,
                    scope = viewModelScope,
                    prefs = prefs,
                    userDto = userDto,
                    libraries = libraries,
                    limit = prefs.maxItemsPerRow,
                    isRefresh = refresh,
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: InvalidStatusException) {
                if (ex.status == 404) {
                    Timber.w(ex, "404 on row %s", row)
                    HomeRowLoadingState.Success(
                        row.title,
                        emptyList(),
                    )
                } else {
                    Timber.e(
                        ex,
                        "Error %s on row %s",
                        ex.status,
                        row,
                    )
                    HomeRowLoadingState.Error(
                        row.title,
                        exception = ex,
                    )
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error on row %s", row)
                HomeRowLoadingState.Error(
                    row.title,
                    exception = ex,
                )
            }
        }

        suspend fun loadHomeRows(
            userDto: ServerUserConfig,
            settings: HomePageResolvedSettings,
        ) {
            Timber.i("Starting loadHomeRows")
            try {
                val preferences = userPreferencesService.getCurrent()
                val prefs = preferences.appPreferences.homePagePreferences

                val state = state.value

                // The libraries the settings were built with serve only their first load: a later load of the same
                // settings comes from a change of the user (their policy, live TV access), so they're read again
                val libraries =
                    settings.libraries?.takeIf { state.settings !== settings }
                        ?: navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)
                this.libraries = libraries

                // Refreshing if a load has already occurred and the rows haven't significantly changed
                val refresh =
                    state.loadingState == LoadingState.Success && state.settings == settings
                Timber.v(
                    "refresh=%s, state.loadingState=%s, %s rows",
                    refresh,
                    state.loadingState,
                    settings.rows.size,
                )
                _state.update {
                    it.copy(
                        loadingState = if (refresh) LoadingState.Success else LoadingState.Loading,
                        refreshState = LoadingState.Loading,
                        settings = settings,
                        homeRows =
                            if (refresh) {
                                it.homeRows
                            } else {
                                settings.rows.map { row -> HomeRowLoadingState.Pending(row.title) }
                            },
                    )
                }

                val semaphore = Semaphore(4)

                val deferred =
                    settings.rows
                        .map { row ->
                            viewModelScope.async(PekeDispatchers.IO) {
                                semaphore.withPermit {
                                    fetchRow(row, prefs, userDto, libraries, refresh)
                                }
                            }
                        }

                // Replace rows as they complete; a first load shows the home with its first row
                val remaining = deferred.withIndex().toMutableList()
                while (remaining.isNotEmpty()) {
                    val (rowIndex, rowData) =
                        select {
                            // "Return" the first remaining that is completed
                            remaining
                                .forEach { (rowIndex, deferred) ->
                                    deferred.onAwait { rowIndex to it }
                                }
                        }
                    Timber.v("Got row data index=%s", rowIndex)
                    remaining.removeIf { it.index == rowIndex }
                    _state.update { state ->
                        val newRows =
                            state.homeRows.toMutableList().apply {
                                set(rowIndex, rowData)
                            }
                        state.copy(
                            loadingState = LoadingState.Success,
                            homeRows = newRows,
                        )
                    }
                }
                _state.update {
                    it.copy(
                        loadingState = LoadingState.Success,
                        refreshState = LoadingState.Success,
                    )
                }
                Timber.d("Home page load complete")
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Exception during home page loading")
                if (state.value.loadingState == LoadingState.Success) {
                    showToast(context, "Error refreshing home: ${ex.localizedMessage}")
                    _state.update { it.copy(refreshState = LoadingState.Error(ex)) }
                } else {
                    _state.update {
                        it.copy(loadingState = LoadingState.Error(ex))
                    }
                }
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            withContext(PekeDispatchers.Main) {
                init()
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + PekeDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            withContext(PekeDispatchers.Main) {
                init()
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        fun deleteItem(
            position: RowColumn,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    val row = state.value.homeRows.getOrNull(position.row)
                    if (row is HomeRowLoadingState.Success) {
                        _state.update {
                            val newRow =
                                row.items.toMutableList().apply {
                                    removeAt(position.column)
                                }
                            it.copy(
                                homeRows =
                                    it.homeRows.toMutableList().apply {
                                        set(position.row, row.copy(items = newRow))
                                    },
                            )
                        }
                    }
                }
            }
        }

        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        fun removeFromNextUp(item: BaseItem) {
            if (item.type == BaseItemKind.EPISODE) {
                viewModelScope.launchDefault {
                    serverRepository.currentUser?.id?.let { userId ->
                        latestNextUpService.removeFromNextUp(userId, item)
                        init()
                    }
                }
            } else {
                Timber.w("Item is not an episode %s", item.id)
            }
        }
    }

data class HomeState(
    val loadingState: LoadingState,
    val refreshState: LoadingState,
    val homeRows: List<HomeRowLoadingState>,
    val settings: HomePageResolvedSettings,
) {
    companion object {
        val EMPTY =
            HomeState(
                LoadingState.Pending,
                LoadingState.Pending,
                emptyList(),
                HomePageResolvedSettings.EMPTY,
            )
    }
}

/**
 * Whether a row is a "is watching" type
 */
private fun isWatchingRow(row: HomeRowConfig) =
    row is HomeRowConfig.ContinueWatching ||
        row is HomeRowConfig.NextUp ||
        row is HomeRowConfig.ContinueWatchingCombined
