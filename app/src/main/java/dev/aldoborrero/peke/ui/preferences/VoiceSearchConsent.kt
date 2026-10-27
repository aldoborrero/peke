package dev.aldoborrero.peke.ui.preferences

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.ui.findActivity
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.theme.pekeFocus
import dev.aldoborrero.peke.ui.tryRequestFocus

private enum class ConsentStage {
    EXPLAIN,
    DENIED,
    DENIED_FOR_GOOD,
}

/**
 * Turning voice search on for kids (spec 005 FR-007, FR-008): first a plain explanation of where the voice goes, then
 * the system's microphone prompt, which the grown-up answers. [onEnabled] runs only once the microphone is allowed.
 * Peke never opens the system settings itself; when Android won't ask again, the grown-up is told where to allow it.
 *
 * Only for grown-up screens: kid screens never request the microphone.
 */
@Composable
fun VoiceSearchConsent(
    onEnabled: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    var stage by remember { mutableStateOf(ConsentStage.EXPLAIN) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                onEnabled()
            } else {
                val askAgain = context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true
                stage = if (askAgain) ConsentStage.DENIED else ConsentStage.DENIED_FOR_GOOD
            }
        }
    when (stage) {
        ConsentStage.EXPLAIN -> {
            MicCard(
                title = stringResource(R.string.peke_voice_search_explain_title),
                body = stringResource(R.string.peke_voice_search_explain),
                confirm = stringResource(R.string.peke_voice_search_allow),
                onConfirm = {
                    val granted =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    if (granted) onEnabled() else launcher.launch(Manifest.permission.RECORD_AUDIO)
                },
                cancel = stringResource(R.string.peke_voice_search_not_now),
                onDismissRequest = onDismissRequest,
            )
        }

        ConsentStage.DENIED, ConsentStage.DENIED_FOR_GOOD -> {
            MicCard(
                title = null,
                body =
                    stringResource(
                        if (stage == ConsentStage.DENIED) {
                            R.string.peke_voice_search_denied
                        } else {
                            R.string.peke_voice_search_denied_for_good
                        },
                    ),
                confirm = stringResource(R.string.confirm),
                onConfirm = onDismissRequest,
                cancel = null,
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

/**
 * The microphone question (OtherMicPekeTV): a 720 px card with the voice icon in tertiary, the title, the explanation
 * and the buttons, the first one focused
 */
@Composable
private fun MicCard(
    title: String?,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    cancel: String?,
    onDismissRequest: () -> Unit,
) {
    val colors = LocalPekeColors.current
    val focusRequester = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.cpx),
            modifier =
                Modifier
                    .width(720.cpx)
                    .background(colors.surface, RoundedCornerShape(36.cpx))
                    .padding(40.cpx),
        ) {
            Icon(
                painter = painterResource(PekeIcon.VOICE.outline),
                contentDescription = null,
                tint = colors.tertiary,
                modifier = Modifier.size(120.cpx),
            )
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.text,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = body,
                fontSize = 21.csp,
                fontWeight = FontWeight.W800,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.cpx),
                modifier = Modifier.padding(top = 8.cpx),
            ) {
                MicPill(text = confirm, primary = true, onClick = onConfirm, modifier = Modifier.focusRequester(focusRequester))
                cancel?.let { MicPill(text = it, primary = false, onClick = onDismissRequest) }
            }
        }
    }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
}

/**
 * A 64 px pill: primary for the answer that goes on, the page colour for the other
 */
@Composable
private fun MicPill(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = PekeFocus.surfaceScale,
        border = PekeFocus.surfaceBorder(),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) colors.primary else colors.background,
                contentColor = if (primary) colors.onPrimary else colors.text,
                focusedContainerColor = colors.primary,
                focusedContentColor = colors.onPrimary,
            ),
        modifier = modifier.height(64.cpx).pekeFocus(CircleShape),
    ) {
        Box(Modifier.height(64.cpx).padding(horizontal = 36.cpx), contentAlignment = Alignment.Center) {
            Text(text = text, fontSize = 24.csp, fontWeight = if (primary) FontWeight.W900 else FontWeight.W800)
        }
    }
}
