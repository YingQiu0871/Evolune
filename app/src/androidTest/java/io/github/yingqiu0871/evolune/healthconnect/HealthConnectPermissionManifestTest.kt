package io.github.yingqiu0871.evolune.healthconnect

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthConnectPermissionManifestTest {

    @Test
    fun declaresHealthConnectPermissionRationaleEntryPoints() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager

        val rationaleIntent = Intent("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE")
        val usageIntent = Intent("android.intent.action.VIEW_PERMISSION_USAGE")
            .addCategory("android.intent.category.HEALTH_PERMISSIONS")

        assertTrue(
            packageManager.queryIntentActivities(rationaleIntent, 0).any { resolveInfo ->
                resolveInfo.activityInfo.packageName == context.packageName
            }
        )
        assertTrue(
            packageManager.queryIntentActivities(usageIntent, 0).any { resolveInfo ->
                resolveInfo.activityInfo.packageName == context.packageName
            }
        )
    }
}
