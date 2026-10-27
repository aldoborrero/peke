package dev.aldoborrero.peke.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.data.model.BaseItem
import dev.aldoborrero.peke.preferences.AppPreferences
import dev.aldoborrero.peke.services.ScreensaverService
import dev.aldoborrero.peke.ui.AppColors
import dev.aldoborrero.peke.ui.CrossFadeFactory
import dev.aldoborrero.peke.ui.nav.TOP_SCRIM_ALPHA
import dev.aldoborrero.peke.ui.nav.TOP_SCRIM_END_FRACTION
import dev.aldoborrero.peke.ui.theme.BuddyMood
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.util.LocalClock
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed interface ScreensaverItem {
    data class Error(
        val exception: Exception,
    ) : ScreensaverItem

    data object Empty : ScreensaverItem

    /**
     * No kid is current: Peke's own art instead of anyone's library
     */
    data object Neutral : ScreensaverItem

    data class CurrentItem(
        val item: BaseItem,
        val backdropUrl: String,
        val logoUrl: String?,
        val title: String,
    ) : ScreensaverItem
}

@Composable
fun AppScreensaver(
    prefs: AppPreferences,
    modifier: Modifier = Modifier,
) {
    AppScreensaverContent(
        showClock = prefs.interfacePreferences.screensaverPreference.showClock,
        duration = prefs.interfacePreferences.screensaverPreference.duration.milliseconds,
        animate = prefs.interfacePreferences.screensaverPreference.animate,
        modifier = modifier,
    )
}

/**
 * The screensaver (OtherScreensaverPekeTV): the buddy asleep on a night sky with a few stars, the moon and a hill, and
 * the time when the clock is on. Nobody's library shows, so a kid's pictures never appear on an idle TV. The buddy
 * moves to another spot every [duration] so nothing burns in, and floats gently when [animate] is on.
 */
@Composable
fun AppScreensaverContent(
    showClock: Boolean,
    duration: Duration,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    Box(modifier.background(lerp(colors.background, Color.Black, NIGHT_SHADE))) {
        Canvas(Modifier.fillMaxSize()) {
            // The board's stars and moon, placed by fractions of its 1280×720
            NIGHT_STARS.forEach { (x, y, r, alpha) ->
                drawCircle(
                    color = colors.textSecondary.copy(alpha = alpha),
                    radius = r.cpx.toPx(),
                    center = Offset(size.width * x, size.height * y),
                )
            }
            val moon = Offset(size.width * 1040 / 1280, size.height * 116 / 720)
            val moonRadius = 40.cpx.toPx()
            drawCircle(colors.time.copy(alpha = .9f), moonRadius, moon)
            drawCircle(
                lerp(colors.background, Color.Black, NIGHT_SHADE),
                moonRadius * .8f,
                moon + Offset(moonRadius * .45f, -moonRadius * .4f),
            )
            // The hill along the bottom
            val hill =
                Path().apply {
                    moveTo(0f, size.height * 640 / 720)
                    quadraticTo(size.width * 320 / 1280, size.height * 560 / 720, size.width / 2, size.height * 620 / 720)
                    quadraticTo(size.width * 960 / 1280, size.height * 680 / 720, size.width, size.height * 600 / 720)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
            drawPath(hill, colors.background)
        }
        SleepingBuddy(duration = duration, animate = animate, modifier = Modifier.fillMaxSize())
        if (showClock) {
            val time by LocalClock.current.timeString
            Text(
                text = time,
                fontSize = 40.csp,
                fontWeight = FontWeight.W900,
                color = colors.textSecondary.copy(alpha = .8f),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 64.cpx, bottom = 40.cpx),
            )
        }
    }
}

/**
 * The sleeping buddy (240×200 px), moving to another spot every [duration]; floating up and down when [animate] is on
 */
@Composable
private fun SleepingBuddy(
    duration: Duration,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    var alignment by remember { mutableStateOf(Alignment.Center) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(duration)
            alignment = SLEEP_SPOTS.filter { it != alignment }.random()
        }
    }
    val float by rememberInfiniteTransition(label = "buddy float").animateFloat(
        initialValue = 0f,
        targetValue = if (animate) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(BUDDY_FLOAT_MILLIS), repeatMode = RepeatMode.Reverse),
        label = "buddy float",
    )
    AnimatedContent(
        targetState = alignment,
        label = "sleeping buddy",
        transitionSpec = {
            fadeIn(animationSpec = tween(1000, delayMillis = 200))
                .togetherWith(fadeOut(animationSpec = tween(1000)))
        },
        modifier = modifier,
    ) { align ->
        Box(Modifier.fillMaxSize().padding(horizontal = 120.cpx, vertical = 140.cpx)) {
            Image(
                painter = painterResource(BuddyMood.SLEEPING.drawable),
                contentDescription = stringResource(R.string.app_name),
                modifier =
                    Modifier
                        .align(align)
                        .graphicsLayer { translationY = -float * 12.cpx.toPx() }
                        .size(240.cpx, 200.cpx),
            )
        }
    }
}

/**
 * The night sky: the page colour 40% darker (`#121730` on the PekeTV board)
 */
private const val NIGHT_SHADE = .42f

private const val BUDDY_FLOAT_MILLIS = 4000

/**
 * x and y as fractions of the screen, radius in canvas px, alpha: the board's five stars
 */
private val NIGHT_STARS =
    listOf(
        NightStar(120f / 1280, 90f / 720, 3, .6f),
        NightStar(340f / 1280, 160f / 720, 2, .5f),
        NightStar(980f / 1280, 80f / 720, 3, .6f),
        NightStar(1160f / 1280, 220f / 720, 2, .5f),
        NightStar(760f / 1280, 40f / 720, 2, .4f),
    )

private data class NightStar(
    val x: Float,
    val y: Float,
    val radius: Int,
    val alpha: Float,
)

private val SLEEP_SPOTS =
    listOf(
        Alignment.Center,
        Alignment.CenterStart,
        Alignment.CenterEnd,
        Alignment.BottomCenter,
        Alignment.TopCenter,
    )
