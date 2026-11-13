package dev.aldoborrero.peke.kids.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.tryRequestFocus

/**
 * "Leave Peke?" (spec 024): asked by Back on the kids home with the sidebar closed. "Keep watching" has focus first;
 * it and Back call [onDismissRequest]. "Leave" calls [onLeave]. It asks, it doesn't lock: no parent code
 * (constitution IV). The panel is the page background, as the grown-up pages' that [GrownUpButton] is drawn for, so
 * "Leave" in surface stands out from it; the dim behind the dialog sets it off from the page.
 */
@Composable
fun KidsLeaveDialog(
    onLeave: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val colors = LocalPekeColors.current
    val keepWatching = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(maxOf(28.cpx, PekeFocus.clearance(60.cpx, grow = false))),
            modifier =
                Modifier
                    .widthIn(max = 640.cpx)
                    .background(colors.background, RoundedCornerShape(36.cpx))
                    .padding(36.cpx),
        ) {
            Text(
                text = stringResource(R.string.kids_leave_title),
                style = boardText(34, FontWeight.W900),
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(maxOf(24.cpx, PekeFocus.clearance(60.cpx, grow = false)))) {
                GrownUpButton(
                    text = stringResource(R.string.kids_leave_keep_watching),
                    onClick = onDismissRequest,
                    primary = true,
                    modifier = Modifier.focusRequester(keepWatching),
                )
                GrownUpButton(text = stringResource(R.string.kids_leave_leave), onClick = onLeave)
            }
        }
    }
    LaunchedEffect(Unit) { keepWatching.tryRequestFocus() }
}
