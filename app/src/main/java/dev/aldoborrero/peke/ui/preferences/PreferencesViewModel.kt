package dev.aldoborrero.peke.ui.preferences

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.resetSubtitles
import dev.aldoborrero.peke.preferences.updateSubtitlePreferences
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.ProfilePolicy
import dev.aldoborrero.peke.services.Release
import dev.aldoborrero.peke.services.ScreensaverService
import dev.aldoborrero.peke.services.SeerrServerRepository
import dev.aldoborrero.peke.services.ServerReportService
import dev.aldoborrero.peke.services.UpdateChecker
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.util.DataLoadingState
import dev.aldoborrero.peke.util.ExceptionHandler
import dev.aldoborrero.peke.util.LoadingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        val preferenceDataStore: DataStore<AppPreferences>,
        val navigationManager: NavigationManager,
        val backdropService: BackdropService,
        val screensaverService: ScreensaverService,
        private val serverRepository: ServerRepository,
        private val seerrServerRepository: SeerrServerRepository,
        private val updateChecker: UpdateChecker,
        private val serverReportService: ServerReportService,
        profilePolicy: ProfilePolicy,
    ) : ViewModel() {
        /**
         * Whether the current server offers kids mode, which needs the Jellyfin Kids plugin
         */
        val kidsAvailable =
            profilePolicy.settingsAvailable.stateIn(viewModelScope, SharingStarted.Eagerly, false)

        val currentUser =
            serverRepository.currentUserFlow.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null,
            )
        val currentUserDto =
            serverRepository.currentUserDtoFlow.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null,
            )

        val seerrConnection = seerrServerRepository.connection

        private val _quickConnectStatus = MutableStateFlow<LoadingState>(LoadingState.Pending)
        val quickConnectStatus: StateFlow<LoadingState> = _quickConnectStatus

        val releaseNotes = MutableStateFlow<DataLoadingState<Release>>(DataLoadingState.Pending)

        val externalPlayers = MutableStateFlow<List<ExternalPlayerApp>>(emptyList())

        init {
            viewModelScope.launchIO {
                val fakeIntent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType("https://damontecres.com/video.mp4".toUri(), "video/*")
                    }
                val externalPlayers = getExternalPlayers(context)
                val systemDefault =
                    ExternalPlayerApp(
                        name = context.getString(R.string.system_default),
                        icon = null,
                        identifier = "",
                    )
                this@PreferencesViewModel.externalPlayers.update { listOf(systemDefault) + externalPlayers }
            }
        }

        fun sendAppLogs() {
            viewModelScope.launchIO { serverReportService.sendAppLogs() }
        }

        fun resetSubtitleSettings() {
            viewModelScope.launchIO {
                resetSubtitleSettings(preferenceDataStore)
            }
        }

        fun setPin(
            user: JellyfinUser,
            pin: String?,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                serverRepository.updateUserAuth(user, pin, false)
            }
        }

        fun setRequireLogin(user: JellyfinUser) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                serverRepository.updateUserAuth(user, null, true)
            }
        }

        fun removeLoginAndPin(user: JellyfinUser) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                serverRepository.updateUserAuth(user, null, false)
            }
        }

        fun resetQuickConnectStatus() {
            _quickConnectStatus.value = LoadingState.Pending
        }

        fun authorizeQuickConnect(code: String) {
            viewModelScope.launchIO {
                _quickConnectStatus.value = LoadingState.Loading
                try {
                    val success = serverRepository.authorizeQuickConnect(code)
                    _quickConnectStatus.value =
                        if (success) {
                            LoadingState.Success
                        } else {
                            LoadingState.Error("Authorization failed")
                        }
                } catch (e: Exception) {
                    _quickConnectStatus.value = LoadingState.Error(e)
                }
            }
        }

        fun fetchReleaseNotes() {
            viewModelScope.launchIO {
                releaseNotes.update { DataLoadingState.Loading }
                try {
                    val release = updateChecker.getRelease(updateChecker.getInstalledVersion())
                    if (release != null) {
                        releaseNotes.update { DataLoadingState.Success(release) }
                    } else {
                        releaseNotes.update { DataLoadingState.Error("Release not found") }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching release")
                    releaseNotes.update { DataLoadingState.Error(ex) }
                }
            }
        }

        companion object {
            suspend fun resetSubtitleSettings(appPreferences: DataStore<AppPreferences>) {
                appPreferences.updateData {
                    it.updateSubtitlePreferences {
                        resetSubtitles()
                    }
                }
            }
        }
    }

data class ExternalPlayerApp(
    val name: String,
    val icon: ImageBitmap?,
    val identifier: String,
)

fun getExternalPlayers(context: Context): List<ExternalPlayerApp> {
    val fakeIntent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType("https://damontecres.com/video.mp4".toUri(), "video/*")
        }
    val externalPlayers =
        context.packageManager
            .queryIntentActivities(fakeIntent, PackageManager.MATCH_ALL)
            .filter { it.priority >= 0 }
            .map {
                val component =
                    ComponentName(
                        it.activityInfo.packageName,
                        it.activityInfo.name,
                    )
                ExternalPlayerApp(
                    name = it.loadLabel(context.packageManager).toString(),
                    icon =
                        it
                            .loadIcon(context.packageManager)
                            .toBitmap()
                            .asImageBitmap(),
                    identifier = component.flattenToString(),
                )
            }
    return externalPlayers
}
