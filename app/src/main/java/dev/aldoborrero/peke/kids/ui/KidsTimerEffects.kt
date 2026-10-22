package dev.aldoborrero.peke.kids.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.KidsModeService
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.kids.isActive
import dev.aldoborrero.peke.kids.timer.KidsTimeLimits
import dev.aldoborrero.peke.kids.timer.KidsTimerBarPosition
import dev.aldoborrero.peke.kids.timer.KidsTimerBarSetting
import dev.aldoborrero.peke.kids.timer.ResumePoint
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.PlayerFactory
import dev.aldoborrero.peke.ui.nav.Destination
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class KidsTimerEffectsViewModel
    @Inject
    constructor(
        val timeLimits: KidsTimeLimits,
        val kidsModeService: KidsModeService,
        val timerBarSetting: KidsTimerBarSetting,
        val navigationManager: NavigationManager,
        private val playerFactory: PlayerFactory,
    ) : ViewModel() {
        fun showTimesUp() {
            val top = navigationManager.backStack.lastOrNull()
            if (top !is Destination.KidsTimesUp) {
                timeLimits.interrupted.interrupt(resumePoint(top))
                // Replacing the back stack disposes any playback page, which reports the stop and releases the player
                navigationManager.replace(Destination.KidsTimesUp)
            }
        }

        fun leaveTimesUp() {
            if (navigationManager.backStack.lastOrNull() is Destination.KidsTimesUp) {
                navigationManager.replace(Destination.Home())
                timeLimits.interrupted.take()?.let {
                    navigationManager.navigateTo(Destination.Playback(it.itemId, it.positionMs))
                }
            }
        }

        /**
         * The item and position playing on [top], if it is a playback page
         */
        private fun resumePoint(top: Destination?): ResumePoint? {
            if (top !is Destination.Playback && top !is Destination.PlaybackList) return null
            val player = playerFactory.currentPlayer ?: return null
            val itemId =
                player.currentMediaItem
                    ?.mediaId
                    ?.toUUIDOrNull()
                    ?: (top as? Destination.Playback)?.itemId
                    ?: return null
            return ResumePoint(itemId, player.currentPosition.coerceAtLeast(0))
        }

        /**
         * Play a short chime, lowering the video's volume instead of pausing it
         */
        suspend fun chime() {
            val player = playerFactory.currentPlayer?.takeIf { it.isCommandAvailable(Player.COMMAND_SET_VOLUME) }
            val volume = player?.volume
            try {
                player?.volume = (volume ?: 1f) * DUCK_FACTOR
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
                try {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP2, CHIME_MILLIS)
                    delay(CHIME_MILLIS.milliseconds + 300.milliseconds)
                } finally {
                    tone.release()
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Could not play kids timer chime")
            } finally {
                if (volume != null) player.volume = volume
            }
        }

        companion object {
            private const val DUCK_FACTOR = .2f
            private const val TONE_VOLUME = 60
            private const val CHIME_MILLIS = 400
        }
    }

/**
 * App-wide kids timer UI: the remaining-time bar, warnings and switching to the time's up screen
 */
@Composable
fun KidsTimerEffects(modifier: Modifier = Modifier) {
    // Outside kids mode there is nothing to show, so no view model is created
    if (!LocalKidsMode.current.isActive) return
    KidsTimerEffectsContent(modifier = modifier, viewModel = hiltViewModel())
}

@Composable
private fun KidsTimerEffectsContent(
    viewModel: KidsTimerEffectsViewModel,
    modifier: Modifier = Modifier,
) {
    val limits by viewModel.timeLimits.state.collectAsState()
    val barPosition by viewModel.timerBarSetting.position.collectAsState(KidsTimerBarPosition.TOP)
    var warning by remember { mutableStateOf<Duration?>(null) }

    // Runs again whenever the page on screen changes, so time added from the kids settings on top of time's up still
    // leaves it once the grown-up goes back to it
    val top = viewModel.navigationManager.backStack.lastOrNull()
    LaunchedEffect(limits.expired, top) {
        if (limits.expired) {
            // A grown-up in the kids settings isn't interrupted mid-change: time's up shows when they leave
            if (top !is Destination.KidsSettings) viewModel.showTimesUp()
        } else if (top is Destination.KidsTimesUp) {
            viewModel.leaveTimesUp()
        }
    }
    // The server counts playback, so ask for the allowance again as soon as playback ends, and when coming back to
    // time's up, for example after a grown-up changed the allowance in the kids settings
    val playing = top is Destination.Playback || top is Destination.PlaybackList
    val timesUp = top is Destination.KidsTimesUp
    LaunchedEffect(playing, timesUp) {
        if (!playing) viewModel.timeLimits.allowanceService.refresh()
    }
    // Between polls the bar counts down while something plays here; the server still decides when time is up
    DisposableEffect(playing) {
        viewModel.timeLimits.setPlaying(playing)
        onDispose { viewModel.timeLimits.setPlaying(false) }
    }
    LaunchedEffect(Unit) {
        viewModel.timeLimits.warnings.collect {
            warning = it
            viewModel.chime()
        }
    }
    LaunchedEffect(warning) {
        if (warning != null) {
            delay(5.seconds)
            warning = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val remaining = limits.remaining
        val total = limits.total
        if (remaining != null && total != null && total.isPositive() && barPosition != KidsTimerBarPosition.HIDDEN) {
            val fraction = (remaining / total).toFloat().coerceIn(0f, 1f)
            Box(
                modifier =
                    Modifier
                        .align(if (barPosition == KidsTimerBarPosition.BOTTOM) Alignment.BottomCenter else Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .15f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        AnimatedVisibility(
            visible = warning != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
        ) {
            val minutes = warning?.inWholeMinutes?.toInt() ?: 1
            Text(
                text = pluralStringResource(R.plurals.kids_minutes_left, minutes, minutes),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}
