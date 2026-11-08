package dev.aldoborrero.peke.ui.nav

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.model.JellyfinServer
import dev.aldoborrero.peke.data.model.JellyfinUser
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.kids.data.KidAvatars
import dev.aldoborrero.peke.kids.home.rememberHomeTimeLeft
import dev.aldoborrero.peke.kids.home.timeLeftText
import dev.aldoborrero.peke.kids.isActive
import dev.aldoborrero.peke.kids.setup.KidAvatar
import dev.aldoborrero.peke.kids.ui.TimeLeftBand
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.services.BackdropService
import dev.aldoborrero.peke.services.ImageUrlService
import dev.aldoborrero.peke.services.MusicService
import dev.aldoborrero.peke.services.NavDrawerService
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.SetupDestination
import dev.aldoborrero.peke.services.SetupNavigationManager
import dev.aldoborrero.peke.ui.FontAwesome
import dev.aldoborrero.peke.ui.components.TimeDisplay
import dev.aldoborrero.peke.ui.ifElse
import dev.aldoborrero.peke.ui.launchDefault
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption
import dev.aldoborrero.peke.ui.spacedByWithFooter
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeAvatar
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.toServerString
import dev.aldoborrero.peke.ui.tryRequestFocus
import dev.aldoborrero.peke.ui.util.LocalInterfaceCustomization
import dev.aldoborrero.peke.ui.util.RestrictedFeature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NavDrawerViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val navDrawerService: NavDrawerService,
        val navigationManager: NavigationManager,
        val setupNavigationManager: SetupNavigationManager,
        val backdropService: BackdropService,
        private val musicService: MusicService,
        private val kidAvatars: KidAvatars,
    ) : ViewModel() {
        val serviceState = navDrawerService.state

        private val _state = MutableStateFlow(NavDrawerState())
        val state: StateFlow<NavDrawerState> = _state

        fun onClickDrawerItem(
            index: Int,
            item: NavDrawerItem,
        ) {
            when (item) {
                NavDrawerItem.Favorites -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(
                        Destination.Favorites,
                    )
                }

                NavDrawerItem.LiveTv -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(Destination.KidsLiveTv)
                }

                is ServerNavDrawerItem -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(item.destination)
                }
            }
        }

        fun setIndex(index: Int) {
            _state.update { it.copy(selectedIndex = index) }
        }

        fun getUserImage(user: JellyfinUser): String = api.imageApi.getUserImageUrl(user.id, maxHeight = ImageUrlService.userImageMaxHeight)

        /**
         * The avatar a grown-up picked for [user] in the Kids plugin, if any
         */
        fun avatar(user: JellyfinUser): Flow<PekeAvatar?> = kidAvatars.observe(user.serverId).map { it[user.rowId] }

        /**
         * Determine which nav drawer item should be highlighted as currently selected
         *
         * @param items the items the drawer shows instead of the libraries, if any
         */
        fun updateSelectedIndex(items: List<NavDrawerItem>? = null) {
            viewModelScope.launchDefault {
                val asDestinations =
                    (items ?: serviceState.value.items).map {
                        when (it) {
                            is ServerNavDrawerItem -> it.destination
                            is NavDrawerItem.Favorites -> Destination.Favorites
                            is NavDrawerItem.LiveTv -> Destination.KidsLiveTv
                        }
                    }

                val backstack = navigationManager.backStack.toList().reversed()
                for (i in 0..<backstack.size) {
                    val key = backstack[i]
                    val index =
                        if (key is Destination.Home) {
                            HOME_INDEX
                        } else if (key is Destination.Search) {
                            SEARCH_INDEX
                        } else if (key is Destination.NowPlaying) {
                            NOW_PLAYING_INDEX
                        } else {
                            val idx = asDestinations.indexOf(key)
                            if (idx >= 0) {
                                idx
                            } else {
                                null
                            }
                        }
                    Timber.v("Found $index => $key")
                    if (index != null) {
                        _state.update { it.copy(selectedIndex = index) }
                        break
                    }
                }
            }
        }

        fun navigateToSetup(userList: SetupDestination) {
            viewModelScope.launchDefault {
                musicService.stop()
                setupNavigationManager.navigateTo(userList)
            }
        }
    }

data class NavDrawerState(
    val selectedIndex: Int = -1,
)

/**
 * An item that can be shown in the nav drawer
 *
 * Some are built-in such as Favorites. Others are created dynamically for libraries.
 */
sealed interface NavDrawerItem {
    val id: String

    fun name(context: Context): String

    object Favorites : NavDrawerItem {
        override val id: String
            get() = "a_favorites"

        override fun name(context: Context): String = context.getString(R.string.favorites)
    }

    /**
     * The guide of a restricted profile's own live TV channels, see [Destination.KidsLiveTv]
     */
    object LiveTv : NavDrawerItem {
        override val id: String
            get() = "a_livetv"

        override fun name(context: Context): String = context.getString(R.string.live_tv)
    }
}

/**
 * A server provided nav drawer item, typically a library
 */
data class ServerNavDrawerItem(
    val itemId: UUID,
    val name: String,
    val destination: Destination,
    val type: CollectionType,
) : NavDrawerItem {
    override val id: String = getId(itemId)

    override fun name(context: Context): String = name

    companion object {
        fun getId(itemId: UUID) = "s_" + itemId.toServerString()
    }
}

/**
 * Whether Back opens the nav drawer: only on the home page with the drawer closed. With the drawer open the app leaves
 * Back to the system, which quits the app as in Wholphin. Back never opens the profile's gate; only its drawer entry
 * does.
 */
internal fun backOpensNavDrawer(
    drawerValue: DrawerValue,
    destination: Destination,
): Boolean = drawerValue == DrawerValue.Closed && destination is Destination.Home

/**
 * Back opens the drawer where [backOpensNavDrawer] says, then [onOpened]. Its own scope reads the drawer's state, so
 * opening and closing the drawer recomposes this alone, not the page around it
 */
@Composable
private fun DrawerBackHandler(
    drawerState: DrawerState,
    destination: Destination,
    enabled: Boolean,
    onOpened: () -> Unit,
) {
    BackHandler(enabled = enabled && backOpensNavDrawer(drawerState.currentValue, destination)) {
        drawerState.setValue(DrawerValue.Open)
        onOpened()
    }
}

/**
 * The page's opacity behind the sidebar: 35% while it is open, animated as `animateFloatAsState` would. The drawer's
 * state is read in an effect, not in composition, and the value only where it is drawn, so opening and closing the
 * drawer doesn't recompose the page
 */
@Composable
internal fun rememberPageAlpha(drawerState: DrawerState): State<Float> {
    val alpha =
        remember(drawerState) {
            Animatable(Snapshot.withoutReadObservation { pageAlpha(drawerState.isOpen) }, visibilityThreshold = .01f)
        }
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.isOpen }.collectLatest { alpha.animateTo(pageAlpha(it)) }
    }
    return alpha.asState()
}

private fun pageAlpha(drawerOpen: Boolean) = if (drawerOpen) .35f else 1f

private const val HOME_INDEX = -1
private const val SEARCH_INDEX = -2
private const val NOW_PLAYING_INDEX = -3

/**
 * Display the left side navigation drawer with [DestinationContent] on the right
 *
 * @param timeLeftBand the room the kid's time-left bar takes at the top or bottom, which the page keeps out of
 */
@Composable
fun NavDrawer(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    drawerState: DrawerState,
    navDrawerListState: LazyListState,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
    timeLeftBand: TimeLeftBand? = null,
    viewModel: NavDrawerViewModel =
        hiltViewModel(
            LocalView.current.findViewTreeViewModelStoreOwner()!!,
            key = "${server.id}_${user.id}", // Keyed to the server & user to ensure its reset when switching either
        ),
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    val focusRequester = remember { FocusRequester() }
    val customization = LocalInterfaceCustomization.current
    val showLibraries = customization.allows(RestrictedFeature.LIBRARIES)
    LaunchedEffect(showLibraries, customization.navDrawerItems) {
        viewModel.updateSelectedIndex(if (showLibraries) null else customization.navDrawerItems)
    }
    val profileGate = LocalProfileGate.current
    // The gate stays open until dismissed, even if the profile stops needing it meanwhile
    var shownProfileGate by remember { mutableStateOf<ProfileGate?>(null) }
    // Where the gate goes once passed: the kids picker from the profile icon, the menu (null) from the drawer entry
    var afterProfileGate by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Every nav entry has its own drawer, sharing [drawerState]. While a page comes in, such as when the home reloads,
    // the one leaving is still composed: only the page on top may take focus or handle Back (spec 005 FR-013)
    val activeEntry = viewModel.navigationManager.backStack.lastOrNull() == destination

    // If the user presses back while on the home page, open the nav drawer, another back press will quit the app
    DrawerBackHandler(drawerState, destination, enabled = activeEntry) { focusRequester.tryRequestFocus() }
    val serviceState by viewModel.serviceState.collectAsState()
    val drawerItems = if (showLibraries) serviceState.items else customization.navDrawerItems
    val state by viewModel.state.collectAsState()
    // A negative index is a built-in page, >=0 is a library
    val selectedIndex = state.selectedIndex

    // The open sidebar covers the page, which fades behind it (HomeDrawerPekeTV)
    val contentAlpha = rememberPageAlpha(drawerState)
    val pageBackground = MaterialTheme.colorScheme.background

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        activeEntry = activeEntry,
        // Opening focuses the selected item, never the profile icon above it, which leads to the parent gate
        initialFocus = focusRequester,
        drawerContent = { drawerValue ->
            val isOpen = drawerValue.isOpen
            // HomeRailPekeTV: 18 px between the rail's tiles; HomeDrawerPekeTV: 12 px between the open rows
            val spacedBy = if (isOpen) 12.cpx else 18.cpx
            val searchFocusRequester = remember { FocusRequester() }

            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontSize = 24.csp)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacedBy),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    // Even though some must be clicked, focusing on it should clear other focused items
                    val interactionSource = remember { MutableInteractionSource() }
                    val userImageUrl = remember(user) { viewModel.getUserImage(user) }
                    val avatar by remember(user) { viewModel.avatar(user) }.collectAsState(null)
                    val timeLeft = rememberHomeTimeLeft()
                    ProfileIcon(
                        user = user,
                        imageUrl = userImageUrl,
                        avatar = avatar,
                        timeLeft = timeLeft?.let { timeLeftText(it) },
                        drawerOpen = isOpen,
                        interactionSource = interactionSource,
                        onClick = {
                            // Switching profile needs the gate first, then the kids picker (spec FR-012); without a gate there
                            // is nowhere to switch to
                            if (profileGate != null) {
                                afterProfileGate = { viewModel.navigateToSetup(SetupDestination.KidsPicker) }
                                shownProfileGate = profileGate
                            }
                        },
                        modifier = Modifier,
                    )
                    Spacer(Modifier.height(if (isOpen) 6.cpx else 16.cpx))
                    AnimatedVisibility(
                        visible = serviceState.nowPlayingEnabled && customization.allows(RestrictedFeature.NOW_PLAYING),
                        enter = expandVertically(expandFrom = Alignment.Top),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top),
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        IconNavItem(
                            text = stringResource(R.string.now_playing),
                            subtext = serviceState.nowPlayingTitle,
                            icon = rememberVectorPainter(Icons.Default.PlayArrow),
                            selected = selectedIndex == NOW_PLAYING_INDEX,
                            drawerOpen = isOpen,
                            interactionSource = interactionSource,
                            onClick = {
                                viewModel.setIndex(NOW_PLAYING_INDEX)
                                viewModel.navigationManager.navigateTo(Destination.NowPlaying)
                            },
                            modifier =
                                Modifier
                                    .ifElse(
                                        selectedIndex == NOW_PLAYING_INDEX,
                                        Modifier.focusRequester(focusRequester),
                                    ),
                        )
                    }
                    LazyColumn(
                        state = navDrawerListState,
                        // Room for the first and last rows' focus rings, which the list would cut
                        contentPadding = PaddingValues(vertical = DrawerRingRoom),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedByWithFooter(spacedBy),
                        modifier =
                            Modifier
                                .focusGroup()
                                .focusProperties {
                                    onEnter = {
                                        if (requestedFocusDirection == FocusDirection.Down) {
                                            searchFocusRequester.tryRequestFocus()
                                        } else {
                                            focusRequester.tryRequestFocus()
                                        }
                                    }
                                }.fillMaxHeight()
                                .drawerRingRoom(),
                    ) {
                        if (customization.allows(RestrictedFeature.SEARCH)) {
                            item {
                                val interactionSource = remember { MutableInteractionSource() }
                                IconNavItem(
                                    text = stringResource(R.string.search),
                                    icon = painterResource(PekeIcon.SEARCH.outline),
                                    selected = selectedIndex == SEARCH_INDEX,
                                    drawerOpen = isOpen,
                                    interactionSource = interactionSource,
                                    onClick = {
                                        viewModel.setIndex(SEARCH_INDEX)
                                        viewModel.navigationManager.navigateToFromDrawer(Destination.Search())
                                    },
                                    modifier =
                                        Modifier
                                            .focusRequester(searchFocusRequester)
                                            .ifElse(
                                                selectedIndex == SEARCH_INDEX,
                                                Modifier.focusRequester(focusRequester),
                                            ),
                                )
                            }
                        }
                        item {
                            val interactionSource = remember { MutableInteractionSource() }
                            IconNavItem(
                                text = stringResource(R.string.home),
                                icon = painterResource(PekeIcon.HOME.outline),
                                selected = selectedIndex == HOME_INDEX,
                                drawerOpen = isOpen,
                                interactionSource = interactionSource,
                                onClick = {
                                    viewModel.setIndex(HOME_INDEX)
                                    if (destination is Destination.Home) {
                                        viewModel.navigationManager.reloadHome()
                                        onClearBackdrop.invoke()
                                    } else {
                                        viewModel.navigationManager.goToHome()
                                    }
                                },
                                modifier =
                                    Modifier
                                        .ifElse(
                                            selectedIndex == HOME_INDEX,
                                            Modifier.focusRequester(focusRequester),
                                        ),
                            )
                        }
                        itemsIndexed(drawerItems) { index, it ->
                            val interactionSource = remember { MutableInteractionSource() }
                            NavItem(
                                library = it,
                                selected = selectedIndex == index,
                                drawerOpen = isOpen,
                                interactionSource = interactionSource,
                                onClick = {
                                    viewModel.onClickDrawerItem(index, it)
                                },
                                modifier =
                                    Modifier
                                        .ifElse(
                                            selectedIndex == index,
                                            Modifier.focusRequester(focusRequester),
                                        ),
                            )
                        }
                        if (customization.allows(RestrictedFeature.SETTINGS)) {
                            item {
                                val interactionSource = remember { MutableInteractionSource() }
                                IconNavItem(
                                    text = stringResource(R.string.settings),
                                    icon = rememberVectorPainter(Icons.Default.Settings),
                                    selected = false,
                                    drawerOpen = isOpen,
                                    interactionSource = interactionSource,
                                    onClick = {
                                        viewModel.navigationManager.navigateTo(
                                            Destination.Settings(
                                                PreferenceScreenOption.BASIC,
                                            ),
                                        )
                                    },
                                    modifier = Modifier,
                                )
                            }
                        }
                        if (profileGate != null) {
                            item {
                                val interactionSource = remember { MutableInteractionSource() }
                                IconNavItem(
                                    text = stringResource(profileGate.title),
                                    icon = painterResource(profileGate.icon),
                                    selected = false,
                                    small = true,
                                    drawerOpen = isOpen,
                                    interactionSource = interactionSource,
                                    onClick = {
                                        afterProfileGate = null
                                        shownProfileGate = profileGate
                                    },
                                    modifier = Modifier,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Drawer content: the open sidebar covers it, and it fades to 35% behind (HomeDrawerPekeTV)
            val kidsHome = destination is Destination.Home && LocalKidsMode.current.isActive
            DestinationContent(
                destination = destination,
                preferences = preferences,
                onClearBackdrop = onClearBackdrop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .dimmedTo(pageBackground) { contentAlpha.value }
                        // Nothing scrolls under the time-left label: the page starts below it at the top, or ends above it at the bottom
                        .padding(top = timeLeftBand?.top ?: 0.dp, bottom = timeLeftBand?.bottom ?: 0.dp)
                        .padding(start = DrawerRailWidth + if (kidsHome) 0.dp else 8.dp, end = if (kidsHome) 0.dp else 16.dp),
            )
            // The kids home and search show the clock in their own top row
            val ownClock = LocalKidsMode.current.isActive && (kidsHome || destination is Destination.Search)
            if (preferences.appPreferences.interfacePreferences.showClock && !ownClock) {
                TimeDisplay()
            }
            shownProfileGate?.content?.invoke({ shownProfileGate = null }, afterProfileGate)
        }
    }
}

/**
 * The kid at the top of the sidebar: their avatar on the closed rail (64 px); open, a bigger avatar (72 px) with their
 * name and, with a daily limit, the time left in the "time" colour (HomeDrawerPekeTV). Clicking it switches kid behind
 * the parent gate; it never takes focus when the sidebar opens.
 */
@Composable
fun NavigationDrawerScope.ProfileIcon(
    user: JellyfinUser,
    imageUrl: String?,
    avatar: PekeAvatar?,
    timeLeft: String?,
    drawerOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    NavigationDrawerItem(
        modifier = modifier,
        onClick = onClick,
        height = if (drawerOpen) 84.cpx else DrawerTileSize,
        leadingContent = {
            KidAvatar(
                id = user.id,
                name = user.name,
                imageUrl = imageUrl,
                avatar = avatar,
                alpha = 1f,
                initialStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.size(if (drawerOpen) 72.cpx else 64.cpx),
            )
        },
        interactionSource = interactionSource,
    ) {
        Text(
            text = user.name ?: user.id.toString(),
            fontSize = 26.csp,
            fontWeight = FontWeight.W900,
            color = drawerTextColor(current = true, focused = focused),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        timeLeft?.let {
            Text(
                text = it,
                fontSize = 16.csp,
                fontWeight = FontWeight.W800,
                color = if (focused) LocalContentColor.current else LocalPekeColors.current.time,
                maxLines = 1,
            )
        }
    }
}

/**
 * A sidebar entry with [icon]; [small] for Grown-ups at the bottom, whose name is quieter (20 px). Its icon is the same
 * size as the others', so the rail keeps one line of icons.
 */
@Composable
fun NavigationDrawerScope.IconNavItem(
    text: String,
    icon: Painter,
    onClick: () -> Unit,
    selected: Boolean,
    drawerOpen: Boolean,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    small: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    NavigationDrawerItem(
        modifier = modifier,
        onClick = onClick,
        current = selected,
        leadingContent = {
            Box(modifier = Modifier.size(DrawerIconSize), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = if (drawerOpen) null else text,
                    tint = navItemColor(selected, focused, drawerOpen),
                    modifier = Modifier.size(DrawerIconSize),
                )
            }
        },
        interactionSource = interactionSource,
    ) {
        DrawerItemText(text = text, current = selected, focused = focused, small = small)
        subtext?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
fun NavigationDrawerScope.NavItem(
    library: NavDrawerItem,
    onClick: () -> Unit,
    selected: Boolean,
    drawerOpen: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val context = LocalContext.current
    // The kids' entries use Peke's outline icons; libraries, which kids never see, keep Wholphin's
    val pekeIcon =
        when (library) {
            NavDrawerItem.Favorites -> PekeIcon.FAVOURITES
            NavDrawerItem.LiveTv -> PekeIcon.LIVE
            else -> null
        }
    val useFont = pekeIcon == null && (library !is ServerNavDrawerItem || library.type != CollectionType.LIVETV)
    val icon =
        remember(library) {
            when (library) {
                NavDrawerItem.Favorites -> {
                    R.string.fa_heart
                }

                NavDrawerItem.LiveTv -> {
                    R.string.fa_tv
                }

                is ServerNavDrawerItem -> {
                    when (library.type) {
                        CollectionType.MOVIES -> R.string.fa_film
                        CollectionType.TVSHOWS -> R.string.fa_tv
                        CollectionType.HOMEVIDEOS -> R.string.fa_video
                        CollectionType.LIVETV -> R.drawable.gf_dvr
                        CollectionType.MUSIC -> R.string.fa_music
                        CollectionType.BOXSETS -> R.string.fa_open_folder
                        CollectionType.PLAYLISTS -> R.string.fa_list_ul
                        else -> R.string.fa_film
                    }
                }
            }
        }
    val focused by interactionSource.collectIsFocusedAsState()
    val name = library.name(context)
    NavigationDrawerItem(
        modifier = modifier,
        onClick = onClick,
        current = selected,
        leadingContent = {
            val color = navItemColor(selected, focused, drawerOpen)
            Box(modifier = Modifier.size(DrawerIconSize), contentAlignment = Alignment.Center) {
                if (pekeIcon != null) {
                    Icon(
                        painter = painterResource(pekeIcon.outline),
                        contentDescription = if (drawerOpen) null else name,
                        tint = color,
                        modifier = Modifier.size(DrawerIconSize),
                    )
                } else if (useFont) {
                    Text(
                        text = stringResource(icon),
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        fontFamily = FontAwesome,
                        color = color,
                        modifier = Modifier,
                    )
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(DrawerIconSize),
                    )
                }
            }
        },
        interactionSource = interactionSource,
    ) {
        DrawerItemText(text = name, current = selected, focused = focused)
    }
}

/**
 * An open sidebar entry's name: 24 px, bolder for the page you're on and the focused entry; 20 px for [small]
 */
@Composable
private fun DrawerItemText(
    text: String,
    current: Boolean,
    focused: Boolean,
    small: Boolean = false,
) {
    Text(
        text = text,
        fontSize = if (small) 20.csp else 24.csp,
        fontWeight = if ((current || focused) && !small) FontWeight.W900 else FontWeight.W800,
        color = drawerTextColor(current = current, focused = focused),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
@ReadOnlyComposable
fun navItemColor(
    selected: Boolean,
    focused: Boolean,
    drawerOpen: Boolean,
): Color =
    when {
        focused -> LocalContentColor.current

        // The page you're on is drawn in the primary colour; the others in "text, secondary", so the rail stays quiet
        // (design/DESIGN.md, Icons)
        selected -> MaterialTheme.colorScheme.primary

        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

val DrawerState.isOpen: Boolean get() = this.currentValue.isOpen

val DrawerValue.isOpen: Boolean get() = this == DrawerValue.Open

/**
 * Fades what this draws to [alpha] over [background], the page behind it, as `graphicsLayer { alpha }` would: by drawing
 * [background] over it at the remaining opacity, which gives the same pixels on an opaque [background] without an
 * offscreen layer the size of the screen on every frame of the sidebar opening and closing
 */
internal fun Modifier.dimmedTo(
    background: Color,
    alpha: () -> Float,
): Modifier =
    drawWithContent {
        drawContent()
        val veil = 1f - alpha()
        if (veil > 0f) drawRect(background, alpha = veil.coerceAtMost(1f))
    }
