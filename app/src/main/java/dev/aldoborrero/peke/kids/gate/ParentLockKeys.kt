package dev.aldoborrero.peke.kids.gate

import android.view.InputDevice
import android.view.KeyEvent

/**
 * Which remote keys make up a parent code, and the symbol each one stores
 *
 * D-pad directions and digits use the same symbols as Wholphin's profile PIN (U, D, L, R, 0-9).
 */
object ParentLockKeys {
    /**
     * Buttons beyond the D-pad and digits that many remotes have, with the symbol stored for each
     */
    enum class Extra(
        val keyCode: Int,
        val symbol: Char,
    ) {
        RED(KeyEvent.KEYCODE_PROG_RED, 'r'),
        GREEN(KeyEvent.KEYCODE_PROG_GREEN, 'g'),
        YELLOW(KeyEvent.KEYCODE_PROG_YELLOW, 'y'),
        BLUE(KeyEvent.KEYCODE_PROG_BLUE, 'b'),
        PLAY_PAUSE(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 'p'),
        REWIND(KeyEvent.KEYCODE_MEDIA_REWIND, 'w'),
        FAST_FORWARD(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, 'f'),
        CHANNEL_UP(KeyEvent.KEYCODE_CHANNEL_UP, '+'),
        CHANNEL_DOWN(KeyEvent.KEYCODE_CHANNEL_DOWN, '-'),
        INFO(KeyEvent.KEYCODE_INFO, 'i'),
    }

    private const val DPAD = "UDLR"
    private const val DIGITS = "0123456789"

    fun alphabet(type: ParentLockType): Set<Char> =
        when (type) {
            ParentLockType.NUMERIC -> DIGITS.toSet()
            ParentLockType.REMOTE -> (DPAD + DIGITS + Extra.entries.map { it.symbol }.joinToString("")).toSet()
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
                else -> Extra.entries.firstOrNull { it.keyCode == keyCode }?.symbol
            }
        return symbol?.takeIf { it in alphabet(type) }
    }

    /**
     * The extra buttons that a connected remote or keyboard reports having
     */
    fun availableExtras(): List<Extra> {
        val codes = Extra.entries.map { it.keyCode }.toIntArray()
        val found = BooleanArray(codes.size)
        InputDevice.getDeviceIds().forEach { id ->
            val device = InputDevice.getDevice(id) ?: return@forEach
            if (device.isVirtual) return@forEach
            device.hasKeys(*codes).forEachIndexed { i, has -> if (has) found[i] = true }
        }
        return Extra.entries.filterIndexed { i, _ -> found[i] }
    }
}
