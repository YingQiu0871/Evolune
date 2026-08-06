package io.github.yuninggu.evolune.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class Batch6ReceiverStaticBoundaryTest {
    @Test
    fun `receiver and Widget production paths contain no forbidden lifecycle or persistence access`() {
        val files = listOf(
            "reminder/MedicationReminderReceiver.kt",
            "reminder/MedicationNotificationActionReceiver.kt",
            "reminder/ReminderRescheduleReceiver.kt",
            "reminder/ReminderReceiverWork.kt",
            "widget/EvoluneWidgetReceiver.kt",
            "widget/WidgetWork.kt"
        ).map { relative ->
            Path.of("src/main/java/io/github/yuninggu/evolune/$relative")
        }
        val forbidden = listOf(
            "GlobalScope",
            "runBlocking",
            "WIDGET_SCOPE",
            "doseEventDao(",
            "medicationPlanDao(",
            "DoseEventEntity",
            "MedicationPlanEntity",
            "data.DoseEventRepository",
            "data.MedicationPlanRepository"
        )

        files.forEach { file ->
            val source = Files.readString(file)
            forbidden.forEach { token ->
                assertFalse("$file contains $token", source.contains(token))
            }
        }
        val launcher = Files.readString(
            Path.of("src/main/java/io/github/yuninggu/evolune/reminder/ReceiverWorkLauncher.kt")
        )
        assertEqualsOne("finish()", launcher)
        assertTrue(launcher.contains("SupervisorJob()"))
        assertTrue(launcher.contains("finally"))
    }

    private fun assertEqualsOne(token: String, source: String) {
        assertTrue(source.indexOf(token) >= 0)
        assertTrue(source.indexOf(token) == source.lastIndexOf(token))
    }
}
