/*
 * Synthetic regression fixtures only: no private keystore, APK or device is used.
 */
package app.morphe.engine.util

import java.nio.file.Files
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignatureIdentitySha256Test {
    private fun certificate(data: ByteArray): Certificate = object : Certificate("Synthetic") {
        override fun getEncoded(): ByteArray = data.clone()
        override fun verify(key: PublicKey?) = Unit
        override fun verify(key: PublicKey?, sigProvider: String?) = Unit
        override fun toString(): String = "Synthetic certificate"
        override fun getPublicKey(): PublicKey = error("Not required in fingerprint tests")
    }

    @Test
    fun `SHA256 certificate fingerprint is deterministic and exact`() {
        val sample = byteArrayOf(0x01, 0x7f, 0x09, 0x3f)
        val expected = MessageDigest.getInstance("SHA-256").digest(sample)
            .joinToString("") { "%02x".format(it) }
        val computed = SignatureIdentity.sha256ForCert(certificate(sample))
        assertEquals(expected, computed)
        assertTrue(computed.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `missing or invalid signing inputs fail closed`() {
        val missing = Files.createTempDirectory("morphe-synthetic-signer").resolve("missing.apk")
        try {
            assertNull(SignatureIdentity.sha256ForApkSigners(missing.toFile()))
            assertNull(SignatureIdentity.sha256ForKeystore(missing.toFile(), null, "alias"))
            Files.write(missing, byteArrayOf(0x01, 0x02, 0x03))
            assertNull(SignatureIdentity.sha256ForApkSigners(missing.toFile()))
        } finally {
            Files.deleteIfExists(missing)
            Files.deleteIfExists(missing.parent)
        }
    }
}
