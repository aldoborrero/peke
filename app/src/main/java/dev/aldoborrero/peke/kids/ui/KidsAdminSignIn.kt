package dev.aldoborrero.peke.kids.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import dev.aldoborrero.peke.ui.components.CircularProgress
import dev.aldoborrero.peke.ui.components.TextButton
import dev.aldoborrero.peke.ui.isNotNullOrBlank
import dev.aldoborrero.peke.ui.tryRequestFocus

/**
 * A server administrator's sign-in: the user name and password, as in the server sign-in, or a Quick Connect code to
 * approve in another Jellyfin app, as in setup (F-G4). The credentials live only in this dialog's state and are passed
 * to [onSubmit] once.
 */
@Composable
fun KidsAdminSignInDialog(
    stage: GateStage.AdminSignIn,
    onSubmit: (username: String, password: String) -> Unit,
    onQuickConnect: () -> Unit,
    onPassword: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .focusGroup()
                    .padding(16.dp)
                    .fillMaxWidth(.4f),
        ) {
            if (stage.recovery) {
                Text(
                    text = stringResource(R.string.kids_gate_forgot),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
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
            if (stage.quickConnect) {
                QuickConnectSignIn(stage, onPassword)
            } else {
                PasswordSignIn(stage, onSubmit, onQuickConnect)
            }
        }
    }
}

@Composable
private fun ColumnScope.PasswordSignIn(
    stage: GateStage.AdminSignIn,
    onSubmit: (username: String, password: String) -> Unit,
    onQuickConnect: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val submit = { if (username.isNotBlank() && !stage.checking) onSubmit(username, password) }
    val focusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    // A failed attempt is typed again
    LaunchedEffect(stage.error) { if (stage.error != null) password = "" }
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
    if (stage.checking) CircularProgress(Modifier.size(32.dp))
    TextButton(
        stringRes = R.string.peke_setup_quick_connect,
        onClick = onQuickConnect,
        enabled = !stage.checking,
    )
}

@Composable
private fun ColumnScope.QuickConnectSignIn(
    stage: GateStage.AdminSignIn,
    onPassword: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val code = stage.quickConnectCode
    if (code == null) {
        if (stage.checking) CircularProgress(Modifier.size(32.dp))
    } else {
        Text(
            text = stringResource(R.string.peke_setup_quick_connect_hint),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = code,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    TextButton(
        stringRes = R.string.peke_setup_use_password,
        onClick = onPassword,
        modifier = Modifier.focusRequester(focusRequester),
    )
}

private fun adminCheckMessage(check: AdminCheck): Int =
    when (check) {
        AdminCheck.WRONG -> R.string.kids_admin_wrong
        AdminCheck.NOT_ADMIN -> R.string.kids_admin_not_admin
        AdminCheck.UNREACHABLE -> R.string.kids_cant_reach_server
        AdminCheck.QUICK_CONNECT_OFF -> R.string.peke_setup_quick_connect_off
        AdminCheck.ADMIN, AdminCheck.FAILED -> R.string.kids_admin_failed
    }
