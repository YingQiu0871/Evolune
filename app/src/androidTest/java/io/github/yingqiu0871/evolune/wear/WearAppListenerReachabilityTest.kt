package io.github.yingqiu0871.evolune.wear

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reachability contract for the Phone-side Wear Data Layer listener.
 *
 * The Wear App sends DataItems on dedicated paths; the Phone only receives them
 * when `.wear.WearAppListenerService` declares a matching intent filter. A
 * missing filter makes an existing handler silently unreachable, so these tests
 * assert reachability through the package manager (merged manifest) instead of
 * grepping manifest text.
 */
@RunWith(AndroidJUnit4::class)
class WearAppListenerReachabilityTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun skipNotificationDataChangedPathResolvesToTheWearAppListener() {
        val resolved = dataChangedServicesFor(WearAppProtocol.SKIP_NOTIFICATION_PATH)

        assertTrue(
            "No DATA_CHANGED registration resolves ${WearAppProtocol.SKIP_NOTIFICATION_PATH}; " +
                "the Wear skip request cannot reach processWearAppSkipNotification. " +
                "Resolved: $resolved",
            resolved.contains(LISTENER_CLASS_NAME)
        )
    }

    @Test
    fun snapshotRequestMessagePathStillResolvesToTheWearAppListener() {
        val intent = Intent(ACTION_MESSAGE_RECEIVED)
            .setData(Uri.parse("wear://test-node${WearAppProtocol.REQUEST_PATH}"))
            .setPackage(context.packageName)

        val resolved = context.packageManager.queryIntentServices(intent, 0)
            .mapNotNull { it.serviceInfo?.name }

        assertTrue(
            "MESSAGE_RECEIVED ${WearAppProtocol.REQUEST_PATH} must keep resolving to the Wear App listener; resolved: $resolved",
            resolved.contains(LISTENER_CLASS_NAME)
        )
    }

    @Test
    fun wearAppCommandPrefixStillResolvesToTheWearAppListener() {
        val commandPath = "/hrt/v1/wear-app/commands/00000000-0000-0000-0000-000000000001"
        val resolved = dataChangedServicesFor(commandPath)

        assertTrue(
            "DATA_CHANGED $commandPath must keep resolving to the Wear App listener; resolved: $resolved",
            resolved.contains(LISTENER_CLASS_NAME)
        )
    }

    @Test
    fun unrelatedWearAppPathsDoNotResolveToTheWearAppListener() {
        val unrelated = listOf(
            WearAppProtocol.SNAPSHOT_PATH,
            "/hrt/v1/wear-app/skip-notification-extra",
            "/hrt/dose-actions/00000000-0000-0000-0000-000000000002"
        )

        unrelated.forEach { path ->
            val resolved = dataChangedServicesFor(path)
            assertEquals(
                "DATA_CHANGED $path must not be captured by the Wear App listener filters; resolved: $resolved",
                emptyList<String>(),
                resolved.filter { it == LISTENER_CLASS_NAME }
            )
        }
    }

    private fun dataChangedServicesFor(path: String): List<String> {
        val intent = Intent(ACTION_DATA_CHANGED)
            .setData(Uri.parse("wear://test-node$path"))
            .setPackage(context.packageName)

        return context.packageManager.queryIntentServices(intent, 0)
            .mapNotNull { it.serviceInfo?.name }
    }

    private companion object {
        const val ACTION_DATA_CHANGED = "com.google.android.gms.wearable.DATA_CHANGED"
        const val ACTION_MESSAGE_RECEIVED = "com.google.android.gms.wearable.MESSAGE_RECEIVED"
        val LISTENER_CLASS_NAME: String = WearAppListenerService::class.java.name
    }
}
