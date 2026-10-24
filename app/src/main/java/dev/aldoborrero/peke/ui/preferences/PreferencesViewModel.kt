package dev.aldoborrero.peke.ui.preferences

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.setup.VoiceSearchSetting
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.resetSubtitles
import dev.aldoborrero.peke.preferences.updateSubtitlePreferences
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.ProfilePolicy
import dev.aldoborrero.peke.services.Release
import dev.aldoborrero.peke.services.ScreensaverService
import dev.aldoborrero.peke.services.ServerReportService
import dev.aldoborrero.peke.services.UpdateChecker
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.util.DataLoadingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        val preferenceDataStore: DataStore<AppPreferences>,
        val navigationManager: NavigationManager,
        val backdropService: BackdropService,
        val screensaverService: ScreensaverService,
        private val serverRepository: ServerRepository,
        private val updateChecker: UpdateChecker,
        private val serverReportService: ServerReportService,
        profilePolicy: ProfilePolicy,
        val voiceSearchSetting: VoiceSearchSetting,
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
        val currentServer =
            serverRepository.currentServerFlow.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                serverRepository.currentServer,
            )
        val currentUserDto =
            serverRepository.currentUserDtoFlow.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                null,
            )

        val releaseNotes = MutableStateFlow<DataLoadingState<Release>>(DataLoadingState.Pending)

        /**
         * Voice search for kids, set only once the grown-up allowed the microphone (spec 005 FR-007)
         */
        fun setKidsVoiceSearch(on: Boolean) {
            viewModelScope.launchIO { voiceSearchSetting.set(on) }
        }

        fun sendAppLogs() {
            viewModelScope.launchIO { serverReportService.sendAppLogs() }
        }

        fun resetSubtitleSettings() {
            viewModelScope.launchIO {
                resetSubtitleSettings(preferenceDataStore)
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
