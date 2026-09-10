package io.github.yingqiu0871.evolune.wear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WearSurfaceRegistrationContractTest {
    @Test
    fun `all production Tiles keep identity and provide branded previews`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val expectedPreviews = mapOf(
            "DoseTileService" to "@drawable/tile_preview",
            "NextDoseTileService" to "@drawable/tile_preview_next_dose",
            "TodayPlanTileService" to "@drawable/tile_preview_today_plan",
            "CurrentE2TileService" to "@drawable/tile_preview_current_e2"
        )
        expectedPreviews.forEach { (service, expectedPreview) ->
                val block = manifest.substringAfter("android:name=\".$service\"")
                    .substringBefore("</service>")
                assertTrue("$service is not exported", block.contains("android:exported=\"true\""))
                assertTrue("$service lost Tile binding", block.contains("BIND_TILE_PROVIDER"))
                assertTrue("$service lost icon", block.contains("android:icon=\"@mipmap/ic_launcher\""))
                assertTrue("$service has no preview", block.contains("androidx.wear.tiles.PREVIEW"))
                assertTrue("$service preview is incorrect", block.contains(expectedPreview))
            }
        val preview = File("src/main/res/drawable/tile_preview.xml").readText()
        assertFalse(
            "legacy curve preview restored the removed chart backing plate",
            preview.contains("#18201B")
        )
        assertTrue(
            "legacy curve preview lost its target-range tint",
            preview.contains("#409DD6B3")
        )
        assertTrue(
            "legacy curve preview lost its concentration curve",
            preview.contains("strokeColor=\"#9DD6B3\"")
        )
        assertFalse("legacy plus asset remains in the shared Tile preview", preview.contains("ic_widget_add"))
        listOf("next_dose", "today_plan", "current_e2").forEach { suffix ->
            assertTrue(
                "missing function-specific preview: $suffix",
                File("src/main/res/drawable-nodpi/tile_preview_$suffix.png").isFile
            )
        }
    }

    @Test
    fun `all production Complications advertise short text and branded icon`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        listOf(
            "NextDoseComplicationDataSource",
            "TodayProgressComplicationDataSource",
            "CurrentE2ComplicationDataSource"
        ).forEach { service ->
            val block = manifest.substringAfter("android:name=\".$service\"")
                .substringBefore("</service>")
            assertTrue("$service is not exported", block.contains("android:exported=\"true\""))
            assertTrue("$service lost provider permission", block.contains("BIND_COMPLICATION_PROVIDER"))
            assertTrue("$service lost SHORT_TEXT", block.contains("android:value=\"SHORT_TEXT\""))
            assertTrue("$service lost icon", block.contains("android:icon=\"@mipmap/ic_launcher\""))
            assertTrue("$service polls more often than the 15 minute fallback", block.contains("android:value=\"900\""))
        }
    }

    @Test
    fun `all discoverable Wear surfaces use Evolune-prefixed names`() {
        val strings = File("src/main/res/values/strings.xml").readText()
        listOf(
            "tile_label",
            "tile_next_dose_label",
            "tile_today_pending_label",
            "tile_current_e2_label",
            "complication_next_dose_label",
            "complication_today_progress_label",
            "complication_current_e2_label"
        ).forEach { name ->
            val value = strings.substringAfter("<string name=\"$name\">").substringBefore("</string>")
            assertTrue("$name is not Evolune-prefixed", value.startsWith("Evolune-"))
        }
    }

    @Test
    fun `occurrence actions stay side by side inside the round-safe dialog`() {
        val layout = File("src/main/res/layout/dialog_wear_occurrence_action.xml").readText()
        assertTrue(layout.contains("android:layout_width=\"168dp\""))
        val actions = layout.substringAfter("android:id=\"@+id/wear_action_dialog_actions\"")
        assertTrue(actions.substringBefore("</LinearLayout>").contains("android:orientation=\"horizontal\""))
        assertTrue(actions.indexOf("wear_action_dialog_skip") < actions.indexOf("wear_action_dialog_confirm"))
    }
}
