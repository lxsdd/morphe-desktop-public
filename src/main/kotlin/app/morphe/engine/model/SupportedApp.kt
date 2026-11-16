/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import app.morphe.engine.util.DownloadUrlResolver
import app.morphe.engine.util.compareVersions
import app.morphe.patcher.patch.FormFactor
import app.morphe.patcher.patch.SupportedAbi

/** App target aggregated from patch bundle compatibility metadata. */
data class SupportedApp(
    val packageName: String,
    val displayName: String,
    val supportedVersions: List<String>,
    val experimentalVersions: List<String> = emptyList(),
    val recommendedVersion: String?,
    val apkDownloadUrl: String? = null,
    val experimentalDownloadUrl: String? = null,
    val appIconColor: String? = null,
    val versionBuildCodes: Map<String, Set<Int>> = emptyMap(),
    val versionAbiBuildCodes: Map<String, Map<SupportedAbi, Int>> = emptyMap(),
    val formFactor: FormFactor? = null,
) {
    /** Select the supported ABI build and form factor for this version only, never promote experimental to stable. */
    fun downloadUrl(version: String?): String? {
        val codes = version?.let { versionAbiBuildCodes[it.removePrefix("v")] ?: versionAbiBuildCodes[it] }
        // A unique build restriction is useful even when the ABI mapping is absent.
        // Multiple unqualified codes are ambiguous: never choose one arbitrarily.
        val buildCode = codes?.let { it[SupportedAbi.ARM64_V8A] ?: it.values.firstOrNull() }
            ?: version?.let { requiredBuildCodes(it).singleOrNull() }
        return getDownloadUrl(packageName, version, buildCode, formFactor)
    }

    fun buildCodeSupported(version: String, versionCode: Int?): Boolean {
        val codes = versionBuildCodes[version.removePrefix("v")]
            ?: versionBuildCodes[version]
            ?: return true
        return codes.isEmpty() || versionCode == null || versionCode in codes
    }

    fun requiredBuildCodes(version: String): Set<Int> {
        return versionBuildCodes[version.removePrefix("v")]
            ?: versionBuildCodes[version]
            ?: emptySet()
    }

    companion object {
        fun resolveDisplayName(packageName: String, providedName: String?): String {
            return providedName?.takeIf { it.isNotBlank() } ?: getDisplayName(packageName)
        }

        private val knownNames = mapOf(
            "com.google.android.youtube" to "YouTube",
            "com.google.android.apps.youtube.music" to "YouTube Music",
            "com.reddit.frontpage" to "Reddit",
        )

        /** Explicitly known product names only; unlike [getDisplayName], never guesses from package syntax. */
        fun getKnownDisplayName(packageName: String): String? = knownNames[packageName]

        /** Formats a package name into a readable display name when no override exists. */
        fun getDisplayName(packageName: String): String {
            getKnownDisplayName(packageName)?.let { return it }

            // Smart fallback: use the most meaningful part of the package name
            val parts = packageName.split(".")
            // Skip common prefixes: com, org, net, android, app, etc.
            val skipParts = setOf("com", "org", "net", "io", "me", "app", "android", "apps", "free")
            val meaningful = parts.filter { it.lowercase() !in skipParts && it.length > 1 }
            // Use the last meaningful part, or the full last segment
            val name = meaningful.lastOrNull() ?: parts.last()
            // Split camelCase and underscores, capitalize
            return name
                .replace("_", " ")
                .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }

        fun getDownloadUrl(
            packageName: String,
            version: String?,
            buildCode: Int? = null,
            formFactor: FormFactor? = null,
        ): String? {
            if (version == null) return null
            return DownloadUrlResolver.getWebSearchDownloadLink(
                packageName = packageName,
                version = version,
                buildCode = buildCode,
                formFactor = formFactor,
            )
        }

        fun getRecommendedVersion(versions: List<String>): String? {
            if (versions.isEmpty()) return null

            return versions.sortedWith { v1, v2 ->
                compareVersions(v2, v1) // Descending order
            }.firstOrNull()
        }
    }
}
