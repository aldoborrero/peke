package dev.aldoborrero.peke.ui.detail.discover

import android.content.Context
import dev.aldoborrero.peke.api.seerr.infrastructure.ClientException
import dev.aldoborrero.peke.api.seerr.model.MediaInfo
import dev.aldoborrero.peke.data.model.DiscoverRating
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.jellyfinId
import dev.aldoborrero.peke.services.jellyfinIdAsString
import dev.aldoborrero.peke.ui.isNotNullOrBlank
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.showToast
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

suspend fun getDiscoverRating(
    id: Int,
    func: suspend () -> DiscoverRating,
): DiscoverRating? =
    try {
        func.invoke()
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: ClientException) {
        if (ex.statusCode == 404) {
            Timber.w("No rating for %s", id)
        } else {
            Timber.e(ex, "Error fetching rating for %s", id)
        }
        null
    } catch (ex: Exception) {
        Timber.e(ex, "Error fetching rating for %s", id)
        null
    }

/**
 * Navigates to the Jellyfin media item if the [mediaInfo] has a valid [jellyfinId]
 *
 * If not, a error toast is shown
 */
suspend fun goToButtonDiscover(
    mediaInfo: MediaInfo?,
    type: BaseItemKind,
    context: Context,
    navigationManager: NavigationManager,
) {
    val id = mediaInfo?.jellyfinId
    if (id != null) {
        navigationManager.navigateTo(
            Destination.MediaItem(
                itemId = id,
                type = type,
            ),
        )
    } else {
        val stringId = mediaInfo?.jellyfinIdAsString
        val msg =
            if (stringId.isNotNullOrBlank()) {
                "Unknown Jellyfin ID: $stringId"
            } else {
                "No Jellyfin ID found"
            }
        Timber.w(
            "Unknown jellyfinId for tmdb=%s: %s/%s",
            mediaInfo?.tmdbId,
            mediaInfo?.jellyfinMediaId,
            mediaInfo?.jellyfinMediaId4k,
        )
        showToast(context, msg)
    }
}
