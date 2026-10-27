package dev.aldoborrero.peke.kids.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.LocalKidsMode
import dev.aldoborrero.peke.kids.isActive
import dev.aldoborrero.peke.kids.server.KidsDenial
import dev.aldoborrero.peke.ui.theme.BuddyMood
import java.io.IOException

/**
 * The buddy's mood on a stop screen (design/DESIGN.md, The PekeTV buddy): waving at time's up, asleep outside the
 * allowed hours, and just there, waiting with the kid, on a break
 */
fun stopMood(denial: KidsDenial): BuddyMood =
    when (denial) {
        KidsDenial.TIME_UP -> BuddyMood.WAVING
        KidsDenial.BEDTIME -> BuddyMood.SLEEPING
        KidsDenial.SITTING_OVER -> BuddyMood.HAPPY
    }

/**
 * What a kid is told when something fails: never the technical error (design/DESIGN.md, Words), only whether the server
 * is out of reach or something didn't work
 */
enum class KidsTrouble(
    val mood: BuddyMood,
    @param:StringRes val title: Int,
    @param:StringRes val hint: Int,
) {
    /**
     * The server can't be reached: the buddy searches for a signal
     */
    OFFLINE(BuddyMood.SEARCHING, R.string.kids_offline_title, R.string.kids_offline_hint),

    /**
     * Anything else: something won't play or load
     */
    OOPS(BuddyMood.PUZZLED, R.string.kids_oops_title, R.string.kids_oops_hint),
    ;

    companion object {
        /**
         * [OFFLINE] when [exception] or one of its causes is a network error, else [OOPS]
         */
        fun of(exception: Throwable?): KidsTrouble =
            if (generateSequence(exception) { it.cause }.any { it is IOException }) OFFLINE else OOPS
    }
}

/**
 * An error as a kid sees it: the buddy, a short friendly line and Grown-ups in the corner, which opens the parent gate.
 * Details such as the exception are for grown-ups only.
 *
 * @param title replaces the trouble's own title, such as "this one won't play" for playback
 */
@Composable
fun KidsTroubleMessage(
    exception: Throwable?,
    modifier: Modifier = Modifier,
    @StringRes title: Int? = null,
) {
    val trouble = KidsTrouble.of(exception)
    var showGate by remember { mutableStateOf(false) }
    KidsGrownUpsMessage(
        title = stringResource(if (trouble == KidsTrouble.OOPS && title != null) title else trouble.title),
        subtitle = stringResource(trouble.hint),
        mood = trouble.mood,
        onClickGrownUps = { showGate = true },
        modifier = modifier,
    )
    if (showGate) {
        KidsParentGate(onDismissRequest = { showGate = false })
    }
}

/**
 * Whether the current screen is one a kid sees: kids mode, outside the grown-up pages
 */
@Composable
fun isKidScreen(): Boolean = LocalKidsMode.current.isActive && !LocalGrownUpPage.current

/**
 * An empty page or list as a kid sees it: the buddy and a short line, in place of "No results"
 */
@Composable
fun KidsEmptyMessage(
    @StringRes text: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        modifier = modifier.fillMaxSize(),
    ) {
        Image(
            painter = painterResource(BuddyMood.HAPPY.drawable),
            contentDescription = null,
            modifier = Modifier.height(140.dp),
        )
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}
