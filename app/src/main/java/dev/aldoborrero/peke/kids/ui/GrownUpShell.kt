package dev.aldoborrero.peke.kids.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.server.KidsPluginService
import dev.aldoborrero.peke.kids.server.KidsServerStatus
import dev.aldoborrero.peke.kids.setup.TvLauncher
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.ui.nav.Destination
import dev.aldoborrero.peke.ui.preferences.PreferenceScreenOption
import dev.aldoborrero.peke.ui.tryRequestFocus
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GrownUpShellViewModel
    @Inject
    constructor(
        private val navigationManager: NavigationManager,
        private val tvLauncher: TvLauncher,
        pluginService: KidsPluginService,
    ) : ViewModel() {
        val pluginState = pluginService.current

        fun open(destination: Destination) = navigationManager.navigateTo(destination)

        /**
         * Back to the kids side's first screen, the picker or the only kid, never a list of users (spec 004)
         */
        fun switchUser() {
            viewModelScope.launch { tvLauncher.launch() }
        }
    }

/**
 * What a grown-up gets in Peke: setting up the TV for the kids, never browsing or watching
 */
@Composable
fun GrownUpShell(
    modifier: Modifier = Modifier,
    viewModel: GrownUpShellViewModel = hiltViewModel(),
) {
    val plugin by viewModel.pluginState.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name_long),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = stringResource(R.string.peke_shell_subtitle),
            style = MaterialTheme.typography.titleMedium,
        )
        if (plugin.status != KidsServerStatus.AVAILABLE) {
            Text(
                text = stringResource(R.string.peke_plugin_needed),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        ) {
            ShellItem(R.string.kids_settings, Icons.Default.Face, Modifier.focusRequester(focus)) {
                viewModel.open(Destination.KidsSettings)
            }
            ShellItem(R.string.settings, Icons.Default.Settings) {
                viewModel.open(Destination.Settings(PreferenceScreenOption.PEKE))
            }
            ShellItem(R.string.peke_shell_switch_user, Icons.Default.Person) { viewModel.switchUser() }
            ShellItem(R.string.license_info, Icons.Default.Info) { viewModel.open(Destination.License) }
        }
    }
}

/**
 * For a user the server couldn't say is or isn't a kid, on a TV that isn't locked to kids: ask again, or pick
 * another user. Never the grown-up shell, which needs the server's "not a kid".
 */
@Composable
fun KidsUnansweredPage(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GrownUpShellViewModel = hiltViewModel(),
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name_long),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = stringResource(R.string.kids_cant_reach_server),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        ) {
            ShellItem(R.string.retry, Icons.Default.Refresh, Modifier.focusRequester(focus), onRetry)
            ShellItem(R.string.peke_shell_switch_user, Icons.Default.Person) { viewModel.switchUser() }
        }
    }
}

@Composable
private fun ShellItem(
    title: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(stringResource(title)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = modifier,
    )
}
