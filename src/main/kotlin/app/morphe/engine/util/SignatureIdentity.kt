/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest
import java.security.KeyStore
import java.security.Provider
import java.security.Security
import java.security.cert.Certificate

/** Computes and parses Android package signature hashes (`dumpsys package`) for device matching. */
object SignatureIdentity {

    /** Computes Android signature hash for [cert]. */
    fun idForCert(cert: Certificate): String =
        Integer.toHexString(cert.encoded.contentHashCode())

    /** Computes signature hash for [alias] in [keystoreFile], or null if unreadable. */
    fun idForKeystore(keystoreFile: File, storePassword: String?, alias: String): String? = try {
        certificateForKeystore(keystoreFile, storePassword, alias)?.let(::idForCert)
    } catch (_: Exception) {
        null
    }


    /** Collision-resistant fingerprint for matching verified APKs against signing identities. */
    fun sha256ForCert(cert: Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }

    /** Public certificate fingerprint of the selected keystore alias, if readable. */
    fun sha256ForKeystore(keystoreFile: File, storePassword: String?, alias: String): String? = try {
        certificateForKeystore(keystoreFile, storePassword, alias)?.let(::sha256ForCert)
    } catch (_: Exception) {
        null
    }

    /**
     * Read-only APK signer verification. A bad APK, unverifiable certificate
     * or empty signer set is UNKNOWN (null), never an ownership success.
     * Handles v1/v2/v3/v3.1 signatures through Android's APK verifier.
     */
    fun sha256ForApkSigners(apkFile: File): Set<String>? = try {
        val verification = ApkVerifier.Builder(apkFile).build().verify()
        if (!verification.isVerified || verification.signerCertificates.isEmpty()) null
        else verification.signerCertificates.mapTo(linkedSetOf(), ::sha256ForCert)
    } catch (_: Exception) {
        null
    }

    /** Parses signing certificate hash from `dumpsys package` output. */
    fun parseDeviceSignatureId(dumpsysOutput: String): String? =
        Regex("""signatures:\[([0-9a-fA-F]+)]""").find(dumpsysOutput)?.groupValues?.get(1)?.lowercase()

    private fun certificateForKeystore(file: File, password: String?, alias: String): Certificate? {
        if (!file.isFile) return null
        ensureBouncyCastle()
        val store = KeyStore.getInstance("BKS")
        file.inputStream().use { store.load(it, password?.toCharArray()) }
        val selectedAlias = store.aliases().toList().firstOrNull { it.equals(alias, ignoreCase = true) }
            ?: alias
        return store.getCertificate(selectedAlias)
    }

    private fun ensureBouncyCastle() {
        if (Security.getProvider("BC") != null) return
        try {
            val provider = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                .getDeclaredConstructor().newInstance() as Provider
            Security.addProvider(provider)
        } catch (_: Exception) {}
    }
}
