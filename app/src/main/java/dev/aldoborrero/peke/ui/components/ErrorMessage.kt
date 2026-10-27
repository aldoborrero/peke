package dev.aldoborrero.peke.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.aldoborrero.peke.R
import dev.aldoborrero.peke.kids.ui.KidsTroubleMessage
import dev.aldoborrero.peke.kids.ui.isKidScreen
import dev.aldoborrero.peke.services.ServerReportService
import dev.aldoborrero.peke.ui.launchIO
import dev.aldoborrero.peke.util.DataLoadingState
import dev.aldoborrero.peke.util.LoadingState
import javax.inject.Inject

@HiltViewModel
class ErrorViewModel
    @Inject
    constructor(
        private val serverReportService: ServerReportService,
    ) : ViewModel() {
        fun sendLogs() {
            viewModelScope.launchIO { serverReportService.sendAppLogs() }
        }
    }

/**
 * Displays an error message and/or exception
 */
@Composable
fun ErrorMessage(
    message: String?,
    exception: Throwable?,
    modifier: Modifier = Modifier,
    @StringRes kidsTitle: Int? = null,
    viewModel: ErrorViewModel = hiltViewModel(),
) {
    // Kids see the buddy and a friendly line, never the error or the log button (design/DESIGN.md, Words)
    if (isKidScreen()) {
        KidsTroubleMessage(exception = exception, title = kidsTitle, modifier = modifier)
        return
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.compose_error_message_title),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(
            stringRes = R.string.send_app_logs,
            onClick = {
                viewModel.sendLogs()
            },
        )
        message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        exception?.localizedMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        var cause = exception?.cause
        while (cause != null) {
            cause.localizedMessage?.let {
                Text(
                    text = "Caused by: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            cause = cause.cause
        }
    }
}

@Composable
fun ErrorMessage(
    error: LoadingState.Error,
    modifier: Modifier = Modifier,
    @StringRes kidsTitle: Int? = null,
) = ErrorMessage(
    message = error.message,
    exception = error.exception,
    modifier = modifier,
    kidsTitle = kidsTitle,
)

@Composable
fun ErrorMessage(
    error: DataLoadingState.Error,
    modifier: Modifier = Modifier,
) = ErrorMessage(
    message = error.message,
    exception = error.exception,
    modifier = modifier,
)
