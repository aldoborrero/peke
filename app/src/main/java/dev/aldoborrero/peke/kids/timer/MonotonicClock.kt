package dev.aldoborrero.peke.kids.timer

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings

/**
 * A clock that is not affected by changes to the device's wall clock
 */
fun interface MonotonicClock {
    fun elapsedMillis(): Long

    /**
     * How many times the device has booted, to tell that [elapsedMillis] started again, or null if it can't be told
     */
    fun bootCount(): Int? = null
}

class SystemMonotonicClock(
    private val context: Context,
) : MonotonicClock {
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        } catch (_: Settings.SettingNotFoundException) {
            null
        }
    }
}
