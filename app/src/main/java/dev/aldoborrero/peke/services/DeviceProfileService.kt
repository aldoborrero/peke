package dev.aldoborrero.peke.services

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.preferences.AssPlaybackMode
import dev.aldoborrero.peke.preferences.ExperimentalPreferences
import dev.aldoborrero.peke.preferences.HdrOverrideMode
import dev.aldoborrero.peke.preferences.PlaybackOverrides
import dev.aldoborrero.peke.preferences.enabled
import dev.aldoborrero.peke.preferences.getHdrRangeTypesFor
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.profile.MediaCodecCapabilitiesTest
import dev.aldoborrero.peke.util.profile.createDeviceProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.ServerVersion
import org.jellyfin.sdk.model.api.DeviceProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates and caches the device direct play/transcoding profile sent to the server for ExoPlayer
 */
@Singleton
class DeviceProfileService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        val mediaCodecCapabilitiesTest by lazy {
            // Created lazily below on another thread since it cn take time
            MediaCodecCapabilitiesTest(context)
        }
        private val mutex = Mutex()

        private var configuration: DeviceProfileConfiguration? = null
        private var deviceProfile: DeviceProfile? = null

        suspend fun getOrCreateDeviceProfile(
            appPrefs: AppPreferences,
            serverVersion: ServerVersion?,
        ): DeviceProfile =
            withContext(PekeDispatchers.Default) {
                val prefs = appPrefs.playbackPreferences
                mutex.withLock {
                    val newConfig =
                        DeviceProfileConfiguration(
                            maxBitrate = prefs.maxBitrate.toInt(),
                            overrides = prefs.overrides,
                            experimental = appPrefs.experimentalPreferences,
                            jellyfinTenEleven =
                                serverVersion != null && serverVersion >= ServerVersion(10, 11, 0),
                        )
                    if (deviceProfile == null || this@DeviceProfileService.configuration != newConfig) {
                        this@DeviceProfileService.configuration = newConfig
                        this@DeviceProfileService.deviceProfile =
                            createDeviceProfile(
                                mediaTest = mediaCodecCapabilitiesTest,
                                maxBitrate = newConfig.maxBitrate,
                                isAC3Enabled = newConfig.overrides.ac3Supported,
                                downMixAudio = newConfig.overrides.downmixStereo,
                                assDirectPlay = newConfig.overrides.assPlaybackMode != AssPlaybackMode.ASS_TRANSCODE,
                                pgsDirectPlay = newConfig.overrides.directPlayPgs,
                                decodeAv1 = prefs.overrides.decodeAv1,
                                preferAc3ForSurround = appPrefs.experimentalPreferences.enabled { preferAc3Surround },
                                forceEnabledHdr =
                                    appPrefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_ENABLE, newConfig.jellyfinTenEleven),
                                forceDisabledHdr =
                                    appPrefs.getHdrRangeTypesFor(HdrOverrideMode.HDR_OVERRIDE_DISABLE, newConfig.jellyfinTenEleven),
                                jellyfinTenEleven = newConfig.jellyfinTenEleven,
                                maxResolution = newConfig.overrides.maxResolution,
                            )
                    }
                    this@DeviceProfileService.deviceProfile!!
                }
            }
    }

/**
 * The configuration used in [createDeviceProfile]
 */
data class DeviceProfileConfiguration(
    val maxBitrate: Int,
    val overrides: PlaybackOverrides,
    val experimental: ExperimentalPreferences,
    val jellyfinTenEleven: Boolean,
)
