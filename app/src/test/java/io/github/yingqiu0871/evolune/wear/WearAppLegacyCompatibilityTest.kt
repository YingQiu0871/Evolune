package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WearAppLegacyCompatibilityTest {
    @Test
    fun `legacy and v1 paths remain additive at the Phone boundary`() {
        assertEquals("/hrt/plans", WearDataLayer.PLANS_PATH)
        assertEquals("/hrt/request-plans", WearDataLayer.REQUEST_PLANS_PATH)
        assertEquals("/hrt/dose-actions", WearDataLayer.DOSE_ACTIONS_PATH_PREFIX)
        assertEquals("/hrt/v1/wear-app/snapshot", WearAppProtocol.SNAPSHOT_PATH)
        assertEquals("/hrt/v1/wear-app/request", WearAppProtocol.REQUEST_PATH)
        assertFalse(WearAppProtocol.SNAPSHOT_PATH.startsWith(WearDataLayer.DOSE_ACTIONS_PATH_PREFIX))
        assertFalse(WearAppProtocol.REQUEST_PATH.startsWith(WearDataLayer.DOSE_ACTIONS_PATH_PREFIX))
    }
}
