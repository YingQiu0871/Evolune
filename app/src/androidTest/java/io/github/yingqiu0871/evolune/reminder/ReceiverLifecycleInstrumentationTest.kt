package io.github.yingqiu0871.evolune.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.widget.EvoluneWidgetReceiver
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionOutcome
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionWork
import io.github.yingqiu0871.evolune.widget.WidgetUpdateWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class ReceiverLifecycleInstrumentationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun notificationReceiverFinishesEveryTypedOutcomeExactlyOnce() {
        listOf(
            NotificationActionOutcome.Accepted(false),
            NotificationActionOutcome.Accepted(true),
            NotificationActionOutcome.AcceptedWithSideEffectFailure,
            NotificationActionOutcome.StalePlan,
            NotificationActionOutcome.StalePlanCleanupFailure,
            NotificationActionOutcome.Conflict,
            NotificationActionOutcome.Invalid,
            NotificationActionOutcome.StorageFailure,
            NotificationActionOutcome.UnexpectedFailure
        ).forEach { outcome ->
            val finished = CountDownLatch(1)
            val finishCalls = AtomicInteger()
            val receiver = MedicationNotificationActionReceiver(
                workFactory = {
                    NotificationActionWork { outcome }
                },
                workLauncher = launcher(finished, finishCalls)
            )
            withRegisteredReceiver(
                receiver,
                MedicationNotificationActionReceiver.ACTION_CONFIRM_DOSE
            ) {
                context.sendBroadcast(validNotificationIntent().setPackage(context.packageName))
                assertTrue("notification outcome $outcome did not finish", finished.awaitCompletion())
            }
            assertEquals(1, finishCalls.get())
        }
    }

    @Test
    fun reminderRescheduleAndWidgetReceiversFinishTheirAsyncDeliveries() {
        val cases = listOf(
            receiverCase(
                action = ACTION_REMINDER_TEST,
                receiver = { finished, calls ->
                    MedicationReminderReceiver(
                        workFactory = { ReminderDeliveryWork { ReminderDeliveryOutcome.Notified } },
                        workLauncher = launcher(finished, calls)
                    )
                },
                intent = {
                    Intent(ACTION_REMINDER_TEST).apply {
                        putExtra(MedicationReminderReceiver.EXTRA_PLAN_ID, PLAN_ID.toString())
                        putExtra(MedicationReminderReceiver.EXTRA_NOTIFICATION_ID, 91)
                        putExtra(
                            MedicationReminderReceiver.EXTRA_SCHEDULED_AT_MILLIS,
                            1_800_000_000_000L
                        )
                    }
                }
            ),
            receiverCase(
                action = ACTION_RESCHEDULE_TEST,
                receiver = { finished, calls ->
                    ReminderRescheduleReceiver(
                        workFactory = {
                            ReminderRescheduleWork { ReminderRescheduleOutcome.Rescheduled }
                        },
                        workLauncher = launcher(finished, calls)
                    )
                },
                intent = { Intent(ACTION_RESCHEDULE_TEST) }
            ),
            receiverCase(
                action = ACTION_WIDGET_RECORD,
                receiver = { finished, calls ->
                    EvoluneWidgetReceiver(
                        quickActionWorkFactory = {
                            WidgetQuickActionWork { WidgetQuickActionOutcome.Accepted(false) }
                        },
                        updateWorkFactory = { _, _ -> WidgetUpdateWork {} },
                        workLauncher = launcher(finished, calls)
                    )
                },
                intent = {
                    Intent(ACTION_WIDGET_RECORD).putExtra("plan_id", PLAN_ID.toString())
                }
            )
        )

        cases.forEach { case ->
            val finished = CountDownLatch(1)
            val finishCalls = AtomicInteger()
            val receiver = case.receiver(finished, finishCalls)
            withRegisteredReceiver(receiver, case.action) {
                context.sendBroadcast(case.intent().setPackage(context.packageName))
                assertTrue("${case.action} did not finish", finished.awaitCompletion())
            }
            assertEquals(1, finishCalls.get())
        }
    }

    @Test
    fun receiverReturnsBeforeSuspendedWorkAndFinishesAfterResume() {
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val finishCalls = AtomicInteger()
        lateinit var resumeWork: () -> Unit
        val receiver = MedicationReminderReceiver(
            workFactory = {
                ReminderDeliveryWork {
                    suspendCancellableCoroutine { continuation ->
                        resumeWork = { continuation.resume(Unit) }
                        started.countDown()
                    }
                    ReminderDeliveryOutcome.Notified
                }
            },
            workLauncher = launcher(finished, finishCalls)
        )

        withRegisteredReceiver(receiver, ACTION_REMINDER_TEST) {
            context.sendBroadcast(
                Intent(ACTION_REMINDER_TEST).apply {
                    setPackage(context.packageName)
                    putExtra(MedicationReminderReceiver.EXTRA_PLAN_ID, PLAN_ID.toString())
                }
            )
            assertTrue(started.awaitCompletion())
            assertEquals(1L, finished.count)
            resumeWork()
            assertTrue(finished.awaitCompletion())
        }
        assertEquals(1, finishCalls.get())
    }

    @Test
    fun cancellationAndUnexpectedExceptionStillFinishOnce() {
        listOf<Throwable>(
            CancellationException("synthetic cancellation"),
            IllegalStateException("synthetic receiver exception")
        ).forEach { failure ->
            val finished = CountDownLatch(1)
            val finishCalls = AtomicInteger()
            val unexpected = AtomicInteger()
            val receiver = ReminderRescheduleReceiver(
                workFactory = {
                    ReminderRescheduleWork { throw failure }
                },
                workLauncher = ReceiverWorkLauncher(
                    dispatcher = Dispatchers.Default,
                    onUnexpectedFailure = { unexpected.incrementAndGet() },
                    onFinished = {
                        finishCalls.incrementAndGet()
                        finished.countDown()
                    }
                )
            )
            withRegisteredReceiver(receiver, ACTION_RESCHEDULE_TEST) {
                context.sendBroadcast(
                    Intent(ACTION_RESCHEDULE_TEST).setPackage(context.packageName)
                )
                assertTrue(finished.awaitCompletion())
            }
            assertEquals(1, finishCalls.get())
            assertEquals(if (failure is CancellationException) 0 else 1, unexpected.get())
        }
    }

    @Test
    fun synchronousRejectionsDoNotStartAsyncWork() {
        val starts = AtomicInteger()
        MedicationReminderReceiver(
            workFactory = {
                starts.incrementAndGet()
                ReminderDeliveryWork { ReminderDeliveryOutcome.Notified }
            }
        ).onReceive(context, Intent(ACTION_REMINDER_TEST))
        MedicationNotificationActionReceiver(
            workFactory = {
                starts.incrementAndGet()
                NotificationActionWork { NotificationActionOutcome.Accepted(false) }
            }
        ).onReceive(context, Intent("synthetic.unknown.action"))
        EvoluneWidgetReceiver(
            quickActionWorkFactory = {
                starts.incrementAndGet()
                WidgetQuickActionWork { WidgetQuickActionOutcome.Accepted(false) }
            },
            updateWorkFactory = { _, _ -> WidgetUpdateWork {} }
        ).onReceive(context, Intent("synthetic.widget.noop"))

        assertEquals(0, starts.get())
    }

    private fun launcher(
        finished: CountDownLatch,
        finishCalls: AtomicInteger
    ) = ReceiverWorkLauncher(
        dispatcher = Dispatchers.Default,
        onFinished = {
            finishCalls.incrementAndGet()
            finished.countDown()
        }
    )

    private fun validNotificationIntent() = Intent(
        MedicationNotificationActionReceiver.ACTION_CONFIRM_DOSE
    ).apply {
        putExtra(MedicationNotificationActionReceiver.EXTRA_PLAN_ID, PLAN_ID.toString())
        putExtra(MedicationNotificationActionReceiver.EXTRA_NOTIFICATION_ID, 90)
        putExtra(
            MedicationNotificationActionReceiver.EXTRA_SCHEDULED_AT_MILLIS,
            1_800_000_000_000L
        )
    }

    private fun withRegisteredReceiver(
        receiver: BroadcastReceiver,
        action: String,
        block: () -> Unit
    ) {
        context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        try {
            block()
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun CountDownLatch.awaitCompletion(): Boolean = await(10, TimeUnit.SECONDS)

    private fun receiverCase(
        action: String,
        receiver: (CountDownLatch, AtomicInteger) -> BroadcastReceiver,
        intent: () -> Intent
    ) = ReceiverCase(action, receiver, intent)

    private data class ReceiverCase(
        val action: String,
        val receiver: (CountDownLatch, AtomicInteger) -> BroadcastReceiver,
        val intent: () -> Intent
    )

    private companion object {
        const val ACTION_REMINDER_TEST = "io.github.yingqiu0871.evolune.test.REMINDER"
        const val ACTION_RESCHEDULE_TEST = "io.github.yingqiu0871.evolune.test.RESCHEDULE"
        const val ACTION_WIDGET_RECORD = "io.github.yingqiu0871.evolune.widget.RECORD_PLAN"
        val PLAN_ID: UUID = UUID(0L, 901L)
    }
}
