package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlin.math.floor

/**
 * Evolune CSV v1 codec (frozen contract §23–§28): EXPORT ONLY, exact 16-column order, RFC4180
 * quoting, LF endings, one final LF, canonical numbers and instants.
 */
internal object PortableCsvCodec {

    const val HEADER: String =
        "schema_version,event_id,date,medication,dose,route,planned_time," +
            "actual_time,timing_delta,event_type,status,ester,zone_id,slot_id,source,extras_json"

    fun encode(events: List<PortableEvent>): ByteArray? {
        val builder = StringBuilder(HEADER.length + 16 + events.size * 180)
        builder.append(HEADER).append('\n')
        for (event in events) {
            val actualTime = PortableJsonText.formatInstant(event.occurredAt) ?: return null
            if (!event.doseMG.isFinite()) return null
            if (event.extras.values.any { !it.isFinite() }) return null
            val fields = listOf(
                "1",
                event.id.toString(),
                event.localDate?.toString().orEmpty(),
                medication(event.route, event.ester, event.extras),
                PortableJsonText.canonicalNumber(event.doseMG),
                event.route.name,
                "",
                actualTime,
                "",
                EVENT_TYPE,
                event.status.name,
                event.ester.name,
                event.zoneId?.id.orEmpty(),
                event.slotId?.toString().orEmpty(),
                event.source.name,
                PortableJsonText.compactExtras(event.extras)
            )
            fields.forEachIndexed { index, value ->
                if (index > 0) builder.append(',')
                builder.append(quoteField(value))
            }
            builder.append('\n')
        }
        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Table D (frozen contract §27): informational, deterministic, non-localized identity
     * projection. Unknown/partial/unavailable identity projects to empty — never a guess.
     */
    fun medication(
        route: Route,
        ester: Ester,
        extras: Map<ExtraKey, Double>
    ): String = when (route) {
        Route.ANTIANDROGEN -> {
            val value = extras[ExtraKey.ANTI_ANDROGEN_TYPE]
            if (value != null && value.isFinite() && value == floor(value) && value >= 0.0 && value <= 3.0) {
                when (value.toInt()) {
                    0 -> "CPA"
                    1 -> "MPA"
                    2 -> "BICALUTAMIDE"
                    3 -> "SPIRONOLACTONE"
                    else -> ""
                }
            } else {
                ""
            }
        }

        Route.INJECTION,
        Route.ORAL,
        Route.SUBLINGUAL,
        Route.GEL,
        Route.PATCH_APPLY,
        Route.PATCH_REMOVE -> when (ester) {
            Ester.E2 -> "E2"
            Ester.EB -> "EB"
            Ester.EV -> "EV"
            Ester.EC -> "EC"
            Ester.EN -> "EN"
        }
    }

    /** RFC4180: quote only when required; embedded quotes are doubled. */
    fun quoteField(value: String): String {
        val requiresQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!requiresQuoting) return value
        return buildString(value.length + 2) {
            append('"')
            value.forEach { character ->
                if (character == '"') append("\"\"") else append(character)
            }
            append('"')
        }
    }

    private const val EVENT_TYPE = "recorded_intake"
}
