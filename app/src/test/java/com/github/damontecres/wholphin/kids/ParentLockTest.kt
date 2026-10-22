package com.github.damontecres.wholphin.kids

import android.view.KeyEvent
import com.github.damontecres.wholphin.kids.gate.ParentLockKeys
import com.github.damontecres.wholphin.kids.gate.ParentLockType
import com.github.damontecres.wholphin.kids.gate.PasscodeHasher
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
    fun `remote codes accept the D-pad, digits and extra buttons`() {
        assertEquals('U', ParentLockKeys.symbol(KeyEvent.KEYCODE_DPAD_UP, ParentLockType.REMOTE))
        assertEquals('0', ParentLockKeys.symbol(KeyEvent.KEYCODE_0, ParentLockType.REMOTE))
        assertEquals('r', ParentLockKeys.symbol(KeyEvent.KEYCODE_PROG_RED, ParentLockType.REMOTE))
        assertEquals('+', ParentLockKeys.symbol(KeyEvent.KEYCODE_CHANNEL_UP, ParentLockType.REMOTE))
        assertNull(ParentLockKeys.symbol(KeyEvent.KEYCODE_DPAD_CENTER, ParentLockType.REMOTE))
        assertNull(ParentLockKeys.symbol(KeyEvent.KEYCODE_BACK, ParentLockType.REMOTE))
        assertTrue(PasscodeHasher.isValid("UrgD", ParentLockType.REMOTE))
    }
}
