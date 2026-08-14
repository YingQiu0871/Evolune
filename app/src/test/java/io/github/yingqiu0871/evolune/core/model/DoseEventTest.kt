package io.github.yingqiu0871.evolune.core.model

import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.util.TimeZone
import java.util.UUID

class DoseEventTest {
    @Test
    fun revisionOneIsValidAndLegacyMetadataRemainsNull() {
        val event = syntheticEvent(revision = 1)

        assertEquals(1, event.revision)
        assertEquals(DoseEventStatus.RECORDED, event.status)
        assertNull(event.zoneId)
        assertNull(event.localDate)
        assertNull(event.slotId)
    }

    @Test
    fun revisionZeroIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            syntheticEvent(revision = 0)
        }
    }

    @Test
    fun negativeRevisionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            syntheticEvent(revision = -1)
        }
    }

    @Test
    fun occurredAtAndLegacyMetadataDoNotDependOnDefaultTimeZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val first = syntheticEvent()
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val second = syntheticEvent()

            assertEquals(first.occurredAt, second.occurredAt)
            assertNull(first.zoneId)
            assertNull(second.zoneId)
            assertNull(first.localDate)
            assertNull(second.localDate)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun phaseOneEnumsContainOnlyResolvedValues() {
        assertEquals(
            listOf("LEGACY", "MANUAL", "JSON_V1", "REMINDER", "WIDGET", "WEAR"),
            DoseEventSource.entries.map { it.name }
        )
        assertEquals(listOf("RECORDED"), DoseEventStatus.entries.map { it.name })
    }

    private fun syntheticEvent(revision: Long = 1): DoseEvent = DoseEvent(
        id = UUID.fromString("10000000-0000-0000-0000-000000000001"),
        route = Route.SUBLINGUAL,
        occurredAt = Instant.parse("2024-01-15T12:34:56Z"),
        doseMG = 1.5,
        ester = Ester.E2,
        extras = mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0),
        source = DoseEventSource.LEGACY,
        revision = revision
    )
}
