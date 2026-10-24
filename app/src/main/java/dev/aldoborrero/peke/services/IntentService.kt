package dev.aldoborrero.peke.services

import android.app.SearchManager
import android.content.Intent
import androidx.annotation.VisibleForTesting
import dev.aldoborrero.peke.data.ServerRepository
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.setup.TvSetupRepository
import dev.aldoborrero.peke.kids.setup.isCurrentKid
import dev.aldoborrero.peke.ui.detail.series.SeasonEpisodeIds
import dev.aldoborrero.peke.ui.nav.Destination
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns an intent (a deep link, a search or a home screen channel) into the pages it opens, only as the current kid and
 * with their rules: an intent never switches user, so user ids in it are ignored (C6)
 */
@Singleton
class IntentService
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val tvSetupRepository: TvSetupRepository,
        private val kidsModeService: KidsModeService,
    ) {
        suspend fun parseIntent(intent: Intent): IntentResult {
            Timber.v("Parsing intent %s", intent)
            Timber.v("Intent extras: %s", intent.extras)
            Timber.v("Intent data: %s", intent.data)
            val action = intent.action ?: intent.data?.host
            if (
                intent.getStringParam(INTENT_ITEM_TYPE) == null &&
                (action == Intent.ACTION_MAIN || action.isNullOrBlank())
            ) {
                // Normal app launch
                return IntentResult.NoOp
            }

            val result = prepare()
            if (result != null) {
                return result
            }

            // Existence of this key implies launching from Play Next channel
            if (intent.getStringParam(INTENT_ITEM_TYPE) != null) {
                return getDestinationFromChannel(intent)?.let {
                    IntentResult.Target(
                        destinations = listOf(it),
                        addHomeToBackStack = false,
                    )
                } ?: IntentResult.Error("Invalid parameters")
            }

            if (action == Intent.ACTION_SEARCH || action == "search") {
                if (kidsModeService.profileFor(serverRepository.current.value?.user)?.searchAllowed != true) {
                    return IntentResult.Error("Search is off for this kid")
                }
                val query = intent.getStringParam(SearchManager.QUERY)
                return IntentResult.Target(listOf(Destination.Search(query ?: "")))
            }

            val itemId = intent.getStringParam("itemId")?.toUUIDOrNull()
            val item =
                itemId?.let {
                    try {
                        api.userLibraryApi
                            .getItem(itemId)
                            .content
                            .let { BaseItem(it) }
                    } catch (ex: Exception) {
                        Timber.w(ex, "Error fetching item %s", itemId)
                        return IntentResult.Error("Could not fetch item $itemId")
                    }
                }
            val itemDestination = item?.destination()

            val destinations =
                when (action) {
                    Intent.ACTION_VIEW, "view" -> {
                        if (itemDestination != null) {
                            listOf(itemDestination)
                        } else {
                            // Will go to the home page
                            emptyList()
                        }
                    }

                    ACTION_PLAYBACK, "play" -> {
                        if (itemId == null || itemDestination == null) {
                            return IntentResult.Error("Cannot start playback with no itemId")
                        }
                        val position = intent.getLongParam("position")?.coerceAtLeast(0)
                        val shuffle = intent.getBooleanExtra("shuffle", false)

                        val playbackDestination =
                            Destination.Playback(
                                itemId = itemId,
                                positionMs = position ?: 0L,
                                shuffle = shuffle,
                            )

                        if (itemDestination is Destination.Playback) {
                            listOf(playbackDestination)
                        } else {
                            listOf(
                                itemDestination,
                                playbackDestination,
                            )
                        }
                    }

                    else -> {
                        return IntentResult.Error("Invalid action: ${intent.action}")
                    }
                }

            return IntentResult.Target(destinations, true)
        }

        /**
         * Null when the intent may run: one of this TV's kids is current. Otherwise it is dropped; it never signs anyone
         * in or switches user.
         */
        @VisibleForTesting
        internal fun prepare(): IntentResult? =
            if (tvSetupRepository.state.value.isCurrentKid(serverRepository.current.value)) {
                null
            } else {
                IntentResult.Error("No kid is current")
            }

        private fun Intent.getStringParam(key: String) = getStringExtra(key) ?: data?.getQueryParameter(key)

        private fun Intent.getLongParam(key: String) =
            getLongExtra(key, -1).takeIf { it >= 0 } ?: data?.getQueryParameter(key)?.toLongOrNull()

        private fun getDestinationFromChannel(intent: Intent): Destination? =
            intent.let {
                val itemId =
                    it.getStringExtra(INTENT_ITEM_ID)?.toUUIDOrNull()
                val type =
                    it.getStringExtra(INTENT_ITEM_TYPE)?.let(BaseItemKind::fromNameOrNull)
                if (itemId != null && type != null) {
                    val seriesId = it.getStringExtra(INTENT_SERIES_ID)?.toUUIDOrNull()
                    val seasonId = it.getStringExtra(INTENT_SEASON_ID)?.toUUIDOrNull()
                    val episodeNumber = it.getIntExtra(INTENT_EPISODE_NUMBER, -1)
                    val seasonNumber = it.getIntExtra(INTENT_SEASON_NUMBER, -1)
                    if (seriesId != null && seasonId != null && episodeNumber >= 0 && seasonNumber >= 0) {
                        Destination.SeriesOverview(
                            itemId = seriesId,
                            type = BaseItemKind.SERIES,
                            seasonEpisode =
                                SeasonEpisodeIds(
                                    seasonId = seasonId,
                                    seasonNumber = seasonNumber,
                                    episodeId = itemId,
                                    episodeNumber = episodeNumber,
                                ),
                        )
                    } else {
                        Destination.MediaItem(itemId, type)
                    }
                } else {
                    null
                }
            }

        companion object {
            const val INTENT_ITEM_ID = "itemId"
            const val INTENT_ITEM_TYPE = "itemType"
            const val INTENT_SERIES_ID = "seriesId"
            const val INTENT_EPISODE_NUMBER = "epNum"
            const val INTENT_SEASON_NUMBER = "seaNum"
            const val INTENT_SEASON_ID = "seaId"

            const val ACTION_PLAYBACK = "dev.aldoborrero.peke.PLAYBACK"
        }
    }

sealed interface IntentResult {
    data class Error(
        val message: String,
    ) : IntentResult

    data object NoOp : IntentResult

    data class Target(
        val destinations: List<Destination>,
        val addHomeToBackStack: Boolean = true,
    ) : IntentResult
}
