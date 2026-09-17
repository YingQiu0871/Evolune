package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Strict classification of canonical document failures (frozen contract §5/§17/§18). */
internal enum class PortableJsonError {
    MALFORMED,
    DUPLICATE_KEY,
    UNKNOWN_FIELD,
    MISSING_FIELD,
    INVALID_FIELD_TYPE,
    INVALID_FIELD,
    INVALID_SCHEMA,
    UNSUPPORTED_VERSION,
    TOO_MANY_EVENTS
}

internal sealed interface PortableJsonDecodeResult {
    data class Success(val document: PortableDocument) : PortableJsonDecodeResult

    data class Failure(val error: PortableJsonError) : PortableJsonDecodeResult
}

/**
 * Evolune Portable JSON v1 codec (frozen contract §6/§10/§12–§17/§29):
 * ordered fields, explicit nulls, canonical numbers, lexical extras, strict fail-closed decoding.
 * Pure and deterministic: same request + same events -> byte-identical output.
 */
internal object PortableJsonCodec {

    private const val SCHEMA_VALUE = "evolune-portable"
    private const val VERSION_VALUE = 1

    private val TOP_LEVEL_FIELDS = setOf("schema", "version", "captured_at", "range", "events")
    private val EVENT_FIELDS = setOf(
        "id",
        "actual_time",
        "local_date",
        "zone_id",
        "route",
        "ester",
        "dose_mg",
        "extras",
        "slot_id",
        "source",
        "status"
    )

    // ---------------------------------------------------------------- encoding

    fun encode(request: PortableExportRequest, events: List<PortableEvent>): ByteArray? {
        val capturedAt = PortableJsonText.formatInstant(request.capturedAt) ?: return null
        val builder = StringBuilder(96 + events.size * 420)
        builder.append("{\n")
        builder.append("  \"schema\": \"").append(SCHEMA_VALUE).append("\",\n")
        builder.append("  \"version\": ").append(VERSION_VALUE).append(",\n")
        builder.append("  \"captured_at\": \"").append(capturedAt).append("\",\n")
        builder.append("  \"range\": \"").append(request.range.name).append("\",\n")
        if (events.isEmpty()) {
            builder.append("  \"events\": []\n")
        } else {
            builder.append("  \"events\": [\n")
            events.forEachIndexed { index, event ->
                val fields = encodeEvent(event) ?: return null
                builder.append("    {\n")
                builder.append(fields)
                builder.append("    }")
                if (index != events.lastIndex) builder.append(',')
                builder.append('\n')
            }
            builder.append("  ]\n")
        }
        builder.append("}\n")
        return builder.toString().toByteArray(Charsets.UTF_8)
    }

    private fun encodeEvent(event: PortableEvent): String? {
        val actualTime = PortableJsonText.formatInstant(event.occurredAt) ?: return null
        if (!event.doseMG.isFinite()) return null
        if (event.extras.values.any { !it.isFinite() }) return null
        return buildString(360) {
            append("      \"id\": ").append(PortableJsonText.stringLiteral(event.id.toString())).append(",\n")
            append("      \"actual_time\": ").append(PortableJsonText.stringLiteral(actualTime)).append(",\n")
            append("      \"local_date\": ").append(PortableJsonText.nullableString(event.localDate?.toString())).append(",\n")
            append("      \"zone_id\": ").append(PortableJsonText.nullableString(event.zoneId?.id)).append(",\n")
            append("      \"route\": ").append(PortableJsonText.stringLiteral(event.route.name)).append(",\n")
            append("      \"ester\": ").append(PortableJsonText.stringLiteral(event.ester.name)).append(",\n")
            append("      \"dose_mg\": ").append(PortableJsonText.canonicalNumber(event.doseMG)).append(",\n")
            append("      \"extras\": ").append(PortableJsonText.compactExtras(event.extras)).append(",\n")
            append("      \"slot_id\": ").append(PortableJsonText.nullableString(event.slotId?.toString())).append(",\n")
            append("      \"source\": ").append(PortableJsonText.stringLiteral(event.source.name)).append(",\n")
            append("      \"status\": ").append(PortableJsonText.stringLiteral(event.status.name)).append("\n")
        }
    }

    // ---------------------------------------------------------------- decoding

    fun decode(input: ByteArray, maxEventCount: Int): PortableJsonDecodeResult {
        val text = String(input, Charsets.UTF_8)
        if (PortableJsonDuplicateKeyScanner.hasDuplicateKey(text)) {
            return PortableJsonDecodeResult.Failure(PortableJsonError.DUPLICATE_KEY)
        }
        val root = try {
            Json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return PortableJsonDecodeResult.Failure(PortableJsonError.MALFORMED)
        } catch (_: IllegalArgumentException) {
            return PortableJsonDecodeResult.Failure(PortableJsonError.MALFORMED)
        }
        val rootObject = root as? JsonObject
            ?: return PortableJsonDecodeResult.Failure(PortableJsonError.MALFORMED)

        if (rootObject.keys.any { it !in TOP_LEVEL_FIELDS }) {
            return PortableJsonDecodeResult.Failure(PortableJsonError.UNKNOWN_FIELD)
        }

        val schema = rootObject["schema"]
            ?: return PortableJsonDecodeResult.Failure(PortableJsonError.INVALID_SCHEMA)
        if (schema !is JsonPrimitive || !schema.isString || schema.content != SCHEMA_VALUE) {
            return PortableJsonDecodeResult.Failure(PortableJsonError.INVALID_SCHEMA)
        }

        val version = rootObject["version"]
            ?: return PortableJsonDecodeResult.Failure(PortableJsonError.MISSING_FIELD)
        if (version !is JsonPrimitive || version.isString) {
            return PortableJsonDecodeResult.Failure(PortableJsonError.INVALID_FIELD_TYPE)
        }
        val versionNumber = version.intOrNull
            ?: return PortableJsonDecodeResult.Failure(PortableJsonError.INVALID_FIELD_TYPE)
        if (versionNumber != VERSION_VALUE) {
            return PortableJsonDecodeResult.Failure(PortableJsonError.UNSUPPORTED_VERSION)
        }

        val capturedAtText = rootObject["captured_at"].asRequiredString()
            ?: return PortableJsonDecodeResult.Failure(
                rootObject.missingOrType("captured_at")
            )
        val capturedAt = PortableJsonText.parseInstant(capturedAtText)
            ?: return PortableJsonDecodeResult.Failure(PortableJsonError.INVALID_FIELD)

        val rangeText = rootObject["range"].asRequiredString()
            ?: return PortableJsonDecodeResult.Failure(rootObject.missingOrType("range"))
        val range = PortableExportRange.values().firstOrNull { it.name == rangeText }
            ?: return PortableJsonDecodeResult.Failure(PortableJsonError.INVALID_FIELD)

        val eventsElement = rootObject["events"]
            ?: return PortableJsonDecodeResult.Failure(PortableJsonError.MISSING_FIELD)
        val eventsArray = eventsElement as? JsonArray
            ?: return PortableJsonDecodeResult.Failure(PortableJsonError.INVALID_FIELD_TYPE)
        if (eventsArray.size > maxEventCount) {
            return PortableJsonDecodeResult.Failure(PortableJsonError.TOO_MANY_EVENTS)
        }

        val events = ArrayList<PortableEvent>(eventsArray.size)
        eventsArray.forEachIndexed { index, element ->
            val eventObject = element as? JsonObject
                ?: return PortableJsonDecodeResult.Failure(PortableJsonError.INVALID_FIELD_TYPE)
            val decoded = decodeEvent(eventObject)
            if (decoded is EventDecodeResult.Failure) {
                return PortableJsonDecodeResult.Failure(decoded.error)
            }
            events += (decoded as EventDecodeResult.Success).event
        }

        return PortableJsonDecodeResult.Success(
            PortableDocument(capturedAt = capturedAt, range = range, events = events)
        )
    }

    private sealed interface EventDecodeResult {
        data class Success(val event: PortableEvent) : EventDecodeResult

        data class Failure(val error: PortableJsonError) : EventDecodeResult
    }

    private fun decodeEvent(event: JsonObject): EventDecodeResult {
        if (event.keys.any { it !in EVENT_FIELDS }) {
            return EventDecodeResult.Failure(PortableJsonError.UNKNOWN_FIELD)
        }

        val idText = event["id"].asRequiredString()
            ?: return EventDecodeResult.Failure(event.missingOrType("id"))
        val id = PortableJsonText.parseUuid(idText)
            ?: return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD)

        val actualTimeText = event["actual_time"].asRequiredString()
            ?: return EventDecodeResult.Failure(event.missingOrType("actual_time"))
        val actualTime = PortableJsonText.parseInstant(actualTimeText)
            ?: return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD)

        val localDate = when (val element = event["local_date"]) {
            null, is JsonNull -> if ("local_date" in event) null else {
                return EventDecodeResult.Failure(PortableJsonError.MISSING_FIELD)
            }

            is JsonPrimitive -> {
                if (!element.isString) {
                    return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD_TYPE)
                }
                PortableJsonText.parseLocalDate(element.content)
                    ?: return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD)
            }

            else -> return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD_TYPE)
        }

        val zoneId = when (val element = event["zone_id"]) {
            null, is JsonNull -> if ("zone_id" in event) null else {
                return EventDecodeResult.Failure(PortableJsonError.MISSING_FIELD)
            }

            is JsonPrimitive -> {
                if (!element.isString) {
                    return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD_TYPE)
                }
                PortableJsonText.parseZoneId(element.content)
                    ?: return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD)
            }

            else -> return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD_TYPE)
        }

        val routeText = event["route"].asRequiredString()
            ?: return EventDecodeResult.Failure(event.missingOrType("route"))
        val route = Route.values().firstOrNull { it.name == routeText }
            ?: return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD)

        val esterText = event["ester"].asRequiredString()
            ?: return EventDecodeResult.Failure(event.missingOrType("ester"))
        val ester = Ester.values().firstOrNull { it.name == esterText }
            ?: return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD)

        val doseMG = event.numericField("dose_mg")
            ?: return EventDecodeResult.Failure(event.missingOrType("dose_mg"))

        val extrasElement = event["extras"]
            ?: return EventDecodeResult.Failure(PortableJsonError.MISSING_FIELD)
        val extrasObject = extrasElement as? JsonObject
            ?: return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD_TYPE)
        val extras = LinkedHashMap<ExtraKey, Double>(extrasObject.size)
        for ((key, value) in extrasObject) {
            val extraKey = ExtraKey.values().firstOrNull { it.name == key }
                ?: return EventDecodeResult.Failure(PortableJsonError.UNKNOWN_FIELD)
            val extraValue = (value as? JsonPrimitive)?.numberOrNull()
                ?: return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD_TYPE)
            if (!extraValue.isFinite()) {
                return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD)
            }
            extras[extraKey] = extraValue
        }

        val slotId = when (val element = event["slot_id"]) {
            null, is JsonNull -> if ("slot_id" in event) null else {
                return EventDecodeResult.Failure(PortableJsonError.MISSING_FIELD)
            }

            is JsonPrimitive -> {
                if (!element.isString) {
                    return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD_TYPE)
                }
                PortableJsonText.parseUuid(element.content)
                    ?: return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD)
            }

            else -> return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD_TYPE)
        }

        val sourceText = event["source"].asRequiredString()
            ?: return EventDecodeResult.Failure(event.missingOrType("source"))
        val source = DoseEventSource.values().firstOrNull { it.name == sourceText }
            ?: return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD)

        val statusText = event["status"].asRequiredString()
            ?: return EventDecodeResult.Failure(event.missingOrType("status"))
        val status = DoseEventStatus.values().firstOrNull { it.name == statusText }
            ?: return EventDecodeResult.Failure(PortableJsonError.INVALID_FIELD)

        return EventDecodeResult.Success(
            PortableEvent(
                id = id,
                occurredAt = actualTime,
                localDate = localDate,
                zoneId = zoneId,
                route = route,
                ester = ester,
                doseMG = doseMG,
                extras = extras,
                slotId = slotId,
                source = source,
                status = status
            )
        )
    }

    private fun JsonElement?.asRequiredString(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return if (primitive.isString) primitive.content else null
    }

    private fun JsonObject.missingOrType(name: String): PortableJsonError =
        if (name !in this) PortableJsonError.MISSING_FIELD else PortableJsonError.INVALID_FIELD_TYPE

    private fun JsonObject.numericField(name: String): Double? {
        val primitive = this[name] as? JsonPrimitive ?: return null
        if (primitive is JsonNull || primitive.isString) return null
        val value = primitive.doubleOrNull ?: return null
        return value.takeIf(Double::isFinite)
    }

    private fun JsonPrimitive.numberOrNull(): Double? {
        if (this is JsonNull || isString) return null
        return doubleOrNull
    }
}
