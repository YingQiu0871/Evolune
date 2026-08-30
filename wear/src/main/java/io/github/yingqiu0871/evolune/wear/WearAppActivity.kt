package io.github.yingqiu0871.evolune.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import androidx.core.content.ContextCompat
import io.github.yingqiu0871.evolune.experience.wear.WearAppOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppRecentDose
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppUndoMessageCode
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class WearAppActivity : android.app.Activity() {
    private lateinit var scrollView: ScrollView
    private lateinit var syncState: TextView
    private lateinit var recentCard: LinearLayout
    private lateinit var recentDose: TextView
    private lateinit var upcomingList: LinearLayout
    private lateinit var emptyState: TextView
    private val dateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var isActivityVisible = false
    private var receiverRegistered = false

    private val refreshRunnable = Runnable {
        if (shouldRunWearAppRefreshCallback(isActivityVisible)) {
            render()
            scheduleNextRefresh()
        }
    }

    private val snapshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (shouldRunWearAppRefreshCallback(isActivityVisible)) {
                render()
                scheduleNextRefresh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear_app)
        scrollView = findViewById(R.id.wear_app_scroll)
        syncState = findViewById(R.id.wear_app_sync_state)
        recentCard = findViewById(R.id.wear_app_recent_card)
        recentDose = findViewById(R.id.wear_app_recent_dose)
        upcomingList = findViewById(R.id.wear_app_upcoming_list)
        emptyState = findViewById(R.id.wear_app_empty_state)
        render()
    }

    override fun onStart() {
        super.onStart()
        isActivityVisible = true
        val filter = IntentFilter(WEAR_APP_STATE_CHANGED_ACTION)
        ContextCompat.registerReceiver(
            this,
            snapshotReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
        render()
        WearAppDataLayer.requestSnapshot(this)
        scheduleNextRefresh()
    }

    override fun onStop() {
        isActivityVisible = false
        refreshHandler.removeCallbacks(refreshRunnable)
        if (receiverRegistered) {
            unregisterReceiver(snapshotReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        isActivityVisible = false
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val sourceIsRotary = event.source and InputDevice.SOURCE_ROTARY_ENCODER ==
            InputDevice.SOURCE_ROTARY_ENCODER
        val isScrollAction = event.action == MotionEvent.ACTION_SCROLL
        val axisValue = event.getAxisValue(MotionEvent.AXIS_SCROLL)
        if (shouldHandleWearAppRotaryScroll(
                isVisible = isActivityVisible,
                isRotaryEncoder = sourceIsRotary,
                isScrollAction = isScrollAction,
                axisValue = axisValue
            )
        ) {
            val scrollFactor = ViewConfigurationCompat.verticalScrollFactor(this)
            scrollView.scrollBy(0, (-axisValue * scrollFactor).roundToInt())
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun scheduleNextRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable)
        if (!isActivityVisible) return
        val nowMillis = System.currentTimeMillis()
        val deadline = WearAppStore.getNextRefreshDeadline(this, nowMillis) ?: return
        refreshHandler.postDelayed(refreshRunnable, delayUntil(deadline, nowMillis))
    }

    private fun delayUntil(deadlineMillis: Long, nowMillis: Long): Long = when {
        deadlineMillis <= nowMillis -> 0L
        nowMillis >= 0L -> deadlineMillis - nowMillis
        deadlineMillis > Long.MAX_VALUE + nowMillis -> Long.MAX_VALUE
        else -> deadlineMillis - nowMillis
    }

    private fun render() {
        if (!::syncState.isInitialized) return
        val presentation = WearAppStore.getPresentation(this, System.currentTimeMillis())
        val pendingOperation = WearAppConfirmationStore.getPendingOperation(this)
        syncState.text = stateText(
            presentation.state,
            pendingOperation,
            WearAppConfirmationStore.consumeUndoResultMessageCode(this)
        )
        val snapshot = presentation.snapshot
        val zoneId = snapshot?.let { runCatching { ZoneId.of(it.zoneId) }.getOrNull() }
            ?: ZoneId.systemDefault()

        val recent = snapshot?.recentDose
        recentCard.visibility = if (recent == null) View.GONE else View.VISIBLE
        if (recent != null) {
            recentDose.text = getString(
                R.string.wear_app_recent_format,
                recent.medicationName,
                formatDose(recent.dose),
                recent.route,
                recent.occurredAt.atZone(zoneId).format(dateFormatter)
            )
            recentDose.contentDescription = recentDose.text
            val canUndo = WearAppStore.canUndoRecentDose(this, snapshot, recent.eventId)
            val canRetryUndo = pendingOperation is WearAppPendingUndo &&
                !pendingOperation.awaitingAuthoritativeSnapshot
            recentCard.minimumHeight = (48f * resources.displayMetrics.density).roundToInt()
            recentCard.isFocusable = canUndo || canRetryUndo
            recentCard.isClickable = canUndo || canRetryUndo
            recentCard.alpha = if (canUndo || canRetryUndo) 1f else 0.72f
            recentCard.contentDescription = getString(
                if (canUndo) R.string.wear_app_undo_action else R.string.wear_app_recent_title
            )
            recentCard.setOnClickListener {
                when {
                    canUndo -> showUndoDialog(snapshot, recent, zoneId)
                    canRetryUndo -> {
                        WearAppDataLayer.retryPending(this@WearAppActivity)
                        render()
                    }
                }
            }
        } else {
            recentCard.isFocusable = false
            recentCard.isClickable = false
            recentCard.alpha = 1f
            recentCard.setOnClickListener(null)
        }

        upcomingList.removeAllViews()
        val upcoming = snapshot?.upcomingOccurrences.orEmpty()
        val pendingConfirmation = pendingOperation as? WearAppPendingConfirmation
        upcoming.forEach { occurrence ->
            val isPending = pendingConfirmation?.occurrenceId == occurrence.occurrenceId
            val canConfirm = snapshot?.let {
                WearAppStore.canConfirm(this, it, occurrence.occurrenceId)
            } == true
            val canRetry = pendingConfirmation?.let {
                isPending && !it.awaitingAuthoritativeSnapshot
            } == true
            val row = TextView(this).apply {
                val description = getString(
                    R.string.wear_app_upcoming_format,
                    occurrence.medicationName,
                    formatDose(occurrence.dose),
                    occurrence.scheduledAt.atZone(zoneId).format(dateFormatter),
                    statusText(occurrence.status)
                )
                text = if (isPending) {
                    "$description · ${getString(
                        if (pendingConfirmation.awaitingAuthoritativeSnapshot) {
                            R.string.wear_app_confirmation_waiting
                        } else {
                            R.string.wear_app_confirmation_pending
                        }
                    )}"
                } else {
                    description
                }
                contentDescription = text
                textSize = 16f
                setTextColor(if (canConfirm || canRetry) Color.WHITE else Color.GRAY)
                setPadding(0, 18, 0, 18)
                minimumHeight = (48f * resources.displayMetrics.density).roundToInt()
                isFocusable = canConfirm || canRetry
                isClickable = canConfirm || canRetry
                alpha = if (canConfirm || canRetry) 1f else 0.72f
                setOnClickListener {
                    when {
                        canConfirm -> showConfirmationDialog(snapshot, occurrence, zoneId)
                        canRetry -> {
                            WearAppDataLayer.retryPending(this@WearAppActivity)
                            render()
                        }
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            upcomingList.addView(row)
        }

        emptyState.visibility = if (upcoming.isEmpty()) View.VISIBLE else View.GONE
        if (upcoming.isEmpty()) {
            emptyState.text = when (presentation.state) {
                WearAppDisplayState.EMPTY -> getString(R.string.wear_app_no_upcoming)
                WearAppDisplayState.WAITING_FOR_PHONE ->
                    getString(R.string.wear_app_waiting)
                WearAppDisplayState.OFFLINE -> getString(R.string.wear_app_offline)
                WearAppDisplayState.STALE -> getString(R.string.wear_app_stale)
                WearAppDisplayState.ERROR -> getString(R.string.wear_app_sync_error)
                WearAppDisplayState.SYNCING -> getString(R.string.wear_app_syncing)
                WearAppDisplayState.READY -> getString(R.string.wear_app_no_upcoming)
            }
        }
    }

    private fun stateText(
        state: WearAppDisplayState,
        pending: WearAppPendingOperation?,
        undoMessageCode: WearAppUndoMessageCode?
    ): String {
        if (pending != null) {
            return when (pending.operationType) {
                WearAppPendingOperationType.CONFIRM -> getString(
                    if (pending.awaitingAuthoritativeSnapshot) {
                        R.string.wear_app_confirmation_waiting
                    } else {
                        R.string.wear_app_confirmation_pending
                    }
                )
                WearAppPendingOperationType.UNDO -> getString(
                    if (pending.awaitingAuthoritativeSnapshot) {
                        R.string.wear_app_undo_waiting
                    } else {
                        R.string.wear_app_undo_pending
                    }
                )
            }
        }
        if (
            undoMessageCode != null &&
            undoMessageCode != WearAppUndoMessageCode.UNDONE &&
            undoMessageCode != WearAppUndoMessageCode.ALREADY_UNDONE
        ) {
            return getString(R.string.wear_app_undo_rejected)
        }
        return when (state) {
            WearAppDisplayState.WAITING_FOR_PHONE -> getString(R.string.wear_app_waiting)
            WearAppDisplayState.SYNCING -> getString(R.string.wear_app_syncing)
            WearAppDisplayState.READY -> getString(R.string.wear_app_synced)
            WearAppDisplayState.EMPTY -> getString(R.string.wear_app_empty)
            WearAppDisplayState.OFFLINE -> getString(R.string.wear_app_offline)
            WearAppDisplayState.STALE -> getString(R.string.wear_app_stale)
            WearAppDisplayState.ERROR -> getString(R.string.wear_app_sync_error)
        }
    }

    private fun showConfirmationDialog(
        snapshot: WearAppSnapshot,
        occurrence: io.github.yingqiu0871.evolune.experience.wear.WearAppUpcomingOccurrence,
        zoneId: ZoneId
    ) {
        val details = getString(
            R.string.wear_app_confirmation_format,
            occurrence.medicationName,
            formatDose(occurrence.dose),
            occurrence.route,
            occurrence.localDate.toString(),
            occurrence.scheduledAt.atZone(zoneId).format(dateFormatter)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.wear_app_confirmation_title)
            .setMessage(details)
            .setNegativeButton(R.string.wear_app_confirmation_cancel, null)
            .setPositiveButton(R.string.wear_app_confirmation_confirm) { _, _ ->
                if (!WearAppDataLayer.confirmOccurrence(this, snapshot, occurrence)) {
                    Toast.makeText(
                        this,
                        R.string.wear_app_confirmation_unavailable,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                render()
            }
            .show()
    }

    private fun showUndoDialog(
        snapshot: WearAppSnapshot,
        recentDose: WearAppRecentDose,
        zoneId: ZoneId
    ) {
        val details = getString(
            R.string.wear_app_undo_format,
            recentDose.medicationName,
            formatDose(recentDose.dose),
            recentDose.route,
            recentDose.occurredAt.atZone(zoneId).format(dateFormatter)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.wear_app_undo_title)
            .setMessage(details)
            .setNegativeButton(R.string.wear_app_confirmation_cancel, null)
            .setPositiveButton(R.string.wear_app_undo_confirm) { _, _ ->
                if (!WearAppDataLayer.undoRecentDose(this, snapshot, recentDose)) {
                    Toast.makeText(
                        this,
                        R.string.wear_app_undo_unavailable,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                render()
            }
            .show()
    }

    private fun statusText(status: WearAppOccurrenceStatus): String = when (status) {
        WearAppOccurrenceStatus.UPCOMING -> getString(R.string.wear_app_status_upcoming)
        WearAppOccurrenceStatus.DUE -> getString(R.string.wear_app_status_due)
    }

    private fun formatDose(dose: Double): String =
        getString(R.string.wear_app_dose_format, dose)
}

private object ViewConfigurationCompat {
    fun verticalScrollFactor(context: Context): Float =
        android.view.ViewConfiguration.get(context).scaledVerticalScrollFactor
            .takeIf { it > 0f }
            ?: (context.resources.displayMetrics.density * 48f)
}
