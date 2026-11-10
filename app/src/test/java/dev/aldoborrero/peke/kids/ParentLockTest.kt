package dev.aldoborrero.peke.kids

import android.view.KeyEvent
import dev.aldoborrero.peke.kids.gate.ParentLockKeys
import dev.aldoborrero.peke.kids.gate.ParentLockType
import dev.aldoborrero.peke.kids.gate.PasscodeHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentLockTest {
    @Test
    fun `numeric codes only accept digits`() {
        assertEquals('7', ParentLockKeys.symbol(KeyEvent.KEYCODE_7, ParentLockType.NUMERIC))
        assertEquals('3', ParentLockKeys.symbol(KeyEvent.KEYCODE_NUMPAD_3, ParentLockType.NUMERIC))
        assertNull(ParentLockKeys.symbol(KeyEvent.KEYCODE_DPAD_UP, ParentLockType.NUMERIC))
        assertNull(ParentLockKeys.symbol(KeyEvent.KEYCODE_PROG_RED, ParentLockType.NUMERIC))
        assertTrue(PasscodeHasher.isValid("1234", ParentLockType.NUMERIC))
        assertFalse(PasscodeHasher.isValid("12U4", ParentLockType.NUMERIC))
    }

    @Test
    fun `remote codes only accept the four arrows`() {
        assertEquals('U', ParentLockKeys.symbol(KeyEvent.KEYCODE_DPAD_UP, ParentLockType.REMOTE))
        assertEquals('D', ParentLockKeys.symbol(KeyEvent.KEYCODE_DPAD_DOWN, ParentLockType.REMOTE))
        assertEquals('L', ParentLockKeys.symbol(KeyEvent.KEYCODE_DPAD_LEFT, ParentLockType.REMOTE))
        assertEquals('R', ParentLockKeys.symbol(KeyEvent.KEYCODE_DPAD_RIGHT, ParentLockType.REMOTE))
        assertNull(ParentLockKeys.symbol(KeyEvent.KEYCODE_0, ParentLockType.REMOTE))
        assertNull(ParentLockKeys.symbol(KeyEvent.KEYCODE_NUMPAD_5, ParentLockType.REMOTE))
        listOf(
            KeyEvent.KEYCODE_PROG_RED,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BACK,
        ).forEach { assertNull(ParentLockKeys.symbol(it, ParentLockType.REMOTE)) }
        assertTrue(PasscodeHasher.isValid("ULDR", ParentLockType.REMOTE))
        assertFalse(PasscodeHasher.isValid("UrgD", ParentLockType.REMOTE))
        assertFalse(PasscodeHasher.isValid("UD12", ParentLockType.REMOTE))
    }
}
