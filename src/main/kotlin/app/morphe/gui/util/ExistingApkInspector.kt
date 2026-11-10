/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.util

import app.morphe.engine.apk.ApkInspectionResult
import app.morphe.engine.apk.ApkInspector
import java.io.File

data class ExistingApkInfo(
    val path: String,
    val fileName: String,
    val fileSize: Long,
    val packageName: String,
    val versionName: String?,
    val versionCode: Int?,
    val appLabel: String?,
)

/**
 * Strictly read-only inspection before a single-file, direct APK install.
 * A split bundle is deliberately NOT treated as an ordinary single APK.
 */
object ExistingApkInspector {
    fun inspect(
        file: File,
        reader: (File) -> ApkInspectionResult? = ApkInspector::inspect,
    ): Result<ExistingApkInfo> = runCatching {
        require(file.isFile) { "The selected APK no longer exists." }
        require(file.extension.equals("apk", ignoreCase = true)) {
            "Direct installation accepts exactly one .apk file; multi-APK bundles require a separate flow."
        }
        val info = reader(file)
            ?: error("The selected file is not a readable Android APK.")
        require(!info.isBundle) { "A split APK bundle cannot be installed as a single APK." }
        require(info.packageName.isNotBlank()) { "The APK has no package identity." }
        ExistingApkInfo(
            path = file.canonicalPath,
            fileName = file.name,
            fileSize = file.length(),
            packageName = info.packageName,
            versionName = info.versionName,
            versionCode = info.versionCode,
            appLabel = info.applicationLabel,
        )
    }
}
