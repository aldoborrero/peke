package dev.aldoborrero.peke.kids

import dev.aldoborrero.peke.kids.gate.PasscodeHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasscodeHasherTest {
    @Test
    fun `hash verifies with the same passcode only`() {
        val verifier = PasscodeHasher.hash("1234")
        assertTrue(PasscodeHasher.verify("1234", verifier))
        assertFalse(PasscodeHasher.verify("1235", verifier))
        assertFalse(PasscodeHasher.verify("", verifier))
    }

    @Test
    fun `verifier does not contain the passcode and is salted`() {
        val a = PasscodeHasher.hash("987654")
        val b = PasscodeHasher.hash("987654")
        assertFalse(a.contains("987654"))
        assertNotEquals(a, b)
    }

    @Test
    fun `matches the JDK PBKDF2 implementation`() {
        val salt = ByteArray(16) { it.toByte() }
        val iterations = 1000
        val spec = PBEKeySpec("2468".toCharArray(), salt, iterations, 256)
        val expected = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val verifier = "pbkdf2-sha256$$iterations$${salt.hex()}$${expected.hex()}"
        assertTrue(PasscodeHasher.verify("2468", verifier))
        assertFalse(PasscodeHasher.verify("2469", verifier))
    }

    @Test
    fun `malformed verifiers never verify`() {
        listOf("", "garbage", "pbkdf2-sha256$0$00$00", "pbkdf2-sha256\$10\$zz\$00", "md5$1$00$00").forEach {
            assertFalse(it, PasscodeHasher.verify("1234", it))
        }
    }

    @Test
    fun `validity requires at least four digits`() {
        assertTrue(PasscodeHasher.isValid("0000"))
        assertFalse(PasscodeHasher.isValid("123"))
        assertFalse(PasscodeHasher.isValid("12a4"))
        assertTrue(PasscodeHasher.isValid("UDL7"))
        assertEquals(4, PasscodeHasher.MIN_LENGTH)
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
