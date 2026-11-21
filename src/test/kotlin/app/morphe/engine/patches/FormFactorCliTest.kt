package app.morphe.engine.patches

import app.morphe.engine.model.CompatiblePackage
import app.morphe.engine.model.PatchMetadata
import app.morphe.patcher.patch.FormFactor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormFactorCliTest {
    @Test fun `CLI uses explicit target metadata and never guesses mobile or unknown`() {
        assertEquals("TV", FormFactor.TV.displayLabel())
        assertEquals("Wear OS", FormFactor.WEARABLE.displayLabel())
        assertEquals("Automotive", FormFactor.AUTOMOTIVE.displayLabel())
        assertNull(FormFactor.MOBILE.displayLabel())
        assertNull((null as FormFactor?).displayLabel())
    }

    @Test fun `catalog carries the declared form factor into live supported app records`() {
        val app = SupportedAppCatalog.extractSupportedAppsFromMetadata(
            listOf(
                PatchMetadata(
                    name = "TV patch",
                    compatiblePackages = listOf(
                        CompatiblePackage(
                            name = "com.example.tv",
                            versions = listOf("1.0"),
                            formFactor = FormFactor.TV,
                        ),
                    ),
                ),
            ),
        ).single()
        assertEquals(FormFactor.TV, app.formFactor)
    }

    @Test fun `absence of form factor metadata cannot synthesize device platform`() {
        val app = SupportedAppCatalog.extractSupportedAppsFromMetadata(
            listOf(
                PatchMetadata(
                    name = "Universal app patch",
                    compatiblePackages = listOf(
                        CompatiblePackage(name = "com.example.generic", versions = listOf("1.0")),
                    ),
                ),
            ),
        ).single()
        assertNull(app.formFactor)
    }
}
