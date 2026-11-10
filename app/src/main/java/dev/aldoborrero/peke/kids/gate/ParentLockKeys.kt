package dev.aldoborrero.peke.kids.gate

import android.view.KeyEvent

/**
 * Which remote keys make up a parent code, and the symbol each one stores
 *
 * A code is one kind of key only: the four arrows (U, D, L, R) for [ParentLockType.REMOTE], the digits for
 * [ParentLockType.NUMERIC] (spec 019 D11). The symbols are the profile PIN's.
 */
object ParentLockKeys {
    private const val DPAD = "UDLR"
    private const val DIGITS = "0123456789"

    fun alphabet(type: ParentLockType): Set<Char> =
        when (type) {
            ParentLockType.NUMERIC -> DIGITS.toSet()
            ParentLockType.REMOTE -> DPAD.toSet()
        }

    /**
     * The symbol a key adds to a code of [type], or null if the key is not part of such codes
     */
    fun symbol(
        keyCode: Int,
        type: ParentLockType,
    ): Char? {
        val symbol =
            when (keyCode) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> '0' + (keyCode - KeyEvent.KEYCODE_0)
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> '0' + (keyCode - KeyEvent.KEYCODE_NUMPAD_0)
                KeyEvent.KEYCODE_DPAD_UP -> 'U'
                KeyEvent.KEYCODE_DPAD_DOWN -> 'D'
                KeyEvent.KEYCODE_DPAD_LEFT -> 'L'
                KeyEvent.KEYCODE_DPAD_RIGHT -> 'R'
                else -> null
            }
        return symbol?.takeIf { it in alphabet(type) }
    }
}
