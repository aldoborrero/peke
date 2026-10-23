package dev.aldoborrero.peke

import android.service.dreams.DreamService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.datastore.core.DataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import dev.aldoborrero.peke.data.RestoredSession
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.services.ScreensaverService
import dev.aldoborrero.peke.services.hilt.AuthOkHttpClient
import dev.aldoborrero.peke.ui.CoilConfig
import dev.aldoborrero.peke.ui.components.AppScreensaverContent
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.util.ProvideLocalClock
import kotlinx.coroutines.flow.collectLatest
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class PekeDreamService :
    DreamService(),
    SavedStateRegistryOwner {
    @Inject
    lateinit var serverRepository: ServerRepository

    @Inject
    lateinit var screensaverService: ScreensaverService

    @Inject
    lateinit var preferencesDataStore: DataStore<AppPreferences>

    @AuthOkHttpClient
    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val lifecycleRegistry = LifecycleRegistry(this)

    private val savedStateRegistryController =
        SavedStateRegistryController.create(this).apply {
            performAttach()
        }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate")

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleScope.launchDefault {
            if (serverRepository.current.value == null) {
                val result = serverRepository.restoreLastSession()
                if (result !is RestoredSession.Success) {
                    Timber.w("Could not restore user session")
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Timber.d("onAttachedToWindow")
        val itemFlow = screensaverService.createItemFlow(lifecycleScope)
        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@PekeDreamService)
                setViewTreeSavedStateRegistryOwner(this@PekeDreamService)
                setContent {
                    val user by serverRepository.currentUserFlow.collectAsState(null)
                    if (user != null) {
                        var prefs by remember { mutableStateOf<AppPreferences?>(null) }
                        LaunchedEffect(Unit) {
                            preferencesDataStore.data.collectLatest { prefs = it }
                        }
                        prefs?.let { prefs ->
                            CoilConfig(
                                prefs = prefs,
                                okHttpClient = okHttpClient,
                                debugLogging = false,
                                enableCache = true,
                            )
                            PekeTheme(appThemeColors = prefs.interfacePreferences.appThemeColors) {
                                ProvideLocalClock {
                                    val screensaverPrefs = prefs.interfacePreferences.screensaverPreference
                                    val currentItem by itemFlow.collectAsState(null)
                                    Box(Modifier.fillMaxSize()) {
                                        AppScreensaverContent(
                                            currentItem = currentItem,
                                            showClock = screensaverPrefs.showClock,
                                            duration = screensaverPrefs.duration.milliseconds,
                                            animate = screensaverPrefs.animate,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        if (screensaverPrefs.dimEnabled) {
                                            val alpha = screensaverPrefs.dimPercent / 100f
                                            Box(
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = alpha)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        Timber.d("onDreamingStarted")
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        Timber.d("onDreamingStopped")
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
