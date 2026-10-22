/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.engine.apk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BundleBaseApkSelectionTest {
    @Test
    fun `apkm with base and splits selects the explicit base`() {
        assertEquals("base.apk", BundleBaseApkSelection.select(
            listOf("manifest.json", "base.apk", "split_config.arm64_v8a.apk", "split_config.xxhdpi.apk")
        ))
    }

    @Test
    fun `apks base-master in splits directory is recognized`() {
        assertEquals("splits/base-master.apk", BundleBaseApkSelection.select(
            listOf("toc.pb", "splits/base-master.apk", "splits/base-en.apk")
        ))
    }

    @Test
    fun `xapk one named base and config split is unambiguous`() {
        assertEquals("com.example.app.apk", BundleBaseApkSelection.select(
            listOf("manifest.json", "com.example.app.apk", "config.arm64.apk")
        ))
    }

    @Test
    fun `several plausible app APKs must never be guessed from size or position`() {
        assertNull(BundleBaseApkSelection.select(listOf("first.apk", "second.apk", "split_config.en.apk")))
        assertNull(BundleBaseApkSelection.select(listOf("split_config.en.apk", "config.arm64.apk")))
        assertNull(BundleBaseApkSelection.select(listOf("base.apk", "splits/base-master.apk")))
    }

    @Test
    fun `unsafe entry names and duplicate APK entries reject entire bundle`() {
        assertNull(BundleBaseApkSelection.select(listOf("base.apk", "../evil.apk")))
        assertNull(BundleBaseApkSelection.select(listOf("base.apk", "/outside.apk")))
        assertNull(BundleBaseApkSelection.select(listOf("base.apk", "C:/temp/evil.apk")))
        assertNull(BundleBaseApkSelection.select(listOf("base.apk", "a\\evil.apk")))
        assertNull(BundleBaseApkSelection.select(listOf("base.apk", "base.apk")))
        assertNull(BundleBaseApkSelection.select(listOf("base.apk", "other//evil.apk")))
    }

    @Test
    fun `oversized entry listing fails closed`() {
        assertNull(BundleBaseApkSelection.select(List(2049) { "config.$it.apk" }))
    }
}
