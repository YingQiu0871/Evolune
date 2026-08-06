package io.github.yuninggu.evolune.application

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ReplayPolicyBoundaryTest {
    @Test
    fun `typed recorders own policy selection and Wear production remains disconnected`() {
        val applicationRoot = Path.of("src/main/java/io/github/yuninggu/evolune/application")
        val localRecorder = Files.readString(applicationRoot.resolve("LocalActionRecorder.kt"))
        val wearRecorder = Files.readString(applicationRoot.resolve("WearActionRecorder.kt"))
        val engine = Files.readString(applicationRoot.resolve("RecordDoseEventAction.kt"))
        val reminder = Files.readString(
            Path.of("src/main/java/io/github/yuninggu/evolune/reminder/ReminderReceiverWork.kt")
        )
        val widget = Files.readString(
            Path.of("src/main/java/io/github/yuninggu/evolune/widget/WidgetWork.kt")
        )
        val wearProduction = Files.readString(
            Path.of("src/main/java/io/github/yuninggu/evolune/wear/WearDataLayer.kt")
        )

        assertTrue(localRecorder.contains("fun recordReminder("))
        assertTrue(localRecorder.contains("fun recordWidget("))
        assertTrue(localRecorder.contains("UUID.nameUUIDFromBytes"))
        assertFalse(localRecorder.contains("DoseEventSource.WEAR"))
        assertTrue(wearRecorder.contains("FirstAcceptedBySourceAndOccurredAt"))
        assertFalse(wearRecorder.contains("ExistingEventPolicy.FirstAcceptedBySource("))
        assertTrue(engine.contains("policy: ExistingEventPolicy,"))
        assertFalse(engine.contains("policy: ExistingEventPolicy ="))
        assertTrue(reminder.contains("LocalActionRecorder"))
        assertTrue(widget.contains("LocalActionRecorder"))
        assertFalse(reminder.contains("ExistingEventPolicy"))
        assertFalse(widget.contains("ExistingEventPolicy"))
        assertFalse(wearProduction.contains("LocalActionRecorder"))
        assertFalse(wearProduction.contains("WearActionRecorder"))
        assertFalse(wearProduction.contains("ExistingEventPolicy"))
    }
}
