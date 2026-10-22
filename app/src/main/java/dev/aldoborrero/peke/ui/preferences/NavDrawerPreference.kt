package dev.aldoborrero.peke.ui.preferences

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.ui.FontAwesome
import dev.aldoborrero.peke.ui.PreviewTvSpec
import dev.aldoborrero.peke.ui.components.Button
import dev.aldoborrero.peke.ui.theme.PekeTheme

@Composable
fun NavDrawerPreferenceListItem(
    title: String,
    pinned: Boolean,
    moveUpAllowed: Boolean,
    moveDownAllowed: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
        ) {
            ListItem(
                selected = false,
                headlineContent = {
                    Text(
                        text = title,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = pinned,
                        onCheckedChange = {
                            onClick.invoke()
                        },
                    )
                },
                onClick = onClick,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.wrapContentWidth(),
            ) {
                MoveButton(R.string.fa_caret_up, moveUpAllowed, onMoveUp)
                MoveButton(R.string.fa_caret_down, moveDownAllowed, onMoveDown)
            }
        }
    }
}

@Composable
fun MoveButton(
    @StringRes icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) = Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.size(32.dp),
) {
    Text(
        text = stringResource(icon),
        fontSize = 16.sp,
        fontFamily = FontAwesome,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@PreviewTvSpec
@Composable
fun NavDrawerPreferenceListItemPreview() {
    PekeTheme {
        NavDrawerPreferenceListItem(
            title = "Movies",
            pinned = true,
            moveUpAllowed = true,
            moveDownAllowed = true,
            onClick = {},
            onMoveUp = {},
            onMoveDown = { },
            modifier = Modifier.width(360.dp),
        )
    }
}
