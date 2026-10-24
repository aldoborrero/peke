package dev.aldoborrero.peke.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import dev.aldoborrero.peke.PekeApplication
import dev.aldoborrero.peke.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

/**
 * A general [CoroutineExceptionHandler] which can optionally show a [Toast] when an exception is thrown
 *
 * @param autoToast automatically show a friendly toast; the exception itself only goes to the log, since the person
 *   in front of the TV is usually a kid (DESIGN Words, spec 005 FR-019)
 */
class ExceptionHandler(
    private val autoToast: Boolean = false,
    private val context: () -> Context = { PekeApplication.instance },
) : CoroutineExceptionHandler {
    override val key: CoroutineContext.Key<*>
        get() = CoroutineExceptionHandler

    override fun handleException(
        context: CoroutineContext,
        exception: Throwable,
    ) {
        if (exception is CancellationException) {
            // Don't log/toast cancellations
            return
        }
        Timber.e(exception, "Exception in coroutine")

        if (autoToast) {
            // Posted, never waited for: this can run on the main thread itself (review P5)
            val appContext = this.context()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, R.string.peke_something_went_wrong, Toast.LENGTH_LONG).show()
            }
        }
    }
}
