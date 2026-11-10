/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import app.morphe.engine.options.ExplicitOptionKind
import app.morphe.engine.options.PatchOption
import app.morphe.engine.options.PatchOptionType
import app.morphe.engine.options.SliderBounds
import app.morphe.gui.util.optionValueFromJson
import app.morphe.gui.util.optionValueToJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class PatchOptionKindTest {
    private fun option(
        type: PatchOptionType = PatchOptionType.STRING,
        key: String = "option",
        description: String = "",
        explicitKind: ExplicitOptionKind? = null,
        bounds: SliderBounds? = null,
        presets: Map<String, Any?>? = null,
        valueType: KType? = null,
    ) = PatchOption(
        key = key,
        title = key,
        description = description,
        type = type,
        explicitKind = explicitKind,
        sliderBounds = bounds,
        presets = presets,
        valueType = valueType,
    )

    @Test
    fun `projects every supported scalar and selection option kind`() {
        assertIs<OptionKind.BooleanToggle>(resolveOptionKind(option(PatchOptionType.BOOLEAN), false))
        assertIs<OptionKind.StringText>(resolveOptionKind(option(), "text"))
        assertIs<OptionKind.IntLong>(resolveOptionKind(option(PatchOptionType.INT), 1))
        assertIs<OptionKind.IntLong>(resolveOptionKind(option(PatchOptionType.LONG), 1L))
        assertIs<OptionKind.FloatDouble>(resolveOptionKind(option(PatchOptionType.FLOAT), 1.5f))
        assertIs<OptionKind.StringDropdown>(
            resolveOptionKind(option(presets = mapOf("One" to "one")), "one")
        )
        assertIs<OptionKind.ArrayDropdown>(
            resolveOptionKind(
                option(presets = mapOf("One" to arrayOf("one")), valueType = typeOf<Array<String>>()),
                arrayOf("one"),
            )
        )
    }

    @Test
    fun `projects color path file image and list editors`() {
        assertIs<OptionKind.Color>(resolveOptionKind(option(key = "accentColor"), "#ff00ff"))
        assertIs<OptionKind.Path>(resolveOptionKind(option(key = "assetsFolder"), "C:/assets"))
        assertIs<OptionKind.FilePath>(
            resolveOptionKind(option(PatchOptionType.FILE, description = "file path"), "C:/input.txt")
        )
        assertIs<OptionKind.Image>(resolveOptionKind(option(key = "customLogo"), "C:/logo.png"))
        assertIs<OptionKind.StringList>(resolveOptionKind(option(PatchOptionType.LIST), listOf("a", "b")))
        assertIs<OptionKind.FolderPicker>(
            resolveOptionKind(option(explicitKind = ExplicitOptionKind.Folder), "C:/folder")
        )
        assertIs<OptionKind.FilePicker>(
            resolveOptionKind(option(explicitKind = ExplicitOptionKind.FilePath), "C:/file.txt")
        )
    }

    @Test
    fun `projects slider and range kinds with their bounds`() {
        val bounds = SliderBounds(1f, 10f, 1f)

        assertEquals(OptionKind.IntSlider(bounds), resolveOptionKind(option(explicitKind = ExplicitOptionKind.IntSlider, bounds = bounds), 2))
        assertEquals(OptionKind.FloatSlider(bounds), resolveOptionKind(option(explicitKind = ExplicitOptionKind.FloatSlider, bounds = bounds), 2.5f))
        assertEquals(OptionKind.IntRangeSlider(bounds), resolveOptionKind(option(explicitKind = ExplicitOptionKind.IntRange, bounds = bounds), listOf(2, 8)))
        assertEquals(OptionKind.FloatRangeSlider(bounds), resolveOptionKind(option(explicitKind = ExplicitOptionKind.FloatRange, bounds = bounds), listOf(2.5f, 8.5f)))
        assertEquals(2f..8f, "8, 2".asFloatRange())
    }

    @Test
    fun `option values retain native json shape and restore editor text`() {
        assertEquals(JsonPrimitive(true), optionValueToJson(true))
        assertEquals(JsonPrimitive(42), optionValueToJson(42))
        val list = optionValueToJson(listOf("one", "two"))
        assertIs<JsonArray>(list)
        assertEquals("one, two", optionValueFromJson(list))
    }
}
