package io.github.yingqiu0871.evolune.experience.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WearAppConfirmationProtocolTest {
    private val operationId = UUID.fromString("00000000-0000-0000-0000-000000000101")
    private val occurrenceId = UUID.fromString("00000000-0000-0000-0000-000000000102")
    private val planId = UUID.fromString("00000000-0000-0000-0000-000000000103")
    private val slotId = UUID.fromString("00000000-0000-0000-0000-000000000104")
    private val producerId = UUID.fromString("00000000-0000-0000-0000-000000000105")

    private val command = WearAppConfirmCommand(
        protocolVersion = WearAppProtocol.PROTOCOL_VERSION,
        commandType = WearAppCommandType.CONFIRM_OCCURRENCE,
        operationId = operationId,
        createdAt = Instant.ofEpochMilli(1_800_000_000_000L),
        sourceSnapshot = WearAppSnapshotIdentity(producerId, 4L, 19L),
        occurrenceId = occurrenceId,
        planId = planId,
        slotId = slotId,
        localDate = LocalDate.of(2026, 8, 30),
        scheduledAt = Instant.parse("2026-08-30T08:30:00Z")
    )

    @Test
    fun `command round trips and carries no authoritative presentation fields`() {
        val decoded = WearAppConfirmCommandCodec.decode(
            WearAppConfirmCommandCodec.encode(command)
        )

        assertEquals(command, decoded)
        assertTrue(WearAppConfirmCommandRules.isValid(command))
    }

    @Test
    fun `unknown field is ignored while duplicate known field rejects`() {
        val payload = WearAppConfirmCommandCodec.encode(command)
        assertNotNull(WearAppConfirmCommandCodec.decode(appendField(payload, 999, byteArrayOf(1))))
        assertNull(WearAppConfirmCommandCodec.decode(appendField(payload, 1, intBytes(1))))
    }

    @Test
    fun `unsupported and malformed commands are rejected`() {
        assertFalse(WearAppConfirmCommandRules.isValid(command.copy(operationId = UUID(0L, 0L))))
        assertNull(WearAppConfirmCommandCodec.decode(ByteArray(3)))
        assertNull(WearAppConfirmCommandCodec.decode(commandPayloadWithProtocol(2)))
    }

    @Test
    fun `paths are strict and bind operation id`() {
        assertEquals(operationId, operationIdFromWearAppCommandPath(wearAppCommandPath(operationId)))
        assertEquals(operationId, operationIdFromWearAppResultPath(wearAppResultPath(operationId)))
        assertNull(operationIdFromWearAppCommandPath("${wearAppCommandPath(operationId)}/extra"))
        assertNull(operationIdFromWearAppCommandPath("${wearAppCommandPath(operationId)}?x=1"))
        val uppercasePathId = UUID.fromString("00000000-0000-0000-0000-00000000010a")
        assertNull(operationIdFromWearAppCommandPath(
            "${WEAR_APP_COMMAND_PATH_PREFIX}${uppercasePathId.toString().uppercase()}"
        ))
        assertNull(operationIdFromWearAppResultPath("$WEAR_APP_RESULT_PATH_PREFIX$operationId/"))
    }

    @Test
    fun `success requires a real event id and rejects never carry one`() {
        val success = WearAppConfirmResult(
            protocolVersion = 1,
            operationId = operationId,
            resultType = WearAppConfirmResultType.CONFIRMED,
            eventId = UUID.fromString("00000000-0000-0000-0000-000000000106"),
            occurrenceId = occurrenceId,
            processedAt = Instant.ofEpochMilli(1_800_000_000_001L),
            messageCode = WearAppConfirmMessageCode.CONFIRMED,
            snapshotRefreshExpected = true
        )
        val rejected = success.copy(
            resultType = WearAppConfirmResultType.REJECTED_CONFLICT,
            eventId = null,
            messageCode = WearAppConfirmMessageCode.CONFLICT,
            snapshotRefreshExpected = false
        )

        assertEquals(success, WearAppConfirmResultCodec.decode(WearAppConfirmResultCodec.encode(success)))
        assertTrue(WearAppConfirmResultRules.isValid(rejected))
        assertFalse(WearAppConfirmResultRules.isValid(success.copy(eventId = null)))
        assertFalse(WearAppConfirmResultRules.isValid(rejected.copy(eventId = success.eventId)))
        assertFalse(WearAppConfirmResultRules.isValid(rejected.copy(snapshotRefreshExpected = true)))
        assertFalse(
            WearAppConfirmResultRules.isValid(
                success.copy(messageCode = WearAppConfirmMessageCode.ALREADY_CONFIRMED)
            )
        )
    }

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

    private fun intBytes(value: Int): ByteArray =
        ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { it.writeInt(value) }
            output.toByteArray()
        }

    private fun commandPayloadWithProtocol(protocolVersion: Int): ByteArray =
        replaceFirstFieldValue(
            WearAppConfirmCommandCodec.encode(command),
            intBytes(protocolVersion)
        )

    private fun replaceFirstFieldValue(payload: ByteArray, value: ByteArray): ByteArray {
        val result = payload.copyOf()
        // The first four bytes are the codec magic, followed by tag and size.
        System.arraycopy(value, 0, result, 12, value.size)
        return result
    }
}
