package dev.aldoborrero.peke.kids.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.gate.AdminCheck
import dev.aldoborrero.peke.kids.setup.CredentialsForm
import dev.aldoborrero.peke.ui.components.BasicDialog
import dev.aldoborrero.peke.ui.isNotNullOrBlank
import dev.aldoborrero.peke.ui.tryRequestFocus

/**
 * A server administrator's user name and password, the same form as the server sign-in. They live only in this
 * dialog's state and are passed to [onSubmit] once.
 */
@Composable
fun KidsAdminSignInDialog(
    stage: GateStage.AdminSignIn,
    onSubmit: (username: String, password: String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val submit = { if (username.isNotBlank() && !stage.checking) onSubmit(username, password) }
    val focusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    // A failed attempt is typed again
    LaunchedEffect(stage.error) { if (stage.error != null) password = "" }
    BasicDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .focusGroup()
                    .padding(16.dp)
                    .fillMaxWidth(.4f),
        ) {
            Text(
                text = stringResource(R.string.kids_admin_sign_in_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            stage.error?.let {
                Text(
                    text = stringResource(adminCheckMessage(it)),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            CredentialsForm(
                username = username,
                onUsernameChange = { username = it },
                password = password,
                onPasswordChange = { password = it },
                onSubmit = submit,
                submitEnabled = username.isNotNullOrBlank() && !stage.checking,
                usernameFocusRequester = focusRequester,
                passwordFocusRequester = passwordFocusRequester,
            )
        }
    }
}

private fun adminCheckMessage(check: AdminCheck): Int =
    when (check) {
        AdminCheck.WRONG -> R.string.kids_admin_wrong
        AdminCheck.NOT_ADMIN -> R.string.kids_admin_not_admin
        AdminCheck.UNREACHABLE -> R.string.kids_cant_reach_server
        AdminCheck.ADMIN, AdminCheck.FAILED -> R.string.kids_admin_failed
    }
