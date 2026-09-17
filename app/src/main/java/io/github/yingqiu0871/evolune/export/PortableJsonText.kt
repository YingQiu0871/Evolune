package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.core.model.ExtraKey
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Canonical textual rendering shared by Evolune Portable JSON v1 and CSV v1
 * (frozen contract §12/§15/§28/§29). Pure, locale-independent, no clock, no device zone.
 */
internal object PortableJsonText {

    private val INSTANT_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    private val INSTANT_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$")
    private val DATE_PATTERN = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val UUID_PATTERN = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    /** Canonical decimal text: locale-independent, round-trip-safe, `Double.toString` semantics. */
    fun canonicalNumber(value: Double): String = value.toString()

    /** Canonical UTC RFC3339 millisecond instant text, or null when not representable. */
    fun formatInstant(instant: Instant): String? {
        val epochMillis = try {
            instant.toEpochMilli()
        } catch (_: ArithmeticException) {
            return null
        }
        if (Instant.ofEpochMilli(epochMillis) != instant) return null
        val text = try {
            INSTANT_FORMATTER.format(instant)
        } catch (_: DateTimeException) {
            return null
        }
        return text.takeIf(INSTANT_PATTERN::matches)
    }

    /** Strict canonical instant parse: exactly 3 fractional digits, terminal `Z`, valid calendar. */
    fun parseInstant(text: String): Instant? {
        if (!INSTANT_PATTERN.matches(text)) return null
        return try {
            Instant.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun parseLocalDate(text: String): LocalDate? {
        if (!DATE_PATTERN.matches(text)) return null
        return try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun parseZoneId(text: String): ZoneId? = try {
        ZoneId.of(text)
    } catch (_: DateTimeException) {
        null
    }

    fun parseUuid(text: String): UUID? {
        if (!UUID_PATTERN.matches(text)) return null
        return try {
            UUID.fromString(text)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun stringLiteral(value: String): String {
        val builder = StringBuilder(value.length + 2)
        builder.append('"')
        for (character in value) {
            when (character) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                else -> if (character < ' ') {
                    builder.append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    builder.append(character)
                }
            }
        }
        builder.append('"')
        return builder.toString()
    }

    fun nullableString(value: String?): String =
        value?.let(::stringLiteral) ?: "null"

    /** Compact canonical extras object: keys lexical ascending, values canonical numeric text. */
    fun compactExtras(extras: Map<ExtraKey, Double>): String {
        if (extras.isEmpty()) return "{}"
        val sortedKeys = extras.keys.sortedBy { it.name }
        return buildString {
            append('{')
            sortedKeys.forEachIndexed { index, key ->
                if (index > 0) append(',')
                append(stringLiteral(key.name)).append(':').append(canonicalNumber(extras.getValue(key)))
            }
            append('}')
        }
    }
}
