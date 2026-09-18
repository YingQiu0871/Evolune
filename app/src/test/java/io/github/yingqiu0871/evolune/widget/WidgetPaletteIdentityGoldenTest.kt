package io.github.yingqiu0871.evolune.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.7.2 Slice A — characterization golden for the Widget palette PERSISTED IDENTITY.
 *
 * These expectations are an independent transcription of the released v1.7.1 behavior; they
 * must pass unchanged before AND after the shared-palette extraction. Ordinal order is pinned
 * because the widget configuration selector iterates `entries`, so ordering is user-visible.
 */
class WidgetPaletteIdentityGoldenTest {

    @Test
    fun `widget palette enum names and order are frozen`() {
        assertEquals(
            listOf(
                "MATERIAL_YOU_AUTO",
                "MONET_BLUE",
                "MONET_VIOLET",
                "MONET_SAKURA",
                "MONET_MINT",
                "MONET_TEAL",
                "MONET_AMBER",
                "MONET_NEUTRAL",
                "MONET_LAVENDER"
            ),
            WidgetColorScheme.entries.map { it.name }
        )
    }

    @Test
    fun `widget appearance shared preferences identity is frozen`() {
        assertEquals("widget_appearance", WidgetAppearanceKeys.PREFERENCES_NAME)
        assertEquals("widget_42_theme", WidgetAppearanceKeys.theme(42))
        assertEquals("widget_42_color", WidgetAppearanceKeys.color(42))
        assertEquals("widget_42_opacity", WidgetAppearanceKeys.opacity(42))
        assertEquals("widget_42_style", WidgetAppearanceKeys.style(42))
        assertEquals("widget_7_color", WidgetAppearanceKeys.color(7))
    }

    @Test
    fun `unknown or missing persisted palette id falls back to material you auto`() {
        assertEquals(WidgetColorScheme.MATERIAL_YOU_AUTO, WidgetColorScheme.fromStored(null))
        assertEquals(WidgetColorScheme.MATERIAL_YOU_AUTO, WidgetColorScheme.fromStored(""))
        assertEquals(WidgetColorScheme.MATERIAL_YOU_AUTO, WidgetColorScheme.fromStored("NOT_A_PALETTE"))
        assertEquals(WidgetColorScheme.MATERIAL_YOU_AUTO, WidgetColorScheme.fromStored("monet_blue"))
    }

    @Test
    fun `stored palette ids round-trip by enum name`() {
        WidgetColorScheme.entries.forEach { scheme ->
            assertEquals(scheme, WidgetColorScheme.fromStored(scheme.name))
        }
    }

    @Test
    fun `widget style ids are frozen`() {
        assertEquals(
            listOf("legacy_default", "today_plan", "next_dose", "current_e2", "pk_chart"),
            WidgetStyle.entries.map { it.id }
        )
        assertEquals(WidgetStyle.NEXT_DOSE, WidgetStyle.fromId("next_dose"))
        assertEquals(WidgetStyle.LEGACY_DEFAULT, WidgetStyle.fromId(null))
        assertEquals(WidgetStyle.LEGACY_DEFAULT, WidgetStyle.fromId("unknown"))
    }
}
