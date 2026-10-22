package dev.aldoborrero.peke.kids.gate

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Creates and checks non-reversible verifiers for the kids mode parent passcode
 *
 * Format: `pbkdf2-sha256$<iterations>$<saltHex>$<hashHex>`
 */
object PasscodeHasher {
    const val MIN_LENGTH = 4
    private const val PREFIX = "pbkdf2-sha256"
    private const val ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BYTES = 32

    /**
     * Whether [passcode] is long enough and only uses the keys allowed for [type]
     */
    fun isValid(
        passcode: String,
        type: ParentLockType = ParentLockType.REMOTE,
    ): Boolean = passcode.length >= MIN_LENGTH && passcode.all { it in ParentLockKeys.alphabet(type) }

    fun hash(
        passcode: String,
        random: SecureRandom = SecureRandom(),
    ): String {
        require(isValid(passcode)) { "Invalid passcode" }
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val hash = derive(passcode, salt, ITERATIONS)
        return listOf(PREFIX, ITERATIONS.toString(), salt.toHex(), hash.toHex())
            .joinToString("$")
    }

    fun verify(
        passcode: String,
        verifier: String,
    ): Boolean {
        val parts = verifier.split("$")
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        return try {
            val salt = parts[2].hexToBytes()
            val expected = parts[3].hexToBytes()
            MessageDigest.isEqual(derive(passcode, salt, iterations), expected)
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun derive(
        passcode: String,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        // PBKDF2-HMAC-SHA256 (RFC 8018) with a single block, since the platform algorithm needs API 26
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(passcode.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val result = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in result.indices) result[i] = (result[i].toInt() xor u[i].toInt()).toByte()
        }
        return result.copyOf(KEY_BYTES)
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Odd length" }
    return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
