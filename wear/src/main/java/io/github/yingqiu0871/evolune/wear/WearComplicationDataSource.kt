package io.github.yingqiu0871.evolune.wear

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService.ComplicationRequestListener
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOccurrenceStatus
import java.util.Locale
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun plain(value: String): PlainComplicationText =
    PlainComplicationText.Builder(value).build()

internal fun buildShortTextData(
    context: Context?,
    value: String,
    title: String,
    tapAction: Boolean = true
): ShortTextComplicationData {
    val contract = complicationTextContract(title, value)
    val builder = ShortTextComplicationData.Builder(
        text = plain(contract.value),
        contentDescription = plain(contract.contentDescription)
    ).setTitle(plain(title))
    if (tapAction) {
        requireNotNull(context) { "tapAction requires a Context" }
        builder.setTapAction(
            PendingIntent.getActivity(
                context,
                title.hashCode(),
                Intent(context, WearAppActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }
    return builder.build()
}

internal data class WearComplicationTextContract(
    val value: String,
    val contentDescription: String
)

internal fun complicationTextContract(title: String, value: String): WearComplicationTextContract =
    WearComplicationTextContract(value, "$title：$value")

internal fun complicationContentDescription(title: String, value: String): String =
    complicationTextContract(title, value).contentDescription

private fun shortText(
    context: Context,
    value: String,
    title: String,
    tapAction: Boolean = true
): ShortTextComplicationData = buildShortTextData(context, value, title, tapAction)

internal fun requestWearComplicationUpdates(context: Context) {
    listOf(
        NextDoseComplicationDataSource::class.java,
        TodayProgressComplicationDataSource::class.java,
        CurrentE2ComplicationDataSource::class.java
    ).forEach { serviceClass ->
        androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
            .create(context, ComponentName(context, serviceClass))
            .requestUpdateAll()
    }
}

enum class WearComplicationKind {
    NEXT_DOSE,
    TODAY_PROGRESS,
    CURRENT_E2
}

internal fun complicationPreviewData(kind: WearComplicationKind): ShortTextComplicationData =
    buildShortTextData(null, complicationPreviewValue(kind), complicationTitle(kind), tapAction = false)

internal fun complicationDataForState(
    kind: WearComplicationKind,
    snapshot: io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot?,
    state: WearAppDisplayState,
    nowMillis: Long
): ShortTextComplicationData = buildShortTextData(
    null,
    complicationDisplayValueForState(kind, snapshot, state, nowMillis),
    complicationTitle(kind),
    tapAction = false
)

private fun complicationTitle(kind: WearComplicationKind): String = when (kind) {
    WearComplicationKind.NEXT_DOSE -> "下一次"
    WearComplicationKind.TODAY_PROGRESS -> "今日进度"
    WearComplicationKind.CURRENT_E2 -> "当前 E2"
}

internal fun complicationPreviewValue(kind: WearComplicationKind): String = when (kind) {
    WearComplicationKind.NEXT_DOSE -> "08:00"
    WearComplicationKind.TODAY_PROGRESS -> "2/3"
    WearComplicationKind.CURRENT_E2 -> "123"
}

internal fun complicationDisplayValueForState(
    kind: WearComplicationKind,
    snapshot: io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot?,
    state: WearAppDisplayState,
    nowMillis: Long
): String = if (state == WearAppDisplayState.READY) complicationValue(kind, snapshot, nowMillis) else "--"

private fun complicationValue(
    kind: WearComplicationKind,
    snapshot: io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot?,
    nowMillis: Long
): String = when (kind) {
    WearComplicationKind.NEXT_DOSE -> nextDoseComplicationText(snapshot)
    WearComplicationKind.TODAY_PROGRESS -> todayProgressComplicationText(snapshot)
    WearComplicationKind.CURRENT_E2 -> currentE2ComplicationText(snapshot, nowMillis)
}

abstract class WearShortTextComplicationDataSource : ComplicationDataSourceService() {
    protected abstract val kind: WearComplicationKind
    protected abstract val title: String
    protected abstract val previewValue: String
    protected abstract fun value(
        snapshot: io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot?,
        nowMillis: Long
    ): String

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val now = System.currentTimeMillis()
        val presentation = WearAppStore.getPresentation(this, now)
        val visible = presentation.state == WearAppDisplayState.READY
        listener.onComplicationData(shortText(this, if (visible) value(presentation.snapshot, now) else "--", title))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData = complicationPreviewData(kind)
}

class NextDoseComplicationDataSource : WearShortTextComplicationDataSource() {
    override val kind = WearComplicationKind.NEXT_DOSE
    override val title = "下一次"
    override val previewValue = "08:00"

    override fun value(snapshot: io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot?, nowMillis: Long): String {
        return nextDoseComplicationText(snapshot)
    }
}

class TodayProgressComplicationDataSource : WearShortTextComplicationDataSource() {
    override val kind = WearComplicationKind.TODAY_PROGRESS
    override val title = "今日进度"
    override val previewValue = "2/3"

    override fun value(snapshot: io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot?, nowMillis: Long): String {
        return todayProgressComplicationText(snapshot)
    }
}

class CurrentE2ComplicationDataSource : WearShortTextComplicationDataSource() {
    override val kind = WearComplicationKind.CURRENT_E2
    override val title = "当前 E2"
    override val previewValue = "123"

    override fun value(snapshot: io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot?, nowMillis: Long): String {
        return currentE2ComplicationText(snapshot, nowMillis)
    }
}

internal fun nextDoseComplicationText(
    snapshot: io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot?
): String {
    val occurrence = snapshot?.upcomingOccurrences?.firstOrNull() ?: return "--"
    if (occurrence.status == WearAppOccurrenceStatus.DUE) return "现在"
    val zoneId = snapshot.zoneId.let { runCatching { ZoneId.of(it) }.getOrDefault(ZoneId.systemDefault()) }
    return DateTimeFormatter.ofPattern("HH:mm")
        .format(occurrence.scheduledAt.atZone(zoneId))
}

internal fun todayProgressComplicationText(
    snapshot: io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot?
): String {
    val summary = snapshot?.todaySummary ?: return "--"
    return "${summary.completedCount}/${summary.totalCount}"
}

internal fun currentE2ComplicationText(
    snapshot: io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot?,
    nowMillis: Long? = null
): String {
    val concentration = snapshot?.concentrationState ?: return "--"
    if (concentration.status != WearAppConcentrationStatus.AVAILABLE) return "--"
    if (nowMillis != null &&
        deriveWearAppConcentrationPresentation(snapshot, nowMillis).state != WearAppConcentrationDisplayState.FRESH
    ) return "--"
    val value = concentration.value ?: return "--"
    return String.format(Locale.ROOT, "%.0f", value)
}
