package io.github.yingqiu0871.evolune.wear

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppUpcomingOccurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class WearOccurrenceDialogEmulatorTest {
    @Test
    fun actionButtonsShareOneVisibleRoundSafeRow() {
        assumeTrue(
            Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.contains("emulator") ||
                Build.PRODUCT.contains("sdk") ||
                Build.HARDWARE.contains("ranchu")
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val snapshot = requireNotNull(WearAppStore.getSnapshot(context))
        val occurrence = snapshot.upcomingOccurrences.first()
        val activity = instrumentation.startActivitySync(
            Intent(context, WearAppActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as WearAppActivity
        lateinit var dialog: Dialog
        instrumentation.runOnMainSync {
            val method = WearAppActivity::class.java.getDeclaredMethod(
                "showOccurrenceActionDialog",
                WearAppSnapshot::class.java,
                WearAppUpcomingOccurrence::class.java,
                ZoneId::class.java,
                Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }
            dialog = method.invoke(
                activity,
                snapshot,
                occurrence,
                ZoneId.of(snapshot.zoneId),
                true
            ) as Dialog
        }
        instrumentation.waitForIdleSync()

        val skip = requireNotNull(dialog.findViewById<TextView>(R.id.wear_action_dialog_skip))
        val confirm = requireNotNull(dialog.findViewById<TextView>(R.id.wear_action_dialog_confirm))
        val root = requireNotNull(dialog.findViewById<android.view.View>(R.id.wear_action_dialog_root))
        assertTrue(skip.isShown)
        assertTrue(confirm.isShown)
        assertTrue(skip.left < confirm.left)
        assertEquals(skip.top, confirm.top)
        assertEquals(skip.bottom, confirm.bottom)
        assertTrue(root.width < context.resources.displayMetrics.widthPixels)
        assertTrue(root.height <= context.resources.displayMetrics.heightPixels)

        val output = File(requireNotNull(context.getExternalFilesDir(null)), "v16_dialog_horizontal.png")
        FileOutputStream(output).use { stream ->
            requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        instrumentation.runOnMainSync {
            dialog.dismiss()
            activity.finish()
        }
    }
}
