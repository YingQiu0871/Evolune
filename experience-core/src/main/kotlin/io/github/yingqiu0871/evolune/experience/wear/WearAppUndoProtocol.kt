package io.github.yingqiu0871.evolune.experience.wear

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

enum class WearAppUndoCommandType {
    UNDO_RECENT_DOSE
}

data class WearAppUndoCommand(
    val protocolVersion: Int,
    val commandType: WearAppUndoCommandType,
    val operationId: UUID,
    val createdAt: Instant,
    val sourceSnapshot: WearAppSnapshotIdentity,
    val eventId: UUID,
    val expectedEventRevision: Long,
    val expectedOccurredAt: Instant,
    val expectedSource: String
)

object WearAppUndoCommandRules {
    const val MAX_PAYLOAD_BYTES = 8 * 1024

    fun isValid(command: WearAppUndoCommand): Boolean = runCatching {
        require(command.protocolVersion == WearAppProtocol.PROTOCOL_VERSION)
        require(command.commandType == WearAppUndoCommandType.UNDO_RECENT_DOSE)
        require(command.operationId.isNonZero())
        require(command.createdAt.toEpochMillisOrNull()?.let { it > 0L } == true)
        require(command.sourceSnapshot.producerInstanceId.isNonZero())
        require(command.sourceSnapshot.producerGeneration > 0L)
        require(command.sourceSnapshot.snapshotRevision > 0L)
        require(command.eventId.isNonZero())
        require(command.expectedEventRevision > 0L)
        require(command.expectedOccurredAt.toEpochMillisOrNull()?.let { it > 0L } == true)
        require(command.expectedSource.isNotBlank())
        require(command.expectedSource.toByteArray(StandardCharsets.UTF_8).size <= 256)
    }.isSuccess
}

enum class WearAppUndoResultType {
    UNDONE,
    ALREADY_UNDONE,
    REJECTED_INVALID,
    REJECTED_STALE_IDENTITY,
    REJECTED_EVENT_NOT_FOUND,
    REJECTED_EVENT_CHANGED,
    REJECTED_NOT_LATEST,
    REJECTED_CONFLICT,
    RETRYABLE_STORAGE_FAILURE
}

enum class WearAppUndoMessageCode {
    UNDONE,
    ALREADY_UNDONE,
    INVALID_COMMAND,
    STALE_IDENTITY,
    EVENT_NOT_FOUND,
    EVENT_CHANGED,
    NOT_LATEST,
    CONFLICT,
    STORAGE_FAILURE
}

data class WearAppUndoResult(
    val protocolVersion: Int,
    val operationId: UUID,
    val resultType: WearAppUndoResultType,
    val eventId: UUID?,
    val processedAt: Instant,
    val messageCode: WearAppUndoMessageCode,
    val snapshotRefreshExpected: Boolean
)

object WearAppUndoResultRules {
    const val MAX_PAYLOAD_BYTES = 4 * 1024

    fun isValid(result: WearAppUndoResult): Boolean = runCatching {
        require(result.protocolVersion == WearAppProtocol.PROTOCOL_VERSION)
        require(result.operationId.isNonZero())
        require(result.processedAt.toEpochMillisOrNull()?.let { it > 0L } == true)
        when (result.resultType) {
            WearAppUndoResultType.UNDONE -> {
                require(result.eventId?.isNonZero() == true)
                require(result.messageCode == WearAppUndoMessageCode.UNDONE)
                require(result.snapshotRefreshExpected)
            }
            WearAppUndoResultType.ALREADY_UNDONE -> {
                require(result.eventId?.isNonZero() == true)
                require(result.messageCode == WearAppUndoMessageCode.ALREADY_UNDONE)
                require(result.snapshotRefreshExpected)
            }
            WearAppUndoResultType.REJECTED_INVALID ->
                requireRejection(result, WearAppUndoMessageCode.INVALID_COMMAND)
            WearAppUndoResultType.REJECTED_STALE_IDENTITY ->
                requireRejection(result, WearAppUndoMessageCode.STALE_IDENTITY)
            WearAppUndoResultType.REJECTED_EVENT_NOT_FOUND ->
                requireRejection(result, WearAppUndoMessageCode.EVENT_NOT_FOUND)
            WearAppUndoResultType.REJECTED_EVENT_CHANGED ->
                requireRejection(result, WearAppUndoMessageCode.EVENT_CHANGED)
            WearAppUndoResultType.REJECTED_NOT_LATEST ->
                requireRejection(result, WearAppUndoMessageCode.NOT_LATEST)
            WearAppUndoResultType.REJECTED_CONFLICT ->
                requireRejection(result, WearAppUndoMessageCode.CONFLICT)
            WearAppUndoResultType.RETRYABLE_STORAGE_FAILURE ->
                requireRejection(result, WearAppUndoMessageCode.STORAGE_FAILURE)
        }
    }.isSuccess

    private fun requireRejection(
        result: WearAppUndoResult,
        expectedMessage: WearAppUndoMessageCode
    ) {
        require(result.eventId == null)
        require(result.messageCode == expectedMessage)
        require(!result.snapshotRefreshExpected)
    }
}

object WearAppUndoCommandCodec {
    private const val MAGIC = 0x45575543 // "EWUC"

    fun encode(command: WearAppUndoCommand): ByteArray {
        require(WearAppUndoCommandRules.isValid(command))
        return UndoWire.fields {
            int(1, command.protocolVersion)
            string(2, command.commandType.name)
            string(3, command.operationId.toString())
            long(4, command.createdAt.toEpochMilli())
            string(5, command.sourceSnapshot.producerInstanceId.toString())
            long(6, command.sourceSnapshot.producerGeneration)
            long(7, command.sourceSnapshot.snapshotRevision)
            string(8, command.eventId.toString())
            long(9, command.expectedEventRevision)
            long(10, command.expectedOccurredAt.toEpochMilli())
            string(11, command.expectedSource)
        }.prependMagic(MAGIC).also { require(it.size <= WearAppUndoCommandRules.MAX_PAYLOAD_BYTES) }
    }

    fun decode(payload: ByteArray): WearAppUndoCommand? = runCatching {
        require(payload.size <= WearAppUndoCommandRules.MAX_PAYLOAD_BYTES)
        val fields = UndoWire.readFields(payload.removeMagic(MAGIC), payload.size)
        val command = WearAppUndoCommand(
            protocolVersion = fields.required(1).readInt(),
            commandType = fields.required(2).readEnum(),
            operationId = fields.required(3).readUuid(),
            createdAt = fields.required(4).readInstant(),
            sourceSnapshot = WearAppSnapshotIdentity(
                producerInstanceId = fields.required(5).readUuid(),
                producerGeneration = fields.required(6).readLong(),
                snapshotRevision = fields.required(7).readLong()
            ),
            eventId = fields.required(8).readUuid(),
            expectedEventRevision = fields.required(9).readLong(),
            expectedOccurredAt = fields.required(10).readInstant(),
            expectedSource = fields.required(11).readString()
        )
        require(WearAppUndoCommandRules.isValid(command))
        command
    }.getOrNull()
}

object WearAppUndoResultCodec {
    private const val MAGIC = 0x45575552 // "EWUR"

    fun encode(result: WearAppUndoResult): ByteArray {
        require(WearAppUndoResultRules.isValid(result))
        return UndoWire.fields {
            int(1, result.protocolVersion)
            string(2, result.operationId.toString())
            string(3, result.resultType.name)
            result.eventId?.let { string(4, it.toString()) }
            long(5, result.processedAt.toEpochMilli())
            string(6, result.messageCode.name)
            bool(7, result.snapshotRefreshExpected)
        }.prependMagic(MAGIC).also { require(it.size <= WearAppUndoResultRules.MAX_PAYLOAD_BYTES) }
    }

    fun decode(payload: ByteArray): WearAppUndoResult? = runCatching {
        require(payload.size <= WearAppUndoResultRules.MAX_PAYLOAD_BYTES)
        val fields = UndoWire.readFields(payload.removeMagic(MAGIC), payload.size)
        val result = WearAppUndoResult(
            protocolVersion = fields.required(1).readInt(),
            operationId = fields.required(2).readUuid(),
            resultType = fields.required(3).readEnum(),
            eventId = fields.optional(4)?.readUuid(),
            processedAt = fields.required(5).readInstant(),
            messageCode = fields.required(6).readEnum(),
            snapshotRefreshExpected = fields.required(7).readBoolean()
        )
        require(WearAppUndoResultRules.isValid(result))
        result
    }.getOrNull()
}

private object UndoWire {
    fun fields(block: Writer.() -> Unit): ByteArray = Writer().apply(block).toByteArray()

    fun readFields(bytes: ByteArray, payloadSize: Int): Fields {
        require(bytes.size <= payloadSize)
        val values = mutableMapOf<Int, MutableList<ByteArray>>()
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            while (data.available() > 0) {
                require(data.available() >= 8)
                val tag = data.readInt()
                val size = data.readInt()
                require(size in 0..payloadSize && size <= data.available())
                val value = ByteArray(size).also(data::readFully)
                values.getOrPut(tag) { mutableListOf() }.add(value)
            }
        }
        return Fields(values)
    }

    class Writer {
        private val output = ByteArrayOutputStream()
        private val data = DataOutputStream(output)

        fun int(tag: Int, value: Int) = write(tag) { writeInt(value) }
        fun long(tag: Int, value: Long) = write(tag) { writeLong(value) }
        fun bool(tag: Int, value: Boolean) = write(tag) { writeBoolean(value) }
        fun string(tag: Int, value: String) =
            write(tag) { write(value.toByteArray(StandardCharsets.UTF_8)) }

        fun toByteArray(): ByteArray = output.toByteArray()

        private fun write(tag: Int, block: DataOutputStream.() -> Unit) {
            require(tag > 0)
            val value = ByteArrayOutputStream().use { valueOutput ->
                DataOutputStream(valueOutput).use(block)
                valueOutput.toByteArray()
            }
            data.writeInt(tag)
            data.writeInt(value.size)
            data.write(value)
        }
    }

    class Fields(private val values: Map<Int, List<ByteArray>>) {
        fun required(tag: Int): ByteArray = values[tag].orEmpty().single()
        fun optional(tag: Int): ByteArray? = values[tag]?.also { require(it.size == 1) }?.single()
    }
}

private fun ByteArray.prependMagic(magic: Int): ByteArray =
    ByteArrayOutputStream().use { output ->
        DataOutputStream(output).use { data ->
            data.writeInt(magic)
            data.write(this)
        }
        output.toByteArray()
    }

private fun ByteArray.removeMagic(expected: Int): ByteArray =
    DataInputStream(ByteArrayInputStream(this)).use { data ->
        require(data.readInt() == expected)
        ByteArray(data.available()).also(data::readFully)
    }

private fun ByteArray.readInt(): Int = DataInputStream(ByteArrayInputStream(this)).use { data ->
    data.readInt().also { require(data.available() == 0) }
}

private fun ByteArray.readLong(): Long = DataInputStream(ByteArrayInputStream(this)).use { data ->
    data.readLong().also { require(data.available() == 0) }
}

private fun ByteArray.readBoolean(): Boolean =
    DataInputStream(ByteArrayInputStream(this)).use { data ->
        data.readBoolean().also { require(data.available() == 0) }
    }

private fun ByteArray.readUuid(): UUID = readString().let { value ->
    UUID.fromString(value).also {
        require(it.toString() == value && it.isNonZero())
    }
}

private fun ByteArray.readInstant(): Instant = Instant.ofEpochMilli(readLong())

private fun ByteArray.readString(): String = StandardCharsets.UTF_8.newDecoder()
    .onMalformedInput(CodingErrorAction.REPORT)
    .onUnmappableCharacter(CodingErrorAction.REPORT)
    .decode(ByteBuffer.wrap(this))
    .toString()

private inline fun <reified T : Enum<T>> ByteArray.readEnum(): T =
    enumValues<T>().single { it.name == readString() }

private fun UUID.isNonZero(): Boolean = this != UUID(0L, 0L)

private fun Instant.toEpochMillisOrNull(): Long? = runCatching { toEpochMilli() }.getOrNull()
