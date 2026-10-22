package com.github.damontecres.wholphin.kids.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.kids.LocalKidsLocked
import com.github.damontecres.wholphin.ui.components.LoadingPage

/**
 * Shows [content] only when the device is not locked to kids mode. Otherwise children are asked to get a grown-up.
 *
 * The lock comes from [LocalKidsLocked], so a device without kids creates nothing for kids mode here.
 */
@Composable
fun KidsLockGuard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when (LocalKidsLocked.current) {
        null -> {
            LoadingPage()
        }

        false -> {
            content()
        }

        true -> {
            var showGate by remember { mutableStateOf(false) }
            KidsGrownUpsMessage(
                title = stringResource(R.string.kids_ask_grown_up),
                subtitle = null,
                onClickGrownUps = { showGate = true },
                modifier = modifier,
            )
            if (showGate) {
                KidsParentGate(onDismissRequest = { showGate = false }, unlockOnly = true)
            }
        }
    }
}
