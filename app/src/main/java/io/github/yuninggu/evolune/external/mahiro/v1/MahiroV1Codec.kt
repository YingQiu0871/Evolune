package io.github.yuninggu.evolune.external.mahiro.v1

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Clock

class MahiroV1Codec(
    private val clock: Clock = Clock.systemUTC()
) {
    private val prettyJson = Json { prettyPrint = true }

    fun decode(jsonContent: String): MahiroV1DecodeResult {
        val root = try {
            Json.parseToJsonElement(jsonContent)
        } catch (error: SerializationException) {
            return MahiroV1DecodeResult.Failure(
                MahiroV1DocumentError.Syntax(error.message.orEmpty())
            )
        } catch (error: IllegalArgumentException) {
            return MahiroV1DecodeResult.Failure(
                MahiroV1DocumentError.Syntax(error.message.orEmpty())
            )
        }

        val rootObject = root as? JsonObject ?: return MahiroV1DecodeResult.Failure(
            MahiroV1DocumentError.InvalidRepresentation("document must be an object")
        )
        val weight = when (val element = rootObject["weight"]) {
            null -> null
            is JsonPrimitive -> element.doubleOrNull
            else -> return MahiroV1DecodeResult.Failure(
                MahiroV1DocumentError.InvalidRepresentation("weight must be a number or absent")
            )
        }
        val events = when (val element = rootObject["events"]) {
            null -> JsonArray(emptyList())
            is JsonArray -> element
            else -> return MahiroV1DecodeResult.Failure(
                MahiroV1DocumentError.InvalidRepresentation("events must be an array or absent")
            )
        }

        val decodedEvents = mutableListOf<MahiroV1DoseEventDto>()
        val diagnostics = mutableListOf<MahiroV1EntryDiagnostic>()
        events.forEachIndexed { index, element ->
            when (val result = decodeEvent(element)) {
                is EventDecodeResult.Success -> decodedEvents += result.event
                is EventDecodeResult.Failure -> diagnostics += MahiroV1EntryDiagnostic(
                    index = index,
                    error = result.error
                )
            }
        }

        return MahiroV1DecodeResult.Success(
            document = MahiroV1DocumentDto(weight = weight, events = decodedEvents),
            diagnostics = diagnostics
        )
    }

    fun encode(document: MahiroV1DocumentDto): String {
        val root = buildJsonObject {
            putJsonObject("meta") {
                put("version", 1)
                put("exportedAt", clock.instant().toString())
            }
            put("weight", document.weight)
            putJsonArray("events") {
                document.events.forEach { event ->
                    addJsonObject {
                        event.id?.let { put("id", it) }
                        put("route", event.route)
                        put("ester", event.ester)
                        put("timeH", event.timeH)
                        put("doseMG", event.doseMG)
                        putJsonObject("extras") {
                            event.extras.forEach { (key, value) -> put(key, value) }
                        }
                    }
                }
            }
            putJsonArray("labResults") {}
            putJsonArray("doseTemplates") {}
        }
        return prettyJson.encodeToString(root)
    }

    private fun decodeEvent(element: JsonElement): EventDecodeResult {
        val event = element as? JsonObject
            ?: return EventDecodeResult.Failure(MahiroV1EntryError.ExpectedObject)
        val id = when (val value = event["id"]) {
            null -> null
            is JsonPrimitive -> if (value.isString) value.content else {
                return EventDecodeResult.Failure(
                    MahiroV1EntryError.InvalidFieldType("id")
                )
            }
            else -> return EventDecodeResult.Failure(
                MahiroV1EntryError.InvalidFieldType("id")
            )
        }
        val route = event.requiredString("route") ?: return event.fieldFailure("route")
        val ester = event.requiredString("ester") ?: return event.fieldFailure("ester")
        val timeH = event.requiredDouble("timeH") ?: return event.fieldFailure("timeH")
        val doseMG = event.requiredDouble("doseMG") ?: return event.fieldFailure("doseMG")
        val extras = when (val value = event["extras"]) {
            null -> emptyMap()
            is JsonObject -> linkedMapOf<String, Double>().apply {
                value.forEach { (key, extra) ->
                    (extra as? JsonPrimitive)?.doubleOrNull?.let { put(key, it) }
                }
            }
            else -> return EventDecodeResult.Failure(
                MahiroV1EntryError.InvalidFieldType("extras")
            )
        }

        return EventDecodeResult.Success(
            MahiroV1DoseEventDto(
                id = id,
                route = route,
                ester = ester,
                timeH = timeH,
                doseMG = doseMG,
                extras = extras
            )
        )
    }

    private fun JsonObject.requiredString(field: String): String? =
        (get(field) as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

    private fun JsonObject.requiredDouble(field: String): Double? =
        (get(field) as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.fieldFailure(field: String): EventDecodeResult.Failure =
        if (field !in this) {
            EventDecodeResult.Failure(MahiroV1EntryError.MissingField(field))
        } else {
            EventDecodeResult.Failure(MahiroV1EntryError.InvalidFieldType(field))
        }

    private sealed interface EventDecodeResult {
        data class Success(val event: MahiroV1DoseEventDto) : EventDecodeResult
        data class Failure(val error: MahiroV1EntryError) : EventDecodeResult
    }
}
