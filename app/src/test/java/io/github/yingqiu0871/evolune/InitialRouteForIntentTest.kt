package io.github.yingqiu0871.evolune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InitialRouteForIntentTest {
    @Test
    fun healthConnectRationaleActionsOpenDisclosures() {
        assertEquals(
            "disclosures",
            initialRouteForIntent("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE")
        )
        assertEquals(
            "disclosures",
            initialRouteForIntent("android.intent.action.VIEW_PERMISSION_USAGE")
        )
    }

    @Test
    fun unrelatedActionsKeepNormalHomeStart() {
        assertNull(initialRouteForIntent(null))
        assertNull(initialRouteForIntent("android.intent.action.MAIN"))
    }
}
