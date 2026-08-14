package io.github.yingqiu0871.evolune.data.migration

import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.core.model.SlotIdResult
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.UUID

data class ParsedLegacyPlanTimes(
    val planId: UUID,
    val rawTimeOfDay: String,
    val entries: List<ParsedLegacyPlanTime>
)

data class ParsedLegacyPlanTime(
    val position: Int,
    val originalValue: String,
    val parsedLocalTime: LocalTime,
    val canonicalLocalTime: String,
    val slotId: UUID
)

object LegacyPlanTimeParser {
    fun parse(
        planId: UUID,
        rawTimeOfDay: String
    ): LegacyMigrationResult<ParsedLegacyPlanTimes> {
        if (rawTimeOfDay.isEmpty()) {
            return success(planId, rawTimeOfDay, emptyList())
        }

        val root = try {
            Json.parseToJsonElement(rawTimeOfDay)
        } catch (_: SerializationException) {
            return invalidJson(planId, rawTimeOfDay, TimeOfDayJsonFailure.MALFORMED)
        } catch (_: IllegalArgumentException) {
            return invalidJson(planId, rawTimeOfDay, TimeOfDayJsonFailure.MALFORMED)
        }

        if (root !is JsonArray) {
            return invalidJson(planId, rawTimeOfDay, TimeOfDayJsonFailure.ROOT_NOT_ARRAY)
        }

        val entries = ArrayList<ParsedLegacyPlanTime>(root.size)
        root.forEachIndexed { position, element ->
            if (element !is JsonPrimitive || !element.isString) {
                return LegacyMigrationResult.Failure(
                    LegacyMigrationError.TimeOfDayElementNotString(
                        planId = planId,
                        position = position,
                        rawValue = element.toString(),
                        elementKind = element.kind()
                    )
                )
            }

            val originalValue = element.content
            val parsedLocalTime = try {
                LocalTime.parse(originalValue)
            } catch (_: DateTimeParseException) {
                return LegacyMigrationResult.Failure(
                    LegacyMigrationError.InvalidLocalTime(
                        planId = planId,
                        position = position,
                        originalValue = originalValue
                    )
                )
            }

            if (parsedLocalTime.second != 0 || parsedLocalTime.nano != 0) {
                return LegacyMigrationResult.Failure(
                    LegacyMigrationError.NonMinuteLocalTime(
                        planId = planId,
                        position = position,
                        originalValue = originalValue,
                        parsedLocalTime = parsedLocalTime
                    )
                )
            }

            val slotId = when (
                val result = ScheduledDoseSlotId.generate(planId, position, parsedLocalTime)
            ) {
                is SlotIdResult.Success -> result.id
                is SlotIdResult.Failure -> {
                    return LegacyMigrationResult.Failure(
                        LegacyMigrationError.SlotIdGenerationFailed(
                            planId = planId,
                            position = position,
                            originalValue = originalValue,
                            cause = result.error
                        )
                    )
                }
            }

            entries += ParsedLegacyPlanTime(
                position = position,
                originalValue = originalValue,
                parsedLocalTime = parsedLocalTime,
                canonicalLocalTime = parsedLocalTime.toString(),
                slotId = slotId
            )
        }

        return success(planId, rawTimeOfDay, entries)
    }

    private fun success(
        planId: UUID,
        rawTimeOfDay: String,
        entries: List<ParsedLegacyPlanTime>
    ): LegacyMigrationResult.Success<ParsedLegacyPlanTimes> =
        LegacyMigrationResult.Success(
            ParsedLegacyPlanTimes(
                planId = planId,
                rawTimeOfDay = rawTimeOfDay,
                entries = entries
            )
        )

    private fun invalidJson(
        planId: UUID,
        rawTimeOfDay: String,
        reason: TimeOfDayJsonFailure
    ): LegacyMigrationResult.Failure = LegacyMigrationResult.Failure(
        LegacyMigrationError.InvalidTimeOfDayJson(
            planId = planId,
            rawTimeOfDay = rawTimeOfDay,
            reason = reason
        )
    )

    private fun JsonElement.kind(): JsonElementKind = when (this) {
        JsonNull -> JsonElementKind.NULL
        is JsonArray -> JsonElementKind.ARRAY
        is JsonObject -> JsonElementKind.OBJECT
        is JsonPrimitive -> when {
            isString -> JsonElementKind.STRING
            booleanOrNull != null -> JsonElementKind.BOOLEAN
            else -> JsonElementKind.NUMBER
        }
    }
}
