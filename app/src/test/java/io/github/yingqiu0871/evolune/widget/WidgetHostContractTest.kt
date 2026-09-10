package io.github.yingqiu0871.evolune.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetHostContractTest {
    @Test
    fun `four independent providers require the shared appearance surface`() {
        listOf(
            "evolune_widget_info.xml",
            "next_dose_widget_info.xml",
            "current_e2_widget_info.xml",
            "pk_chart_widget_info.xml"
        ).forEach { fileName ->
            val provider = File("src/main/res/xml/$fileName").readText()
            assertTrue(provider.contains("android:configure=\"@string/widget_configuration_activity\""))
            assertTrue(provider.contains("android:widgetFeatures=\"reconfigurable\""))
            assertFalse(provider.contains("configuration_optional"))
        }
    }

    @Test
    fun `configuration activity reference retains the source activity class for both variants`() {
        val production = File("src/main/res/values/widget_configuration.xml").readText()
        val debug = File("src/debug/res/values/widget_configuration.xml").readText()

        assertTrue(
            production.contains(
                "io.github.yingqiu0871.evolune.widget.WidgetConfigurationActivity"
            )
        )
        assertTrue(
            debug.contains(
                "io.github.yingqiu0871.evolune.widget.WidgetConfigurationActivity"
            )
        )
    }

    @Test
    fun `manifest exposes four functions while appearance screen has no function selector`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        listOf(
            "EvoluneWidgetReceiver",
            "NextDoseWidgetReceiver",
            "CurrentE2WidgetReceiver",
            "PkChartWidgetReceiver"
        ).forEach { provider -> assertTrue("missing $provider", manifest.contains(provider)) }
        val source = File(
            "src/main/java/io/github/yingqiu0871/evolune/widget/WidgetConfigurationActivity.kt"
        ).readText()
        assertFalse(source.contains("StyleSelector"))
        assertFalse(source.contains("widget_config_style"))
        assertTrue(source.contains("setResult(RESULT_CANCELED)"))
    }

    @Test
    fun `all Phone picker entries use Evolune-prefixed names`() {
        val strings = File("src/main/res/values/strings.xml").readText()
        listOf(
            "widget_today_plan_label",
            "widget_next_dose_label",
            "widget_current_e2_label",
            "widget_pk_chart_label"
        ).forEach { name ->
            val value = strings.substringAfter("<string name=\"$name\">").substringBefore("</string>")
            assertTrue("$name is not Evolune-prefixed", value.startsWith("Evolune-"))
        }
    }

    @Test
    fun `trend preview and runtime bars rise from the bottom x axis`() {
        val preview = File("src/main/res/layout/widget_preview_pk_chart.xml").readText()
        assertTrue(preview.contains("android:gravity=\"bottom\""))
        assertTrue(preview.contains("android:baselineAligned=\"false\""))
        assertTrue(preview.contains("android:layout_gravity=\"bottom\""))
        assertTrue(preview.contains("android:background=\"@drawable/widget_chart_bar\""))
        assertFalse(preview.contains("android:background=\"@drawable/widget_progress_segment\""))

        listOf(
            "widget_evolune.xml",
            "widget_evolune_compact.xml",
            "widget_evolune_wide.xml",
            "widget_evolune_expanded.xml"
        ).forEach { fileName ->
            val layout = File("src/main/res/layout/$fileName").readText()
            val chartContainer = layout.substringAfter("android:id=\"@+id/widget_chart_container\"")
                .substringBefore("/>")
            val xAxis = layout.substringAfter("android:id=\"@+id/widget_chart_x_axis\"")
                .substringBefore("/>")
            assertTrue("$fileName chart container is not bottom-aligned", chartContainer.contains("android:gravity=\"bottom\""))
            assertTrue("$fileName chart container still uses baseline alignment", chartContainer.contains("android:baselineAligned=\"false\""))
            assertTrue("$fileName x axis is not at the plot bottom", xAxis.contains("android:layout_gravity=\"bottom\""))
            assertFalse("$fileName x axis is offset from the y axis", xAxis.contains("android:layout_marginStart"))
        }

        val segment = File("src/main/res/layout/widget_chart_segment.xml").readText()
        assertTrue(segment.contains("android:layout_gravity=\"bottom\""))
        assertTrue(segment.contains("android:background=\"@drawable/widget_chart_bar\""))

        val barShape = File("src/main/res/drawable/widget_chart_bar.xml").readText()
        assertTrue(barShape.contains("android:topLeftRadius=\"6dp\""))
        assertTrue(barShape.contains("android:topRightRadius=\"6dp\""))
        assertTrue(barShape.contains("android:bottomLeftRadius=\"1dp\""))
        assertTrue(barShape.contains("android:bottomRightRadius=\"1dp\""))
    }

    @Test
    fun `record action uses the checkmark and has no production add icon reference`() {
        val receiver = File(
            "src/main/java/io/github/yingqiu0871/evolune/widget/EvoluneWidgetReceiver.kt"
        ).readText()
        val recordMapping = receiver.substringAfter("WidgetRowAction.RECORD ->")
            .substringBefore("WidgetRowAction.COMPLETED ->")
        assertTrue(recordMapping.contains("R.drawable.ic_widget_check"))
        assertFalse(recordMapping.contains("R.drawable.ic_widget_add"))
    }

    @Test
    fun `appearance hero preview centers both panels`() {
        val source = File(
            "src/main/java/io/github/yingqiu0871/evolune/widget/WidgetConfigurationActivity.kt"
        ).readText()
        val hero = source.substringAfter("private fun HeroConfigurationPreview(")
            .substringBefore("private fun ChartConfigurationPreview(")

        assertTrue(hero.contains("contentAlignment = Alignment.Center"))
        assertTrue(hero.contains("horizontalAlignment = Alignment.CenterHorizontally"))
        assertTrue(hero.contains("modifier = Modifier.fillMaxSize()"))
        assertTrue(hero.contains("modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)"))
        assertTrue(hero.contains("modifier = Modifier.fillMaxWidth()"))
        assertTrue(hero.contains("textAlign = TextAlign.Center"))
        assertTrue(hero.contains("PlatformTextStyle(includeFontPadding = false)"))
    }

    @Test
    fun `next dose preview gives the medicine and time centered balanced type`() {
        val source = File(
            "src/main/java/io/github/yingqiu0871/evolune/widget/WidgetConfigurationActivity.kt"
        ).readText()
        val nextDose = source.substringAfter("WidgetStyle.NEXT_DOSE -> HeroConfigurationPreview(")
            .substringBefore("WidgetStyle.CURRENT_E2")

        assertTrue(nextDose.contains("valueTextSp = 18"))
        assertTrue(nextDose.contains("metaTextSp = 16"))
    }

    @Test
    fun `chart axis uses three evenly weighted labels in every host surface`() {
        val layouts = listOf(
            "widget_evolune.xml",
            "widget_evolune_compact.xml",
            "widget_evolune_wide.xml",
            "widget_evolune_expanded.xml"
        )
        layouts.forEach { fileName ->
            val layout = File("src/main/res/layout/$fileName").readText()
            val axis = layout.substringAfter("android:id=\"@+id/widget_chart_axis\"")
                .substringBefore("</LinearLayout>")
            assertTrue("$fileName missing start label", axis.contains("@+id/widget_chart_axis_start"))
            assertTrue("$fileName missing center label", axis.contains("@+id/widget_chart_axis_now"))
            assertTrue("$fileName missing end label", axis.contains("@+id/widget_chart_axis_end"))
            assertTrue("$fileName axis labels are not equally weighted", axis.split("android:layout_weight=\"1\"").size - 1 == 3)
            assertTrue(axis.contains("android:gravity=\"start\""))
            assertTrue(axis.contains("android:gravity=\"center\""))
            assertTrue(axis.contains("android:gravity=\"end\""))
        }

        val preview = File("src/main/res/layout/widget_preview_pk_chart.xml").readText()
        assertTrue(preview.contains("@string/widget_pk_chart_axis_start"))
        assertTrue(preview.contains("@string/widget_pk_chart_axis_now"))
        assertTrue(preview.contains("@string/widget_pk_chart_axis_end"))
    }

    @Test
    fun `today plan keeps the v15 row structure and a scrollable collection`() {
        val row = File("src/main/res/layout/widget_medication_row.xml").readText()
        assertFalse(row.contains("widget_row_background"))
        assertTrue(row.contains("android:id=\"@+id/widget_row_rail\""))

        listOf(
            "widget_evolune.xml",
            "widget_evolune_compact.xml",
            "widget_evolune_wide.xml",
            "widget_evolune_expanded.xml"
        ).forEach { fileName ->
            val layout = File("src/main/res/layout/$fileName").readText()
            assertTrue("$fileName lost the ListView", layout.contains("<ListView"))
            assertTrue("$fileName lost the rows container", layout.contains("@+id/widget_rows_container"))
        }

        val source = File(
            "src/main/java/io/github/yingqiu0871/evolune/widget/WidgetUi.kt"
        ).readText()
        val styleRows = source.substringAfter("private fun styleRows(")
            .substringBefore("private fun List<MedicationTimelineItem>.actionableItems")
        assertFalse(styleRows.contains("take(layout.rowCapacity)"))
    }
}
