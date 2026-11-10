/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.constants

import java.util.Properties

/** Application-level constants and version metadata. */
object AppConstants {

    private val pkg by lazy { AppConstants::class.java.`package` }

    val APP_VERSION: String by lazy {
        val resourceVersion = AppConstants::class.java
            .getResourceAsStream("/app/morphe/cli/version.properties")
            ?.use { stream ->
                Properties().apply { load(stream) }.getProperty("version")
            }

        val resolvedVersion = resourceVersion ?: pkg?.implementationVersion
        resolvedVersion?.let { "v$it" } ?: "dev"
    }

    /** Historic private Quick-mode web search API; retained until URL-service parity is validated. */
    const val MORPHE_API_URL = "https://api.morphe.software"
    val FALLBACK_PACKAGES = listOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.reddit.frontpage",
    )

    const val WEBSITE_URL = "https://morphe.software"
    /** True if the running version is a prerelease or unpackaged development build. */
    val IS_DEV_BUILD: Boolean by lazy { APP_VERSION == "dev" || APP_VERSION.contains('-') }

    val DOCUMENTATION_URL: String by lazy {
        val branch = if (IS_DEV_BUILD) "dev" else "main"
        "https://github.com/MorpheApp/morphe-desktop/blob/$branch/docs/documentation.md"
    }
    // TODO: Re-enable checksum verification when checksums are added to .mpp files.
}
