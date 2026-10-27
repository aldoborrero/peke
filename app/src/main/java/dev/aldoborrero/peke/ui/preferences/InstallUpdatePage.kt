package dev.aldoborrero.peke.ui.preferences

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.preferences.UserPreferences
import dev.aldoborrero.peke.services.DownloadCallback
import dev.aldoborrero.peke.services.NavigationManager
import dev.aldoborrero.peke.services.Release
import dev.aldoborrero.peke.services.UpdateChecker
import dev.aldoborrero.peke.ui.OneTimeLaunchedEffect
import dev.aldoborrero.peke.ui.PreviewTvSpec
import dev.aldoborrero.peke.ui.components.BasicDialog
import dev.aldoborrero.peke.ui.components.ErrorMessage
import dev.aldoborrero.peke.ui.components.LoadingPage
import dev.aldoborrero.peke.ui.components.TextButton
import dev.aldoborrero.peke.ui.dimAndBlur
import dev.aldoborrero.peke.ui.formatBytes
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.ui.theme.LocalPekeColors
import dev.aldoborrero.peke.ui.theme.PekeFocus
import dev.aldoborrero.peke.ui.theme.PekeIcon
import dev.aldoborrero.peke.ui.theme.PekeTheme
import dev.aldoborrero.peke.ui.theme.cpx
import dev.aldoborrero.peke.ui.theme.csp
import dev.aldoborrero.peke.ui.theme.pekeFocus
import dev.aldoborrero.peke.util.ExceptionHandler
import dev.aldoborrero.peke.util.LoadingState
import dev.aldoborrero.peke.util.Version
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        val updater: UpdateChecker,
        val navigationManager: NavigationManager,
    ) : ViewModel(),
        DownloadCallback {
        private val _state = MutableStateFlow(InstallUpdateState())
        val state: StateFlow<InstallUpdateState> = _state

        private val _bytesDownloaded = MutableStateFlow(0L)
        val bytesDownloaded: StateFlow<Long> = _bytesDownloaded

        val currentVersion = updater.getInstalledVersion()

        fun init() {
            _state.update { it.copy(loading = LoadingState.Loading) }
            viewModelScope.launchIO {
                try {
                    val release = updater.getLatestRelease()
                    _state.update {
                        it.copy(
                            loading = LoadingState.Success,
                            release = release,
                            contentLength = -1L,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching the latest release")
                    _state.update { it.copy(loading = LoadingState.Error(ex)) }
                }
            }
        }

        private var downloadJob: Job? = null

        fun installRelease(release: Release) {
            downloadJob =
                viewModelScope.launchIO {
                    try {
                        _bytesDownloaded.value = 0L
                        _state.update { it.copy(downloading = true) }
                        updater.installRelease(release, this@UpdateViewModel)
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error downloading")
                        _state.update { it.copy(loading = LoadingState.Error(ex)) }
                    } finally {
                        _state.update { it.copy(downloading = false) }
                    }
                }
        }

        fun cancelDownload() {
            viewModelScope.launchIO {
                downloadJob?.cancel()
                _state.update {
                    it.copy(
                        downloading = false,
                        contentLength = -1L,
                    )
                }
            }
        }

        override fun contentLength(contentLength: Long) {
            _state.update { it.copy(contentLength = contentLength) }
        }

        override fun bytesDownloaded(bytes: Long) {
            _bytesDownloaded.value = bytes
        }
    }

data class InstallUpdateState(
    val loading: LoadingState = LoadingState.Pending,
    val downloading: Boolean = false,
    val release: Release? = null,
    val contentLength: Long = -1L,
)

@Composable
fun InstallUpdatePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    OneTimeLaunchedEffect { viewModel.init() }

    val state by viewModel.state.collectAsState()
    var permissions by remember { mutableStateOf(viewModel.updater.hasPermissions()) }
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            if (isGranted) {
                permissions = true
            } else {
                // TODO
            }
        }
    when (val st = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            val release = state.release
            if (release != null) {
                InstallUpdatePageContent(
                    currentVersion = viewModel.currentVersion,
                    release = release,
                    onInstallRelease = {
                        if (!permissions) {
                            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            viewModel.installRelease(release)
                        }
                    },
                    onCancel = {
                        viewModel.navigationManager.goBack()
                    },
                    modifier = modifier.dimAndBlur(state.downloading),
                )
            } else {
                Text(
                    text = "No release found! Check the update URL.",
                )
            }
            if (state.downloading) {
                val bytesDownloaded by viewModel.bytesDownloaded.collectAsState(0L)
                DownloadDialog(
                    contentLength = state.contentLength,
                    bytesDownloaded = bytesDownloaded,
                    onDismissRequest = {
                        viewModel.cancelDownload()
                    },
                )
            }
        }
    }
}

/**
 * The update page (OtherUpdatePekeTV): on the left the download icon, "Update available", the installed and the new
 * version, and Download & Update over Later; on the right the release notes on a surface panel, scrolled with up and
 * down. What the buttons do is unchanged.
 */
@Composable
fun InstallUpdatePageContent(
    currentVersion: Version,
    release: Release,
    onInstallRelease: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPekeColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(56.cpx),
        modifier = modifier.padding(horizontal = 72.cpx, vertical = 56.cpx),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.cpx),
            modifier = Modifier.width(360.cpx),
        ) {
            Icon(
                painter = painterResource(PekeIcon.DOWNLOAD.outline),
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(120.cpx),
            )
            Text(
                text = stringResource(R.string.update_available),
                style = MaterialTheme.typography.displaySmall,
                color = colors.text,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.cpx)) {
                Text(
                    text = stringResource(R.string.update_installed_version, currentVersion.toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Text(
                    text = stringResource(R.string.update_new_version, release.version.toString()),
                    fontSize = 22.csp,
                    fontWeight = FontWeight.W900,
                    color = colors.time,
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(12.cpx),
                modifier = Modifier.padding(top = 10.cpx),
            ) {
                UpdatePill(
                    text = stringResource(R.string.download_and_update),
                    primary = true,
                    onClick = onInstallRelease,
                )
                UpdatePill(
                    text = stringResource(R.string.update_later),
                    primary = false,
                    onClick = onCancel,
                )
            }
        }

        val scrollAmount = 100f
        val columnState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        fun scroll(reverse: Boolean = false) {
            scope.launch(ExceptionHandler()) {
                columnState.scrollBy(if (reverse) -scrollAmount else scrollAmount)
            }
        }
        val columnInteractionSource = remember { MutableInteractionSource() }
        val panelShape = RoundedCornerShape(30.cpx)
        LazyColumn(
            state = columnState,
            contentPadding = PaddingValues(horizontal = 30.cpx, vertical = 28.cpx),
            verticalArrangement = Arrangement.spacedBy(14.cpx),
            modifier =
                Modifier
                    .pekeFocus(panelShape)
                    .focusable(interactionSource = columnInteractionSource)
                    .fillMaxHeight()
                    .weight(1f)
                    .background(colors.surface, shape = panelShape)
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyUp) {
                            return@onKeyEvent false
                        }
                        if (it.key == Key.DirectionDown) {
                            scroll(false)
                            return@onKeyEvent true
                        }
                        if (it.key == Key.DirectionUp) {
                            scroll(true)
                            return@onKeyEvent true
                        }
                        return@onKeyEvent false
                    },
        ) {
            item {
                Text(
                    text = stringResource(R.string.update_whats_new),
                    fontSize = 22.csp,
                    fontWeight = FontWeight.W900,
                    color = colors.text,
                )
            }
            item {
                ReleaseNotes(release)
            }
        }
    }
}

/**
 * A full-width pill of the update page: Download & Update 64 px in primary, Later 60 px on surface
 */
@Composable
private fun UpdatePill(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPekeColors.current
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = PekeFocus.surfaceScale,
        border = PekeFocus.surfaceBorder(),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) colors.primary else colors.surface,
                contentColor = if (primary) colors.onPrimary else colors.text,
                focusedContainerColor = colors.primary,
                focusedContentColor = colors.onPrimary,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(if (primary) 64.cpx else 60.cpx)
                .pekeFocus(CircleShape),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = if (primary) 22.csp else 20.csp,
                fontWeight = if (primary) FontWeight.W900 else FontWeight.W800,
            )
        }
    }
}

@Composable
fun ReleaseNotes(
    release: Release,
    modifier: Modifier = Modifier,
) {
    Markdown(
        content = release.content,
        typography =
            markdownTypography(
                h1 = MaterialTheme.typography.headlineLarge,
                h2 = MaterialTheme.typography.headlineMedium,
                h3 = MaterialTheme.typography.headlineSmall,
                text = MaterialTheme.typography.bodySmall,
                code = MaterialTheme.typography.bodySmall,
            ),
        modifier = modifier,
    )
}

@Composable
fun DownloadDialog(
    contentLength: Long,
    bytesDownloaded: Long,
    onDismissRequest: () -> Unit,
) {
    val progress =
        if (contentLength > 0) {
            bytesDownloaded.toFloat() / contentLength
        } else {
            null
        }
    BasicDialog(
        onDismissRequest = onDismissRequest,
        elevation = 6.dp,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier,
            ) {
                Text(
                    text = stringResource(R.string.downloading),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (progress == null) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.border,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .padding(8.dp),
                    )
                }
            }
            if (progress != null) {
                // OtherUpdatePekeTV: a 14 px bar in the secondary colour
                val colors = LocalPekeColors.current
                Box(
                    Modifier
                        .width(480.cpx)
                        .height(14.cpx)
                        .background(colors.background, CircleShape),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(14.cpx)
                            .background(colors.secondary, CircleShape),
                    )
                }
                val bytes = formatBytes(bytesDownloaded)
                val size = formatBytes(contentLength)
                Text(
                    text = "$bytes / $size",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun InstallUpdatePageContentPreview() {
    PekeTheme {
        InstallUpdatePageContent(
            currentVersion = Version.fromString("v0.4.0"),
            release =
                Release(
                    version = Version.fromString("v0.5.3"),
                    downloadUrl = "https://url",
                    publishedAt = null,
                    body =
                        "## Header 2\n" +
                            "Lorem ipsum dolor sit amet consectetur adipiscing elit. Quisque faucibus " +
                            "ex sapien vitae pellentesque sem placerat. In id cursus mi pretium " +
                            "tellus duis convallis. Tempus leo eu aenean sed diam urna tempor. " +
                            "Pulvinar vivamus fringilla lacus nec metus bibendum egestas. " +
                            "Iaculis massa nisl malesuada lacinia integer nunc posuere. " +
                            "Ut hendrerit semper vel class aptent taciti sociosqu. Ad litora " +
                            "torquent per conubia nostra inceptos himenaeos.\n\n" +
                            "### Header 3\n" +
                            "Lorem ipsum dolor sit amet consectetur adipiscing elit. Quisque faucibus " +
                            "ex sapien vitae pellentesque sem placerat. In id cursus mi pretium " +
                            "tellus duis convallis. Tempus leo eu aenean sed diam urna tempor. " +
                            "Pulvinar vivamus fringilla lacus nec metus bibendum egestas. " +
                            "Iaculis massa nisl malesuada lacinia integer nunc posuere. " +
                            "Ut hendrerit semper vel class aptent taciti sociosqu. Ad litora " +
                            "torquent per conubia nostra inceptos himenaeos.",
                    notes = listOf(),
                ),
            onInstallRelease = {},
            onCancel = {},
        )
    }
}
