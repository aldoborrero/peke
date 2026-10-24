package dev.aldoborrero.peke.ui.preferences

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.ui.components.BasicDialog
import dev.aldoborrero.peke.ui.components.ConfirmDialog
import dev.aldoborrero.peke.ui.components.TextButton
import dev.aldoborrero.peke.ui.findActivity

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
            ConfirmDialog(
                title = stringResource(R.string.peke_voice_search_explain_title),
                body = stringResource(R.string.peke_voice_search_explain),
                onCancel = onDismissRequest,
                onConfirm = {
                    val granted =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                    if (granted) onEnabled() else launcher.launch(Manifest.permission.RECORD_AUDIO)
                },
            )
        }

        ConsentStage.DENIED, ConsentStage.DENIED_FOR_GOOD -> {
            BasicDialog(
                onDismissRequest = onDismissRequest,
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth(.5f),
                ) {
                    Text(
                        text =
                            stringResource(
                                if (stage == ConsentStage.DENIED) {
                                    R.string.peke_voice_search_denied
                                } else {
                                    R.string.peke_voice_search_denied_for_good
                                },
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(stringRes = R.string.confirm, onClick = onDismissRequest)
                }
            }
        }
    }
}
