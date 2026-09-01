package io.github.yingqiu0871.evolune.wear

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearManifestContractTest {
    @Test
    fun finalLauncherManifestUsesValidIsolatedTaskAffinity() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = targetContext.packageManager
        val packageName = targetContext.packageName
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        assertNotNull("Wear launcher activity must exist", launchIntent)
        val launcherComponent = requireNotNull(launchIntent).component
        assertNotNull("Wear launcher component must exist", launcherComponent)
        assertEquals(
            "io.github.yingqiu0871.evolune.wear.WearAppActivity",
            requireNotNull(launcherComponent).className
        )

        val activityInfo = packageManager.getActivityInfo(
            ComponentName(packageName, requireNotNull(launcherComponent).className),
            0
        )
        val affinity = activityInfo.taskAffinity
        assertNotNull("Wear launcher taskAffinity must be declared", affinity)
        assertFalse("Wear launcher taskAffinity must not contain a hyphen", affinity.contains('-'))
        assertFalse("Old invalid taskAffinity must not be present", affinity.contains("wear-app"))
        assertTrue(
            "Wear launcher taskAffinity must be a Java/package-style identifier",
            JAVA_PACKAGE_IDENTIFIER.matches(affinity)
        )
        assertEquals("$packageName.wearapp", affinity)
    }

    private companion object {
        val JAVA_PACKAGE_IDENTIFIER =
            Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
    }
}
