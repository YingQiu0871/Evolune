package io.github.yingqiu0871.evolune.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.github.yingqiu0871.evolune.experience.wear.WearAppOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WearAppActivity : android.app.Activity() {
    private lateinit var syncState: TextView
    private lateinit var recentCard: LinearLayout
    private lateinit var recentDose: TextView
    private lateinit var upcomingList: LinearLayout
    private lateinit var emptyState: TextView
    private val dateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    private val snapshotReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wear_app)
        syncState = findViewById(R.id.wear_app_sync_state)
        recentCard = findViewById(R.id.wear_app_recent_card)
        recentDose = findViewById(R.id.wear_app_recent_dose)
        upcomingList = findViewById(R.id.wear_app_upcoming_list)
        emptyState = findViewById(R.id.wear_app_empty_state)
        render()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(WEAR_APP_SNAPSHOT_CHANGED_ACTION)
        ContextCompat.registerReceiver(
            this,
            snapshotReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        render()
        WearAppDataLayer.requestSnapshot(this)
    }

    override fun onStop() {
        unregisterReceiver(snapshotReceiver)
        super.onStop()
    }

    private fun render() {
        if (!::syncState.isInitialized) return
        val presentation = WearAppStore.getPresentation(this, System.currentTimeMillis())
        syncState.text = stateText(presentation.state)
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
        }

        upcomingList.removeAllViews()
        val upcoming = snapshot?.upcomingOccurrences.orEmpty()
        upcoming.forEach { occurrence ->
            val row = TextView(this).apply {
                text = getString(
                    R.string.wear_app_upcoming_format,
                    occurrence.medicationName,
                    formatDose(occurrence.dose),
                    occurrence.scheduledAt.atZone(zoneId).format(dateFormatter),
                    statusText(occurrence.status)
                )
                contentDescription = text
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(0, 18, 0, 18)
                isFocusable = false
                isClickable = false
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

    private fun stateText(state: WearAppDisplayState): String = when (state) {
        WearAppDisplayState.WAITING_FOR_PHONE -> getString(R.string.wear_app_waiting)
        WearAppDisplayState.SYNCING -> getString(R.string.wear_app_syncing)
        WearAppDisplayState.READY -> getString(R.string.wear_app_synced)
        WearAppDisplayState.EMPTY -> getString(R.string.wear_app_empty)
        WearAppDisplayState.OFFLINE -> getString(R.string.wear_app_offline)
        WearAppDisplayState.STALE -> getString(R.string.wear_app_stale)
        WearAppDisplayState.ERROR -> getString(R.string.wear_app_sync_error)
    }

    private fun statusText(status: WearAppOccurrenceStatus): String = when (status) {
        WearAppOccurrenceStatus.UPCOMING -> getString(R.string.wear_app_status_upcoming)
        WearAppOccurrenceStatus.DUE -> getString(R.string.wear_app_status_due)
    }

    private fun formatDose(dose: Double): String =
        getString(R.string.wear_app_dose_format, dose)
}
