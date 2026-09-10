package io.github.yingqiu0871.evolune.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class WearAppearancePolicyTest {
    @Test
    fun `wear exposes system Monet and the same eight phone presets`() {
        assertEquals(
            listOf("系统颜色", "蓝", "紫罗兰", "樱花", "薄荷", "青绿", "琥珀", "中性", "薰衣草"),
            WearColorScheme.entries.map { it.displayName }
        )
    }

    @Test
    fun `passive Tiles use a battery-conscious fifteen minute fallback`() {
        assertEquals(15 * 60 * 1000L, WEAR_TILE_FRESHNESS_MILLIS)
    }

    @Test
    fun `wear app and Tiles share one moderate card radius`() {
        assertEquals(24f, WEAR_CARD_CORNER_RADIUS_DP, 0f)
        assertEquals(WEAR_CARD_CORNER_RADIUS_DP, WEAR_GALLERY_PANEL_CORNER_RADIUS_DP, 0f)
    }
}
