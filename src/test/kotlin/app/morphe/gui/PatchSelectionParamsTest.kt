/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatchSelectionParamsTest {
    @Test
    fun `compose navigation payload preserves repatch source and device import state`() {
        val params = PatchSelectionParams(
            apkPath = "C:/imports/base.apk",
            apkName = "Example",
            patchesFilePath = "C:/patches/stable.mpp",
            packageName = "example.app",
            apkArchitectures = listOf("arm64-v8a"),
            patchesFilePaths = listOf("C:/patches/stable.mpp", "C:/patches/dev.mpp"),
            patchSourceNames = listOf("Stable", "Development"),
            patchSourceIds = listOf("stable-id", "dev-id"),
            patchSourceHashes = listOf("stable-sha", "dev-sha"),
            initialSelectionByBundle = mapOf("Stable" to setOf("patch-id")),
            initialPatchOptions = mapOf("Patch.option" to "saved-value"),
            apkVersion = "1.2.3",
            temporaryInputRoot = "C:/imports/session",
            sourceDeviceSerial = "device-serial",
            deviceSpecificInput = true,
            validatedDeviceImport = true,
        )

        assertEquals(listOf("stable-id", "dev-id"), params.patchSourceIds)
        assertEquals(listOf("stable-sha", "dev-sha"), params.patchSourceHashes)
        assertEquals(setOf("patch-id"), params.initialSelectionByBundle.getValue("Stable"))
        assertEquals("saved-value", params.initialPatchOptions.getValue("Patch.option"))
        assertEquals("C:/imports/session", params.temporaryInputRoot)
        assertEquals("device-serial", params.sourceDeviceSerial)
        assertTrue(params.deviceSpecificInput)
        assertTrue(params.validatedDeviceImport)
    }
}
