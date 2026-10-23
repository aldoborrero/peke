package dev.aldoborrero.peke

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavBackStack
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.kids.KidsLockedLaunch
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.LocalKidsLiveTv
import dev.aldoborrero.peke.kids.LocalKidsLocked
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.kids.data.KidsDataCleaner
import dev.aldoborrero.peke.kids.livetv.KidsLiveTvService
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.timer.KidsAllowanceService
import dev.aldoborrero.peke.kids.timer.KidsMusicGuard
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.services.AppUpgradeHandler
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.DatePlayedInvalidationService
import dev.aldoborrero.peke.services.DeviceProfileService
import dev.aldoborrero.peke.services.ImageUrlService
import dev.aldoborrero.peke.services.IntentResult
import dev.aldoborrero.peke.services.IntentService
import dev.aldoborrero.peke.services.LatestNextUpSchedulerService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.PlaybackLifecycleObserver
import dev.aldoborrero.peke.services.RefreshRateService
import dev.aldoborrero.peke.services.ScreensaverService
import dev.aldoborrero.peke.services.ServerEventListener
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.services.SuggestionsSchedulerService
import dev.aldoborrero.peke.services.UpdateChecker
import dev.aldoborrero.peke.services.UserPreferencesService
import dev.aldoborrero.peke.services.UserSwitchListener
import dev.aldoborrero.peke.services.hilt.AuthOkHttpClient
import dev.aldoborrero.peke.ui.CoilConfig
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.collectLatestIn
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.showToast
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.colors.PurpleThemeColors
import dev.aldoborrero.peke.ui.util.ProvideLocalClock
import dev.aldoborrero.peke.util.DebugLogTree
import dev.aldoborrero.peke.util.ExceptionHandler
import dev.aldoborrero.peke.util.PekeDispatchers
import dev.aldoborrero.peke.util.requestSerializersModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityViewModel by viewModels()

    @Inject
    lateinit var userPreferencesDataStore: DataStore<AppPreferences>

    @Inject
    lateinit var userPreferencesService: UserPreferencesService

    @AuthOkHttpClient
    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var navigationManager: NavigationManager

    @Inject
    lateinit var setupNavigationManager: SetupNavigationManager

    @Inject
    lateinit var updateChecker: UpdateChecker

    @Inject
    lateinit var playbackLifecycleObserver: PlaybackLifecycleObserver

    @Inject
    lateinit var imageUrlService: ImageUrlService

    @Inject
    lateinit var refreshRateService: RefreshRateService

    @Inject
    lateinit var userSwitchListener: UserSwitchListener

    @Inject
    lateinit var suggestionsSchedulerService: SuggestionsSchedulerService

    @Inject
    lateinit var latestNextUpSchedulerService: LatestNextUpSchedulerService

    @Inject
    lateinit var backdropService: BackdropService

    // Note: unused but injected to ensure it is created
    @Inject
    lateinit var serverEventListener: ServerEventListener

    // Note: unused but injected to ensure it is created
    @Inject
    lateinit var datePlayedInvalidationService: DatePlayedInvalidationService

    @Inject
    lateinit var screensaverService: ScreensaverService

    @Inject
    lateinit var intentService: IntentService

    @Inject
    lateinit var kidsModeService: KidsModeService

    @Inject
    lateinit var kidsDataCleaner: KidsDataCleaner

    @Inject
    lateinit var kidsPluginService: KidsPluginService

    @Inject
    lateinit var kidsAllowanceService: KidsAllowanceService

    @Inject
    lateinit var kidsMusicGuard: KidsMusicGuard

    @Inject
    lateinit var kidsLiveTvService: KidsLiveTvService

    private var signInAuto = true

    private val json =
        Json {
            classDiscriminator = "_type"
            serializersModule = requestSerializersModule
        }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        Timber.i("MainActivity.onCreate: savedInstanceState is null=${savedInstanceState == null}")
        lifecycle.addObserver(playbackLifecycleObserver)
        lifecycle.addObserver(kidsPluginService)
        lifecycle.addObserver(kidsAllowanceService)
        lifecycle.addObserver(kidsModeService)
        lifecycle.addObserver(kidsMusicGuard)
        if (savedInstanceState == null) lifecycleScope.launch { kidsDataCleaner.sweep() }

        val backStackStr = savedInstanceState?.getString(KEY_BACK_STACK)
        val restoredBackStack =
            if (backStackStr != null) {
                try {
                    json.decodeFromString<List<Destination>>(backStackStr)
                } catch (ex: Exception) {
                    // Best-effort: a previously persisted back stack we can no longer decode
                    // must not crash startup; fall back to a fresh start destination.
                    Timber.w(ex, "Could not restore back stack; starting fresh")
                    null
                }
            } else {
                null
            }
        if (restoredBackStack != null) {
            Timber.d("Restoring back stack")
            var backStack = restoredBackStack
            val lastDest = backStack.lastOrNull()
            if (lastDest.isPlayback) {
                Timber.v("Restoring back stack without playback")
                backStack = backStack.toMutableList().apply { removeAt(lastIndex) }
            }
            navigationManager.backStack = NavBackStack(*backStack.toTypedArray())
        } else {
            navigationManager.backStack = NavBackStack(Destination.Home())
        }

        viewModel.serverRepository.currentUserFlow
            .onEach { user ->
                withContext(PekeDispatchers.Main) {
                    if (user?.hasPin == true) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }.catch { ex ->
                Timber.e(ex, "Error with settings flag secure")
            }.launchIn(lifecycleScope)

        screensaverService.keepScreenOn
            .onEach { keepScreenOn ->
                Timber.v("keepScreenOn: %s", keepScreenOn)
                withContext(PekeDispatchers.Main) {
                    if (keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }.catch { ex ->
                Timber.e(ex, "Error with keepScreenOn")
            }.launchIn(lifecycleScope)

        userPreferencesDataStore.data.collectLatestIn(lifecycleScope) { prefs ->
            signInAuto = prefs.signInAutomatically
        }

        viewModel.appStart(intent)
        setContent {
            MaterialTheme(colorScheme = PurpleThemeColors.darkScheme) {
                Surface(Modifier.fillMaxSize()) {
                    val userPreferences by userPreferencesService.flow.collectAsState(null)
                    if (userPreferences == null) {
                        // Show loading page if it is taking a while to get app preferences
                        var showLoading by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay(500.milliseconds)
                            Timber.i("Showing loading page")
                            showLoading = true
                        }
                        if (showLoading) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black),
                            ) {
                                LoadingPage()
                            }
                        }
                    } else {
                        userPreferences?.let { userPreferences ->
                            val appPreferences = userPreferences.appPreferences
                            CoilConfig(
                                prefs = appPreferences,
                                okHttpClient = okHttpClient,
                                debugLogging = false,
                                enableCache = true,
                            )
                            LaunchedEffect(appPreferences.debugLogging) {
                                DebugLogTree.INSTANCE.enabled = appPreferences.debugLogging
                            }
                            val kidsModeState by kidsModeService.state.collectAsState()
                            val kidsLocked by kidsModeService.locked.collectAsState()
                            val kidsLiveTv by kidsLiveTvService.available.collectAsState()
                            CompositionLocalProvider(
                                LocalImageUrlService provides imageUrlService,
                                LocalKidsMode provides kidsModeState,
                                LocalKidsLocked provides kidsLocked,
                                LocalKidsLiveTv provides kidsLiveTv,
                            ) {
                                PekeTheme(
                                    true,
                                    appThemeColors = appPreferences.interfacePreferences.appThemeColors,
                                ) {
                                    ProvideLocalClock {
                                        MainContent(
                                            backStack = setupNavigationManager.backStack,
                                            navigationManager = navigationManager,
                                            userPreferences = userPreferences,
                                            backdropService = backdropService,
                                            screensaverService = screensaverService,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screensaverService.state.value.run { show || showDim }) {
            screensaverService.stop(false)
            screensaverService.pulse()
            return true
        } else {
            screensaverService.pulse()
            return super.dispatchKeyEvent(event)
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("onResume")
        viewModel.appResume()
        lifecycleScope.launchDefault {
            screensaverService.pulse()
        }
    }

    override fun onRestart() {
        super.onRestart()
        Timber.d("onRestart")
        viewModel.appStart(null)
        // If restarting during playback, go back a page
        val lastDest = navigationManager.backStack.lastOrNull()
        if (lastDest.isPlayback) {
            Timber.v("onRestart: go back from playback")
            navigationManager.goBack()
        }
    }

    override fun onStop() {
        super.onStop()
        Timber.d("onStop")
        screensaverService.stop(true)
    }

    override fun onPause() {
        super.onPause()
        Timber.d("onPause")
    }

    override fun onStart() {
        super.onStart()
        Timber.d("onStart")

        lifecycleScope.launchDefault {
            val appPreferences = userPreferencesDataStore.data.first()
            // Update notices are for grown-ups only: never while the TV is locked to kids or a kid is using it
            val grownUp = kidsModeService.lockEngaged.filterNotNull().first() == false && !kidsModeService.isActive
            if (UpdateChecker.ACTIVE && appPreferences.autoCheckForUpdates && grownUp) {
                try {
                    updateChecker.maybeShowUpdateToast()
                } catch (ex: Exception) {
                    Timber.w(
                        ex,
                        "Exception during update check",
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Timber.d("onSaveInstanceState")
        try {
            val str = json.encodeToString(navigationManager.backStack.toList())
            outState.putString(KEY_BACK_STACK, str)
        } catch (ex: Exception) {
            // Best-effort: some destinations carry types we can't serialize; skip persisting
            // the back stack rather than crashing in onSaveInstanceState.
            Timber.w(ex, "Could not persist back stack; skipping")
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        Timber.d("onRestoreInstanceState")
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("onDestroy")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Timber.d("onConfigurationChanged: newConfig=%s", newConfig)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.v("onNewIntent")
        setIntent(intent)
        viewModel.appStart(intent)
    }

    fun changeDisplayMode(modeId: Int) {
        lifecycleScope.launch(PekeDispatchers.Main + ExceptionHandler(autoToast = true)) {
            try {
                val attrs = window.attributes
                if (attrs.preferredDisplayModeId != modeId) {
                    Timber.d("Switch preferredDisplayModeId to %s", modeId)
                    window.attributes = attrs.apply { preferredDisplayModeId = modeId }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error switching preferredDisplayModeId to %s", modeId)
                Toast
                    .makeText(this@MainActivity, "Error changing display mode", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    companion object {
        private const val KEY_BACK_STACK = "backStack"

        lateinit var instance: MainActivity
            private set
    }
}

@HiltViewModel
class MainActivityViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val preferences: DataStore<AppPreferences>,
        val serverRepository: ServerRepository,
        private val setupNavigationManager: SetupNavigationManager,
        private val navigationManager: NavigationManager,
        private val deviceProfileService: DeviceProfileService,
        private val backdropService: BackdropService,
        private val appUpgradeHandler: AppUpgradeHandler,
        private val intentService: IntentService,
        private val kidsModeService: KidsModeService,
        private val kidsLockedLaunch: KidsLockedLaunch,
    ) : ViewModel() {
        private val mutex = Mutex()

        fun appStart(intent: Intent?) {
            viewModelScope.launchDefault {
                mutex.withLock {
                    try {
                        handleAppUpgrade()
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error during app upgrade!")
                        showToast(
                            context,
                            "Error during app upgrade: ${ex.localizedMessage}",
                        )
                    }

                    try {
                        val result = handleIntent(intent)
                        if (result) {
                            Timber.i("appStart was handled by intent")
                            return@withLock
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error parsing intent")
                    }

                    try {
                        handleAppStart()
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error during appStart")
                        setupNavigationManager.navigateTo(SetupDestination.ServerList)
                    }
                }
            }
            viewModelScope.launchDefault {
                // Create the mediaCodecCapabilitiesTest if needed
                deviceProfileService.mediaCodecCapabilitiesTest.supportsAVC()
            }
        }

        fun appResume() {
            viewModelScope.launchDefault {
                mutex.withLock {
                    try {
                        Timber.v("Updating userDto")
                        serverRepository.updateUserDto()
                    } catch (ex: Exception) {
                        Timber.w(ex, "Error during appResume")
                    }
                }
            }
        }

        /**
         * Handle an [Intent], navigating if needed
         *
         * @return true if the intent was handled, false if normal app start should proceed
         */
        @VisibleForTesting
        internal suspend fun handleIntent(intent: Intent?): Boolean {
            val result = intent?.let { intentService.parseIntent(intent) }
            if (result is IntentResult.Target && kidsModeService.isLockedNow()) {
                // Deep links could lead outside the kids experience, so start normally instead
                Timber.i("Ignoring intent while locked to kids mode")
                return false
            }
            when (result) {
                is IntentResult.Error -> {
                    val current = serverRepository.current.value
                    val destination =
                        if (current != null) {
                            SetupDestination.UserList(current.server)
                        } else {
                            SetupDestination.ServerList
                        }
                    setupNavigationManager.navigateTo(destination)
                    Timber.e("Error parsing intent: %s", result.message)
                    showToast(context, result.message)
                    return true
                }

                is IntentResult.Target -> {
                    val current = serverRepository.current.value
                    if (current != null) {
                        Timber.i("Received valid intent, switching to AppContent")

                        if (result.addHomeToBackStack) {
                            navigationManager.reloadHome()
                        } else {
                            navigationManager.backStack.clear()
                        }
                        navigationManager.backStack.addAll(result.destinations)

                        setupNavigationManager.navigateTo(
                            SetupDestination.AppContent(current),
                        )
                    } else {
                        // This should never happen, but just reset app state if it does
                        setupNavigationManager.navigateTo(SetupDestination.ServerList)
                        Timber.e("Error parsing intent, no user is active")
                        showToast(context, "An error occurred parsing the intent")
                    }
                    return true
                }

                IntentResult.NoOp,
                null,
                -> {
                    // Not handled
                    return false
                }
            }
        }

        private suspend fun handleAppUpgrade() {
            val needUpgrade = appUpgradeHandler.needUpgrade()
            if (needUpgrade) {
                showToast(
                    context,
                    context.getString(
                        R.string.updated_toast,
                        appUpgradeHandler.currentVersion.toString(),
                    ),
                )
                appUpgradeHandler.run()
            }
            appUpgradeHandler.copySubfont(false)
        }

        /**
         * Handle starting the app including auto sign in and navigating to the right starting page
         */
        @VisibleForTesting
        internal suspend fun handleAppStart() {
            val signInAutomatically = preferences.data.first().signInAutomatically
            val currentUser = serverRepository.current.value
            if (signInAutomatically && currentUser != null && !currentUser.user.isProtected) {
                // Hot reload
                Timber.v("Hot reload restoring state")
                setupNavigationManager.navigateTo(
                    SetupDestination.AppContent(currentUser),
                )
            } else {
                val restoredSession =
                    try {
                        if (signInAutomatically) {
                            Timber.v("Sign in automatically")
                            serverRepository.restoreLastSession()
                        } else {
                            serverRepository.getMostRecentServer()
                        }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error restoring the last session")
                        null
                    }
                Timber.d("Restore last session result=%s", restoredSession)
                // While locked to kids mode, a launch always goes back to the kids side
                val destination =
                    kidsLockedLaunch.destination(restoredSession, signInAutomatically)
                        ?: restoredSession?.destination
                        ?: SetupDestination.ServerList
                setupNavigationManager.navigateTo(destination)
            }
        }
    }

private val Destination?.isPlayback: Boolean
    get() =
        this is Destination.Playback ||
            this is Destination.PlaybackList ||
            this is Destination.Slideshow
