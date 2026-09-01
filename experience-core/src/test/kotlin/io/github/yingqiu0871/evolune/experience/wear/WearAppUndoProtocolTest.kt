package io.github.yingqiu0871.evolune.experience.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Base64
import java.util.UUID

class WearAppUndoProtocolTest {
    private val operationId = UUID.fromString("00000000-0000-0000-0000-000000000201")
    private val eventId = UUID.fromString("00000000-0000-0000-0000-000000000202")
    private val producerId = UUID.fromString("00000000-0000-0000-0000-000000000203")
    private val occurrenceId = UUID.fromString("00000000-0000-0000-0000-000000000204")
    private val planId = UUID.fromString("00000000-0000-0000-0000-000000000205")
    private val slotId = UUID.fromString("00000000-0000-0000-0000-000000000206")
    private val createdAt = Instant.parse("2026-08-30T10:00:00Z")

    private val command = WearAppUndoCommand(
        protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
        commandType = WearAppUndoCommandType.UNDO_RECENT_DOSE,
        operationId = operationId,
        createdAt = createdAt,
        sourceSnapshot = WearAppSnapshotIdentity(producerId, 3L, 17L),
        eventId = eventId,
        expectedEventRevision = 4L,
        expectedOccurredAt = createdAt.minusSeconds(60L),
        expectedSource = "WEAR"
    )

    private val undone = WearAppUndoResult(
        protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
        operationId = operationId,
        resultType = WearAppUndoResultType.UNDONE,
        eventId = eventId,
        processedAt = createdAt.plusSeconds(1L),
        messageCode = WearAppUndoMessageCode.UNDONE,
        snapshotRefreshExpected = true
    )

    @Test
    fun `command and result round trip with independent codecs`() {
        assertEquals(command, WearAppUndoCommandCodec.decode(WearAppUndoCommandCodec.encode(command)))
        assertEquals(undone, WearAppUndoResultCodec.decode(WearAppUndoResultCodec.encode(undone)))
        assertNull(WearAppUndoCommandCodec.decode(WearAppConfirmCommandCodec.encode(confirmCommand())))
        assertNull(WearAppConfirmCommandCodec.decode(WearAppUndoCommandCodec.encode(command)))
    }

    @Test
    fun `command encoding remains stable for the golden fixture`() {
        assertEquals(
            GOLDEN_COMMAND_BASE64,
            Base64.getEncoder().encodeToString(WearAppUndoCommandCodec.encode(command))
        )
    }

    @Test
    fun `unknown fields are ignored and duplicate known fields reject`() {
        val commandPayload = WearAppUndoCommandCodec.encode(command)
        val resultPayload = WearAppUndoResultCodec.encode(undone)

        assertNotNull(WearAppUndoCommandCodec.decode(appendField(commandPayload, 999, byteArrayOf(1))))
        assertNull(WearAppUndoCommandCodec.decode(appendField(commandPayload, 1, intBytes(1))))
        assertNotNull(WearAppUndoResultCodec.decode(appendField(resultPayload, 999, byteArrayOf(1))))
        assertNull(WearAppUndoResultCodec.decode(appendField(resultPayload, 1, intBytes(1))))
    }

    @Test
    fun `malformed known utf8 and invalid identity values reject`() {
        assertNull(
            WearAppUndoCommandCodec.decode(
                replaceField(commandPayload(), 2, byteArrayOf(0xC3.toByte(), 0x28))
            )
        )
        assertFalse(WearAppUndoCommandRules.isValid(command.copy(operationId = UUID(0L, 0L))))
        assertFalse(WearAppUndoCommandRules.isValid(command.copy(expectedEventRevision = 0L)))
        assertFalse(WearAppUndoCommandRules.isValid(command.copy(expectedOccurredAt = Instant.EPOCH)))
        assertFalse(WearAppUndoCommandRules.isValid(command.copy(createdAt = Instant.ofEpochMilli(-1L))))
        assertNull(WearAppUndoCommandCodec.decode(ByteArray(WearAppUndoCommandRules.MAX_PAYLOAD_BYTES + 1)))
        assertFalse(WearAppUndoResultRules.isValid(undone.copy(eventId = null)))
        assertFalse(WearAppUndoResultRules.isValid(undone.copy(snapshotRefreshExpected = false)))
    }

    @Test
    fun `result types enforce exact payload meaning`() {
        val rejectionTypes = listOf(
            WearAppUndoResultType.REJECTED_INVALID to WearAppUndoMessageCode.INVALID_COMMAND,
            WearAppUndoResultType.REJECTED_STALE_IDENTITY to WearAppUndoMessageCode.STALE_IDENTITY,
            WearAppUndoResultType.REJECTED_EVENT_NOT_FOUND to WearAppUndoMessageCode.EVENT_NOT_FOUND,
            WearAppUndoResultType.REJECTED_EVENT_CHANGED to WearAppUndoMessageCode.EVENT_CHANGED,
            WearAppUndoResultType.REJECTED_NOT_LATEST to WearAppUndoMessageCode.NOT_LATEST,
            WearAppUndoResultType.REJECTED_CONFLICT to WearAppUndoMessageCode.CONFLICT,
            WearAppUndoResultType.RETRYABLE_STORAGE_FAILURE to WearAppUndoMessageCode.STORAGE_FAILURE
        )

        rejectionTypes.forEach { (type, messageCode) ->
            val result = undone.copy(
                resultType = type,
                eventId = null,
                messageCode = messageCode,
                snapshotRefreshExpected = false
            )
            assertTrue(type.name, WearAppUndoResultRules.isValid(result))
            assertEquals(result, WearAppUndoResultCodec.decode(WearAppUndoResultCodec.encode(result)))
        }
        assertTrue(WearAppUndoCommandRules.isValid(command))
    }

    @Test
    fun `undo paths bind a canonical operation id`() {
        assertEquals(
            operationId,
            operationIdFromWearAppUndoCommandPath(wearAppUndoCommandPath(operationId))
        )
        assertEquals(
            operationId,
            operationIdFromWearAppUndoResultPath(wearAppUndoResultPath(operationId))
        )
        assertNull(operationIdFromWearAppUndoCommandPath("${wearAppUndoCommandPath(operationId)}/extra"))
        assertEquals(operationId, operationIdFromWearAppResultPath(wearAppUndoResultPath(operationId)))
    }

    private fun commandPayload(): ByteArray = WearAppUndoCommandCodec.encode(command)

    private fun confirmCommand() = WearAppConfirmCommand(
        protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
        commandType = WearAppCommandType.CONFIRM_OCCURRENCE,
        operationId = operationId,
        createdAt = createdAt,
        sourceSnapshot = WearAppSnapshotIdentity(producerId, 3L, 17L),
        occurrenceId = occurrenceId,
        planId = planId,
        slotId = slotId,
        localDate = java.time.LocalDate.of(2026, 8, 30),
        scheduledAt = createdAt
    )

    private fun appendField(payload: ByteArray, tag: Int, value: ByteArray): ByteArray =
        ByteArrayOutputStream().use { output ->
            output.write(payload)
            DataOutputStream(output).use { data ->
                data.writeInt(tag)
                data.writeInt(value.size)
                data.write(value)
            }
            output.toByteArray()
        }

    private fun replaceField(payload: ByteArray, tagToReplace: Int, replacement: ByteArray): ByteArray {
        val input = ByteBuffer.wrap(payload)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(input.int)
            while (input.hasRemaining()) {
                val tag = input.int
                val size = input.int
                val value = ByteArray(size).also(input::get)
                data.writeInt(tag)
                data.writeInt(if (tag == tagToReplace) replacement.size else size)
                data.write(if (tag == tagToReplace) replacement else value)
            }
        }
        return output.toByteArray()
    }

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()

    private companion object {
        const val GOLDEN_COMMAND_BASE64 =
            "RVdVQwAAAAEAAAAEAAAAAQAAAAIAAAAQVU5ET19SRUNFTlRfRE9TRQAAAAMAAAAkMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMjAxAAAABAAAAAgAAAGgUhyVAAAAAAUAAAAkMDAwMDAwMDAtMDAwMC0wMDAwLTAwMDAtMDAwMDAwMDAwMjAzAAAABgAAAAgAAAAAAAAAAwAAAAcAAAAIAAAAAAAAABEAAAAIAAAAJDAwMDAwMDAwLTAwMDAtMDAwMC0wMDAwLTAwMDAwMDAwMDIwMgAAAAkAAAAIAAAAAAAAAAQAAAAKAAAACAAAAaBSG6qgAAAACwAAAARXRUFS"
    }
}
