package dev.aldoborrero.peke.ui.detail.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.api.seerr.model.MovieDetails
import dev.aldoborrero.peke.data.model.DiscoverRating
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.services.jellyfinId
import dev.aldoborrero.peke.ui.LocalImageUrlService
import dev.aldoborrero.peke.ui.components.GenreText
import dev.aldoborrero.peke.ui.components.HeaderUtils
import dev.aldoborrero.peke.ui.components.OverviewText
import dev.aldoborrero.peke.ui.components.QuickDetailsText
import dev.aldoborrero.peke.ui.components.TitleOrLogo
import dev.aldoborrero.peke.ui.formatDuration
import dev.aldoborrero.peke.ui.isNotNullOrBlank
import dev.aldoborrero.peke.ui.letNotEmpty
import dev.aldoborrero.peke.ui.listToDotString
import dev.aldoborrero.peke.ui.roundMinutes
import dev.aldoborrero.peke.util.ExceptionHandler
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ImageType
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

@Composable
fun DiscoverMovieDetailsHeader(
    preferences: UserPreferences,
    movie: MovieDetails,
    rating: DiscoverRating?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val logoImageUrl =
        remember(movie) {
            movie.mediaInfo?.jellyfinId?.let {
                imageUrlService.getItemImageUrl(it, ImageType.LOGO)
            }
        }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        TitleOrLogo(
            title = movie.title,
            logoImageUrl = logoImageUrl,
            showLogo = showLogo,
            modifier =
                Modifier
                    .fillMaxWidth(.75f)
                    .padding(start = HeaderUtils.startPadding),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            val resources = LocalResources.current
            val details =
                remember(movie, rating, resources) {
                    buildList {
                        movie.releaseDate?.let(::add)
                        movie.runtime
                            ?.toDouble()
                            ?.minutes
                            ?.roundMinutes
                            ?.let { add(resources.formatDuration(it)) }
                        val release =
                            movie.releases
                                ?.results
                                ?.firstOrNull { it.iso31661 == Locale.getDefault().country }
                                ?: movie.releases
                                    ?.results
                                    ?.firstOrNull { it.iso31661 == Locale.US.country }
                                ?: movie.releases
                                    ?.results
                                    ?.firstOrNull()

                        release
                            ?.releaseDates
                            ?.firstOrNull()
                            ?.certification
                            ?.takeIf { it.isNotNullOrBlank() }
                            ?.let(::add)
                    }.let {
                        listToDotString(
                            it,
                            rating?.audienceRating,
                            rating?.criticRating?.toFloat(),
                        )
                    }
                }

            QuickDetailsText(details, Modifier.padding(start = HeaderUtils.startPadding))
            movie.genres?.mapNotNull { it.name }?.letNotEmpty {
                GenreText(it, Modifier.padding(start = HeaderUtils.startPadding))
            }

            val tagline = remember { movie.tagline?.takeIf { it.isNotNullOrBlank() } }
            tagline?.let {
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(start = HeaderUtils.startPadding),
                )
            }

            // Description
            movie.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    modifier =
                        Modifier.onFocusChanged {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                )
            }

            val directorName =
                remember(movie.credits?.crew) {
                    movie.credits
                        ?.crew
                        ?.filter { it.job == "Director" && it.name.isNotNullOrBlank() }
                        ?.joinToString(", ") { it.name!! }
                        ?.takeIf { it.isNotNullOrBlank() }
                }

            directorName
                ?.let {
                    Text(
                        text = stringResource(R.string.directed_by, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = HeaderUtils.startPadding),
                    )
                }
        }
    }
}
