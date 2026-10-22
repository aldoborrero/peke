package com.github.damontecres.wholphin.kids.timer

import android.os.SystemClock

/**
 * A clock that is not affected by changes to the device's wall clock
 */
fun interface MonotonicClock {
    fun elapsedMillis(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
}
