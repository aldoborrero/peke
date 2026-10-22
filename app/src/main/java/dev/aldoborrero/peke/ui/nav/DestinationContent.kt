package dev.aldoborrero.peke.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.filter.DefaultForGenresFilterOptions
import dev.aldoborrero.peke.data.filter.DefaultForStudiosFilterOptions
import dev.aldoborrero.peke.data.model.SeerrItemType
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.kids.isActive
import dev.aldoborrero.peke.kids.playback.KidsDirectPlay
import dev.aldoborrero.peke.kids.playback.KidsDirectPlayPage
import dev.aldoborrero.peke.kids.ui.KidsTimesUpPage
import dev.aldoborrero.peke.kids.ui.settings.KidsSettingsPage
import dev.aldoborrero.peke.preferences.PlayerBackend
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.ItemGrid
import dev.aldoborrero.peke.ui.components.LicenseInfo
import dev.aldoborrero.peke.ui.data.MovieSortOptions
import dev.aldoborrero.peke.ui.data.rememberSortOptions
import dev.aldoborrero.peke.ui.detail.CollectionFolderGeneric
import dev.aldoborrero.peke.ui.detail.CollectionFolderLiveTv
import dev.aldoborrero.peke.ui.detail.CollectionFolderMovie
import dev.aldoborrero.peke.ui.detail.CollectionFolderMusic
import dev.aldoborrero.peke.ui.detail.CollectionFolderPhotoAlbum
import dev.aldoborrero.peke.ui.detail.CollectionFolderPlaylist
import dev.aldoborrero.peke.ui.detail.CollectionFolderRecordings
import dev.aldoborrero.peke.ui.detail.CollectionFolderTv
import dev.aldoborrero.peke.ui.detail.DebugPage
import dev.aldoborrero.peke.ui.detail.FavoritesPage
import dev.aldoborrero.peke.ui.detail.HomeRowGrid
import dev.aldoborrero.peke.ui.detail.PersonPage
import dev.aldoborrero.peke.ui.detail.PlaylistDetails
import dev.aldoborrero.peke.ui.detail.collection.CollectionDetails
import dev.aldoborrero.peke.ui.detail.discover.DiscoverMovieDetails
import dev.aldoborrero.peke.ui.detail.discover.DiscoverPersonPage
import dev.aldoborrero.peke.ui.detail.discover.DiscoverSeriesDetails
import dev.aldoborrero.peke.ui.detail.episode.EpisodeDetails
import dev.aldoborrero.peke.ui.detail.livetv.TvGuideGrid
import dev.aldoborrero.peke.ui.detail.movie.MovieDetails
import dev.aldoborrero.peke.ui.detail.music.AlbumDetailsPage
import dev.aldoborrero.peke.ui.detail.music.ArtistDetailsPage
import dev.aldoborrero.peke.ui.detail.music.NowPlayingPage
import dev.aldoborrero.peke.ui.detail.music.SongDetailsPage
import dev.aldoborrero.peke.ui.detail.series.SeriesDetails
import dev.aldoborrero.peke.ui.detail.series.SeriesOverview
import dev.aldoborrero.peke.ui.discover.DiscoverPage
import dev.aldoborrero.peke.ui.discover.DiscoverRequestGrid
import dev.aldoborrero.peke.ui.main.HomePage
import dev.aldoborrero.peke.ui.main.settings.HomeSettingsPage
import dev.aldoborrero.peke.ui.playback.PlayExternalPage
import dev.aldoborrero.peke.ui.playback.PlaybackPage
import dev.aldoborrero.peke.ui.preferences.PreferencesPage
import dev.aldoborrero.peke.ui.preferences.subtitle.SubtitleStylePage
import dev.aldoborrero.peke.ui.preferences.user.UserProfilePreferencesPage
import dev.aldoborrero.peke.ui.search.SearchPage
import dev.aldoborrero.peke.ui.setup.InstallUpdatePage
import dev.aldoborrero.peke.ui.slideshow.SlideshowPage
import dev.aldoborrero.peke.ui.util.LocalInterfaceCustomization
import dev.aldoborrero.peke.ui.util.RestrictedFeature
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber

/**
 * Chose the page for the [Destination]
 */
@Composable
fun DestinationContent(
    destination: Destination,
    preferences: UserPreferences,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (destination.fullScreen) {
        LaunchedEffect(Unit) { onClearBackdrop.invoke() }
    }
    if (LocalKidsMode.current.isActive && KidsDirectPlay.handles(destination)) {
        KidsDirectPlayPage(destination, modifier)
        return
    }
    val externalPlayer = LocalInterfaceCustomization.current.allows(RestrictedFeature.EXTERNAL_PLAYER)
    when (destination) {
        is Destination.Home -> {
            HomePage(
                preferences = preferences,
                modifier = modifier,
            )
        }

        is Destination.HomeSettings -> {
            HomeSettingsPage(preferences, modifier)
        }

        is Destination.Playback -> {
            val backend =
                destination.backend ?: preferences.appPreferences.playbackPreferences.playerBackend
            if (backend == PlayerBackend.EXTERNAL_PLAYER && externalPlayer) {
                PlayExternalPage(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            } else {
                PlaybackPage(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            }
        }

        is Destination.PlaybackList -> {
            if (preferences.appPreferences.playbackPreferences.playerBackend == PlayerBackend.EXTERNAL_PLAYER && externalPlayer) {
                PlayExternalPage(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            } else {
                PlaybackPage(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            }
        }

        is Destination.Settings -> {
            PreferencesPage(
                preferences.appPreferences,
                destination.screen,
                modifier,
            )
        }

        is Destination.SubtitleSettings -> {
            SubtitleStylePage(
                preferences.appPreferences,
                destination.hdr,
                modifier,
            )
        }

        Destination.UserAppPreferences -> {
            UserProfilePreferencesPage(modifier)
        }

        Destination.KidsSettings -> {
            KidsSettingsPage(modifier)
        }

        Destination.KidsTimesUp -> {
            KidsTimesUpPage(modifier)
        }

        Destination.KidsLiveTv -> {
            TvGuideGrid(
                preferences = preferences,
                onRowPosition = {},
                modifier = modifier.fillMaxSize(),
            )
        }

        is Destination.SeriesOverview -> {
            SeriesOverview(
                preferences = preferences,
                destination = destination,
                initialSeasonEpisode = destination.seasonEpisode,
                modifier = modifier,
            )
        }

        is Destination.MediaItem -> {
            when (destination.type) {
                BaseItemKind.SERIES -> {
                    SeriesDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.MOVIE -> {
                    MovieDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.VIDEO,
                BaseItemKind.MUSIC_VIDEO,
                -> {
                    // TODO Use VideoDetails
                    MovieDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.EPISODE -> {
                    EpisodeDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.BOX_SET -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionDetails(
                        preferences = preferences,
                        itemId = destination.itemId,
                        modifier = modifier,
                    )
                }

                BaseItemKind.PLAYLIST -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    PlaylistDetails(
                        preferences = preferences,
                        destination = destination,
                        modifier = modifier,
                    )
                }

                BaseItemKind.COLLECTION_FOLDER -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = null,
                        recursiveOverride = null,
                        modifier = modifier,
                    )
                }

                BaseItemKind.FOLDER -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = true,
                        recursiveOverride = null,
                        modifier = modifier,
                    )
                }

                BaseItemKind.USER_VIEW -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = null,
                        recursiveOverride = true,
                        modifier = modifier,
                    )
                }

                BaseItemKind.PERSON -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    PersonPage(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.PHOTO_ALBUM -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolderPhotoAlbum(
                        preferences = preferences,
                        itemId = destination.itemId,
                        recursive = false,
                        modifier = modifier,
                    )
                }

                BaseItemKind.MUSIC_ALBUM -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    AlbumDetailsPage(
                        preferences = preferences,
                        itemId = destination.itemId,
                        initialSongId = destination.initialSongId,
                        modifier = modifier,
                    )
                }

                BaseItemKind.MUSIC_ARTIST -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    ArtistDetailsPage(
                        preferences = preferences,
                        itemId = destination.itemId,
                        modifier = modifier,
                    )
                }

                BaseItemKind.AUDIO -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    SongDetailsPage(
                        preferences = preferences,
                        itemId = destination.itemId,
                        modifier = modifier,
                    )
                }

                else -> {
                    Timber.w("Unsupported item type: ${destination.type}")
                    Text("Unsupported item type: ${destination.type}", modifier)
                }
            }
        }

        is Destination.FilteredCollection -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderGeneric(
                preferences = preferences,
                itemId = destination.itemId,
                filter = destination.filter,
                recursive = destination.recursive,
                usePosters = true,
                playEnabled = true, // TODO only genres use this currently, so might need to change in future
                filterOptions =
                    when (destination.parentType) {
                        BaseItemKind.GENRE -> DefaultForGenresFilterOptions
                        BaseItemKind.STUDIO -> DefaultForStudiosFilterOptions
                        else -> throw IllegalArgumentException("Unsupported parentType ${destination.parentType}")
                    },
                sortOptions = rememberSortOptions(destination.collectionType),
                modifier = modifier,
            )
        }

        is Destination.MoreHomeRow -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            HomeRowGrid(
                preferences = preferences,
                destination = destination,
                modifier = modifier,
            )
        }

        is Destination.Recordings -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            if (LocalInterfaceCustomization.current.allows(RestrictedFeature.LIVE_TV_RECORDINGS)) {
                CollectionFolderRecordings(
                    preferences,
                    destination.itemId,
                    false,
                    modifier,
                )
            } else {
                ErrorMessage(stringResource(R.string.kids_not_available), null, modifier)
            }
        }

        is Destination.ItemGrid<*> -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            ItemGrid(
                preferences,
                destination,
                modifier,
            )
        }

        is Destination.Slideshow -> {
            SlideshowPage(
                slideshow = destination,
            )
        }

        Destination.Favorites -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            FavoritesPage(
                preferences = preferences,
                modifier = modifier,
            )
        }

        Destination.NowPlaying -> {
            NowPlayingPage(modifier)
        }

        Destination.UpdateApp -> {
            InstallUpdatePage(preferences, modifier)
        }

        Destination.License -> {
            LicenseInfo(modifier)
        }

        is Destination.Search -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            SearchPage(
                initialQuery = destination.query,
                userPreferences = preferences,
                modifier = modifier,
            )
        }

        Destination.Debug -> {
            DebugPage(preferences, modifier)
        }

        Destination.Discover -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            DiscoverPage(
                preferences = preferences,
                modifier = modifier,
            )
        }

        is Destination.DiscoveredItem -> {
            when (destination.item.type) {
                SeerrItemType.MOVIE -> {
                    DiscoverMovieDetails(
                        preferences = preferences,
                        destination = destination,
                        modifier = modifier,
                    )
                }

                SeerrItemType.TV -> {
                    DiscoverSeriesDetails(
                        preferences = preferences,
                        destination = destination,
                        modifier = modifier,
                    )
                }

                SeerrItemType.PERSON -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    DiscoverPersonPage(
                        person = destination.item,
                        startIndex = destination.startIndex,
                        modifier = modifier,
                    )
                }

                SeerrItemType.UNKNOWN -> {
                    Text(
                        text = "Unknown discover type",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        is Destination.DiscoverMoreResult -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            DiscoverRequestGrid(
                destination = destination,
                showTitle = true,
                positionCallback = { _, _ -> },
                modifier = modifier,
            )
        }
    }
}

@Composable
fun CollectionFolder(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    collectionType: CollectionType?,
    usePostersOverride: Boolean?,
    recursiveOverride: Boolean?,
    modifier: Modifier = Modifier,
) {
    when (collectionType) {
        CollectionType.TVSHOWS -> {
            CollectionFolderTv(
                preferences,
                destination,
                modifier,
            )
        }

        CollectionType.MOVIES -> {
            CollectionFolderMovie(
                preferences,
                destination,
                modifier,
            )
        }

        CollectionType.BOXSETS -> {
            CollectionFolderGeneric(
                preferences = preferences,
                itemId = destination.itemId,
                usePosters = true,
                recursive = false,
                playEnabled = false,
                modifier = modifier,
                sortOptions = MovieSortOptions,
            )
        }

        CollectionType.PLAYLISTS -> {
            CollectionFolderPlaylist(
                preferences,
                destination.itemId,
                true,
                modifier,
            )
        }

        CollectionType.LIVETV -> {
            CollectionFolderLiveTv(
                preferences = preferences,
                destination = destination,
                modifier = modifier,
            )
        }

        CollectionType.MUSIC -> {
            CollectionFolderMusic(
                preferences,
                destination,
                modifier,
            )
        }

        CollectionType.HOMEVIDEOS,
        CollectionType.PHOTOS,
        -> {
            CollectionFolderPhotoAlbum(
                preferences = preferences,
                itemId = destination.itemId,
                recursive = recursiveOverride ?: false,
                modifier = modifier,
            )
        }

        CollectionType.MUSICVIDEOS,
        CollectionType.BOOKS,
        -> {
            CollectionFolderGeneric(
                preferences,
                destination.itemId,
                usePosters = usePostersOverride ?: false,
                recursive = recursiveOverride ?: false,
                playEnabled = true,
                modifier = modifier,
            )
        }

        CollectionType.FOLDERS,
        CollectionType.TRAILERS,
        CollectionType.UNKNOWN,
        null,
        -> {
            CollectionFolderGeneric(
                preferences,
                destination.itemId,
                usePosters = usePostersOverride ?: false,
                recursive = recursiveOverride ?: false,
                playEnabled = false,
                modifier = modifier,
            )
        }
    }
}
