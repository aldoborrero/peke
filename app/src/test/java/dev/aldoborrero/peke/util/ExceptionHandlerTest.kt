package dev.aldoborrero.peke.util

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.aldoborrero.peke.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Spec 005 FR-019 (review P5): the error toast never waits for the main thread, and never shows the exception
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ExceptionHandlerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `on the main thread it returns at once and shows a friendly toast`() {
        // Robolectric runs the test on the main looper, where the old runBlocking deadlocked
        assertEquals(Looper.getMainLooper(), Looper.myLooper())
        ExceptionHandler(autoToast = true) { context }.handleException(EmptyCoroutineContext, IllegalStateException("HTTP 500 at /Items"))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(context.getString(R.string.peke_something_went_wrong), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `without auto toast nothing shows`() {
        ExceptionHandler { context }.handleException(EmptyCoroutineContext, IllegalStateException("boom"))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, ShadowToast.shownToastCount())
    }
}
