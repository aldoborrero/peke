package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.tryRequestFocus

/**
 * Username and password fields with a login button
 *
 * Next on the username moves to the password, and Go on the password calls [onSubmit], as does the button.
 */
@Composable
fun ColumnScope.CredentialsForm(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
    usernameFocusRequester: FocusRequester,
    passwordFocusRequester: FocusRequester,
    isInputValid: (String) -> Boolean = { true },
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text(
            text = stringResource(R.string.username),
            modifier = Modifier.padding(end = 8.dp),
        )
        EditTextBox(
            value = username,
            onValueChange = onUsernameChange,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
            keyboardActions =
                KeyboardActions(
                    onNext = {
                        passwordFocusRequester.tryRequestFocus()
                    },
                ),
            isInputValid = isInputValid,
            modifier = Modifier.focusRequester(usernameFocusRequester),
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    ) {
        Text(
            text = stringResource(R.string.password),
            modifier = Modifier.padding(end = 8.dp),
        )
        EditTextBox(
            value = password,
            onValueChange = onPasswordChange,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions =
                KeyboardActions(
                    onGo = { onSubmit.invoke() },
                ),
            isInputValid = isInputValid,
            modifier = Modifier.focusRequester(passwordFocusRequester),
        )
    }
    TextButton(
        stringRes = R.string.login,
        onClick = { onSubmit.invoke() },
        enabled = submitEnabled,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}
