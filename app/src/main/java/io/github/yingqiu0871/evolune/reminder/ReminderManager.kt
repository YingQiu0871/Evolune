package io.github.yingqiu0871.evolune.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.yingqiu0871.evolune.core.model.MedicationPlan as DomainMedicationPlan
import io.github.yingqiu0871.evolune.utils.description
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

interface MedicationPlanReminderScheduler {
    fun schedule(plan: DomainMedicationPlan)
    fun cancel(planId: UUID)
    suspend fun reschedule(plans: List<DomainMedicationPlan>)
}

/**
 * 用药提醒管理器
 * 负责管理用药方案的提醒
 */
class ReminderManager(
    private val context: Context
) : MedicationPlanReminderScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private companion object {
        const val MAX_PLAN_TIME_POINTS = 10
    }

    override fun schedule(plan: DomainMedicationPlan) {
        scheduleReminder(plan)
    }

    override fun cancel(planId: UUID) {
        cancelReminder(planId)
    }

    override suspend fun reschedule(plans: List<DomainMedicationPlan>) {
        rescheduleDomainReminders(plans)
    }

    fun scheduleReminder(plan: DomainMedicationPlan) {
        if (!plan.isEnabled) {
            return
        }
        cancelReminder(plan.id)
        reminderOccurrences(plan, LocalDateTime.now()).forEach { occurrence ->
            scheduleAlarm(plan, occurrence.dateTime, occurrence.requestOffset)
        }
    }

    /**
     * 取消用药方案的所有提醒
     */
    fun cancelReminder(planId: UUID) {
        for (timeIndex in 0 until MAX_PLAN_TIME_POINTS) {
            for (dayIndex in 0 until SCHEDULED_OCCURRENCES_PER_TIME) {
                val requestCode = reminderRequestCode(planId, timeIndex, dayIndex)
                val intent = Intent(context, MedicationReminderReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                pendingIntent?.let {
                    alarmManager.cancel(it)
                    it.cancel()
                }
            }
        }
    }

    /**
     * 重新设置所有启用方案的提醒
     */
    suspend fun rescheduleDomainReminders(plans: List<DomainMedicationPlan>) {
        plans.forEach { cancelReminder(it.id) }
        plans.filter { it.isEnabled }.forEach { scheduleReminder(it) }
    }

    /**
     * 设置单次提醒
     */
    private fun scheduleAlarm(
        plan: DomainMedicationPlan,
        dateTime: LocalDateTime,
        timeIndex: Int
    ) {
        scheduleAlarm(
            planId = plan.id,
            planName = plan.name,
            planDescription = plan.description(),
            dateTime = dateTime,
            timeIndex = timeIndex
        )
    }

    private fun scheduleAlarm(
        planId: UUID,
        planName: String,
        planDescription: String,
        dateTime: LocalDateTime,
        timeIndex: Int
    ) {
        val scheduledAtMillis =
            dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Wait until the end of the ±1 hour check-in window before deciding
        // whether a reminder is still needed.
        val triggerTime = reminderEvaluationTimeMillis(scheduledAtMillis)
        
        // 如果时间已经过去，不设置提醒
        if (triggerTime < System.currentTimeMillis()) {
            return
        }

        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            putExtra(MedicationReminderReceiver.EXTRA_PLAN_ID, planId.toString())
            putExtra(MedicationReminderReceiver.EXTRA_PLAN_NAME, planName)
            putExtra(MedicationReminderReceiver.EXTRA_PLAN_DESCRIPTION, planDescription)
            putExtra(MedicationReminderReceiver.EXTRA_NOTIFICATION_ID, planId.hashCode() + timeIndex)
            putExtra(MedicationReminderReceiver.EXTRA_SCHEDULED_AT_MILLIS, scheduledAtMillis)
        }

        val requestCode = planId.hashCode() + timeIndex
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 设置精确提醒
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12及以上，检查是否有精确闹钟权限
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                // 如果没有权限，使用非精确提醒
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

}
