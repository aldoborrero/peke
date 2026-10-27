package dev.aldoborrero.peke.kids.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.KidsModeState
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.kids.timer.KidsTimeLimits
import dev.aldoborrero.peke.ui.components.ExpandablePlayButton
import dev.aldoborrero.peke.ui.components.TimeDisplay
import dev.aldoborrero.peke.ui.tryRequestFocus
import javax.inject.Inject
import kotlin.time.Duration

@HiltViewModel
class KidsTimesUpViewModel
    @Inject
    constructor(
        val timeLimits: KidsTimeLimits,
    ) : ViewModel()

/**
 * Shown when the server says the kid may not watch now: time's up, bedtime, or a break after the sitting, with the
 * break left. The only control is the grown-ups lock.
 */
@Composable
fun KidsTimesUpPage(
    modifier: Modifier = Modifier,
    viewModel: KidsTimesUpViewModel = hiltViewModel(),
) {
    val name = (LocalKidsMode.current as? KidsModeState.Active)?.user?.name.orEmpty()
    val limits by viewModel.timeLimits.state.collectAsState()
    var showGate by remember { mutableStateOf(false) }
    // Nothing to go back to
    BackHandler { }
    val denial = limits.denial ?: KidsDenial.TIME_UP
    val breakLeft =
        limits.breakRemaining?.takeIf { denial == KidsDenial.SITTING_OVER }?.let {
            val left = it.inWholeMinutes.toInt() + if (it.inWholeSeconds % 60 > 0) 1 else 0
            pluralStringResource(R.plurals.kids_break_left, left.coerceAtLeast(1), left.coerceAtLeast(1))
        }
    val greeting =
        name.takeIf { it.isNotBlank() }?.let {
            when (denial) {
                KidsDenial.TIME_UP -> stringResource(R.string.kids_times_up_subtitle, it)
                KidsDenial.BEDTIME -> stringResource(R.string.kids_bedtime_subtitle, it)
                KidsDenial.SITTING_OVER -> stringResource(R.string.kids_sitting_over_subtitle, it)
            }
        }
    KidsGrownUpsMessage(
        title =
            stringResource(
                when (denial) {
                    KidsDenial.TIME_UP -> R.string.kids_times_up
                    KidsDenial.BEDTIME -> R.string.kids_bedtime
                    KidsDenial.SITTING_OVER -> R.string.kids_sitting_over
                },
            ),
        subtitle = listOfNotNull(breakLeft, greeting).joinToString("\n").takeIf { it.isNotBlank() },
        onClickGrownUps = { showGate = true },
        modifier = modifier,
    )
    if (showGate) {
        KidsParentGate(onDismissRequest = { showGate = false })
    }
}

/**
 * A live channel the server refused for the kid (spec 006 FR-016): it says so, and Grown-ups leads through the code
 * to the menu, where the kid's rules list their channels. Back returns to where the kid was.
 */
@Composable
fun KidsChannelRefusedPage(modifier: Modifier = Modifier) {
    val name = (LocalKidsMode.current as? KidsModeState.Active)?.user?.name.orEmpty()
    var showGate by remember { mutableStateOf(false) }
    KidsGrownUpsMessage(
        title =
            if (name.isBlank()) {
                stringResource(R.string.kids_channel_not_available)
            } else {
                stringResource(R.string.kids_channel_not_on_list, name)
            },
        subtitle = stringResource(R.string.kids_channel_not_on_list_hint),
        onClickGrownUps = { showGate = true },
        modifier = modifier,
    )
    if (showGate) {
        KidsParentGate(onDismissRequest = { showGate = false })
    }
}

/**
 * A full screen message for children whose only action is the grown-ups button, which opens the parent gate. Focus
 * starts on the message, never on Grown-ups (constitution V, spec 005 FR-013): OK there does nothing, and Down reaches
 * Grown-ups.
 */
@Composable
fun KidsGrownUpsMessage(
    title: String,
    subtitle: String?,
    onClickGrownUps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messageFocus = remember { FocusRequester() }
    val grownUpsFocus = remember { FocusRequester() }
    var messageFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { messageFocus.tryRequestFocus() }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        TimeDisplay()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Focusable so the remote has a place to start, but with no action
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier
                        .focusRequester(messageFocus)
                        .focusProperties { down = grownUpsFocus }
                        .onFocusChanged { messageFocused = it.isFocused }
                        .focusable()
                        .border(
                            width = 2.dp,
                            color = if (messageFocused) MaterialTheme.colorScheme.border else Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                        ).padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ExpandablePlayButton(
                title = R.string.kids_grown_ups,
                resume = Duration.ZERO,
                icon = Icons.Default.Lock,
                onClick = { onClickGrownUps() },
                modifier = Modifier.focusRequester(grownUpsFocus),
            )
        }
    }
}
