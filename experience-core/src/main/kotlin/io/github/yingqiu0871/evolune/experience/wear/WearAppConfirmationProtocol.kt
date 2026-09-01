package io.github.yingqiu0871.evolune.experience.wear

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

const val WEAR_APP_COMMAND_PATH_PREFIX = "/hrt/v1/wear-app/commands/"
const val WEAR_APP_RESULT_PATH_PREFIX = "/hrt/v1/wear-app/results/"

enum class WearAppCommandType {
    CONFIRM_OCCURRENCE
}

data class WearAppSnapshotIdentity(
    val producerInstanceId: UUID,
    val producerGeneration: Long,
    val snapshotRevision: Long
)

data class WearAppConfirmCommand(
    val protocolVersion: Int,
    val commandType: WearAppCommandType,
    val operationId: UUID,
    val createdAt: Instant,
    val sourceSnapshot: WearAppSnapshotIdentity,
    val occurrenceId: UUID,
    val planId: UUID,
    val slotId: UUID,
    val localDate: LocalDate,
    val scheduledAt: Instant
)

object WearAppConfirmCommandRules {
    const val MAX_PAYLOAD_BYTES = 8 * 1024

    fun isValid(command: WearAppConfirmCommand): Boolean = runCatching {
        require(command.protocolVersion == WearAppProtocol.PROTOCOL_VERSION)
        require(command.commandType == WearAppCommandType.CONFIRM_OCCURRENCE)
        require(command.operationId.isNonZero())
        require(command.createdAt.toEpochMilli() > 0L)
        require(command.sourceSnapshot.producerInstanceId.isNonZero())
        require(command.sourceSnapshot.producerGeneration > 0L)
        require(command.sourceSnapshot.snapshotRevision > 0L)
        require(command.occurrenceId.isNonZero())
        require(command.planId.isNonZero())
        require(command.slotId.isNonZero())
        require(command.scheduledAt.toEpochMilli() > 0L)
    }.isSuccess
}

enum class WearAppConfirmResultType {
    CONFIRMED,
    ALREADY_CONFIRMED,
    REJECTED_INVALID,
    REJECTED_STALE_IDENTITY,
    REJECTED_OCCURRENCE_NOT_FOUND,
    REJECTED_PLAN_DISABLED,
    REJECTED_CONFLICT,
    RETRYABLE_STORAGE_FAILURE
}

enum class WearAppConfirmMessageCode {
    CONFIRMED,
    ALREADY_CONFIRMED,
    INVALID_COMMAND,
    STALE_IDENTITY,
    OCCURRENCE_NOT_FOUND,
    PLAN_DISABLED,
    CONFLICT,
    STORAGE_FAILURE
}

data class WearAppConfirmResult(
    val protocolVersion: Int,
    val operationId: UUID,
    val resultType: WearAppConfirmResultType,
    val eventId: UUID?,
    val occurrenceId: UUID,
    val processedAt: Instant,
    val messageCode: WearAppConfirmMessageCode?,
    val snapshotRefreshExpected: Boolean
)

object WearAppConfirmResultRules {
    const val MAX_PAYLOAD_BYTES = 4 * 1024

    fun isValid(result: WearAppConfirmResult): Boolean = runCatching {
        require(result.protocolVersion == WearAppProtocol.PROTOCOL_VERSION)
        require(result.operationId.isNonZero())
        require(result.occurrenceId.isNonZero())
        require(result.processedAt.toEpochMilli() > 0L)
        when (result.resultType) {
            WearAppConfirmResultType.CONFIRMED,
            WearAppConfirmResultType.ALREADY_CONFIRMED -> {
                require(result.eventId?.isNonZero() == true)
                require(
                    result.messageCode == when (result.resultType) {
                        WearAppConfirmResultType.CONFIRMED -> WearAppConfirmMessageCode.CONFIRMED
                        WearAppConfirmResultType.ALREADY_CONFIRMED -> WearAppConfirmMessageCode.ALREADY_CONFIRMED
                    }
                )
                require(result.snapshotRefreshExpected)
            }
            WearAppConfirmResultType.REJECTED_INVALID -> {
                require(result.eventId == null)
                require(result.messageCode == WearAppConfirmMessageCode.INVALID_COMMAND)
                require(!result.snapshotRefreshExpected)
            }
            WearAppConfirmResultType.REJECTED_STALE_IDENTITY -> {
                require(result.eventId == null)
                require(result.messageCode == WearAppConfirmMessageCode.STALE_IDENTITY)
                require(!result.snapshotRefreshExpected)
            }
            WearAppConfirmResultType.REJECTED_OCCURRENCE_NOT_FOUND -> {
                require(result.eventId == null)
                require(result.messageCode == WearAppConfirmMessageCode.OCCURRENCE_NOT_FOUND)
                require(!result.snapshotRefreshExpected)
            }
            WearAppConfirmResultType.REJECTED_PLAN_DISABLED -> {
                require(result.eventId == null)
                require(result.messageCode == WearAppConfirmMessageCode.PLAN_DISABLED)
                require(!result.snapshotRefreshExpected)
            }
            WearAppConfirmResultType.REJECTED_CONFLICT -> {
                require(result.eventId == null)
                require(result.messageCode == WearAppConfirmMessageCode.CONFLICT)
                require(!result.snapshotRefreshExpected)
            }
            WearAppConfirmResultType.RETRYABLE_STORAGE_FAILURE -> {
                require(result.eventId == null)
                require(result.messageCode == WearAppConfirmMessageCode.STORAGE_FAILURE)
                require(!result.snapshotRefreshExpected)
            }
        }
    }.isSuccess
}

object WearAppConfirmCommandCodec {
    private const val MAGIC = 0x45574343 // "EWCC"

    fun encode(command: WearAppConfirmCommand): ByteArray {
        require(WearAppConfirmCommandRules.isValid(command))
        return Wire.fields {
            int(1, command.protocolVersion)
            string(2, command.commandType.name)
            string(3, command.operationId.toString())
            long(4, command.createdAt.toEpochMilli())
            string(5, command.sourceSnapshot.producerInstanceId.toString())
            long(6, command.sourceSnapshot.producerGeneration)
            long(7, command.sourceSnapshot.snapshotRevision)
            string(8, command.occurrenceId.toString())
            string(9, command.planId.toString())
            string(10, command.slotId.toString())
            string(11, command.localDate.toString())
            long(12, command.scheduledAt.toEpochMilli())
        }.prependMagic(MAGIC)
            .also { require(it.size <= WearAppConfirmCommandRules.MAX_PAYLOAD_BYTES) }
    }

    fun decode(payload: ByteArray): WearAppConfirmCommand? = runCatching {
        require(payload.size <= WearAppConfirmCommandRules.MAX_PAYLOAD_BYTES)
        val fields = Wire.readFields(payload.removeMagic(MAGIC), payload.size)
        val command = WearAppConfirmCommand(
            protocolVersion = fields.required(1).readInt(),
            commandType = fields.required(2).readEnum(),
            operationId = fields.required(3).readUuid(),
            createdAt = Instant.ofEpochMilli(fields.required(4).readLong()),
            sourceSnapshot = WearAppSnapshotIdentity(
                producerInstanceId = fields.required(5).readUuid(),
                producerGeneration = fields.required(6).readLong(),
                snapshotRevision = fields.required(7).readLong()
            ),
            occurrenceId = fields.required(8).readUuid(),
            planId = fields.required(9).readUuid(),
            slotId = fields.required(10).readUuid(),
            localDate = fields.required(11).readLocalDate(),
            scheduledAt = Instant.ofEpochMilli(fields.required(12).readLong())
        )
        require(WearAppConfirmCommandRules.isValid(command))
        command
    }.getOrNull()
}

object WearAppConfirmResultCodec {
    private const val MAGIC = 0x45574352 // "EWCR"

    fun encode(result: WearAppConfirmResult): ByteArray {
        require(WearAppConfirmResultRules.isValid(result))
        return Wire.fields {
            int(1, result.protocolVersion)
            string(2, result.operationId.toString())
            string(3, result.resultType.name)
            result.eventId?.let { string(4, it.toString()) }
            string(5, result.occurrenceId.toString())
            long(6, result.processedAt.toEpochMilli())
            result.messageCode?.let { string(7, it.name) }
            bool(8, result.snapshotRefreshExpected)
        }.prependMagic(MAGIC)
            .also { require(it.size <= WearAppConfirmResultRules.MAX_PAYLOAD_BYTES) }
    }

    fun decode(payload: ByteArray): WearAppConfirmResult? = runCatching {
        require(payload.size <= WearAppConfirmResultRules.MAX_PAYLOAD_BYTES)
        val fields = Wire.readFields(payload.removeMagic(MAGIC), payload.size)
        val result = WearAppConfirmResult(
            protocolVersion = fields.required(1).readInt(),
            operationId = fields.required(2).readUuid(),
            resultType = fields.required(3).readEnum(),
            eventId = fields.optional(4)?.readUuid(),
            occurrenceId = fields.required(5).readUuid(),
            processedAt = Instant.ofEpochMilli(fields.required(6).readLong()),
            messageCode = fields.optional(7)?.readEnum(),
            snapshotRefreshExpected = fields.required(8).readBoolean()
        )
        require(WearAppConfirmResultRules.isValid(result))
        result
    }.getOrNull()
}

fun wearAppCommandPath(operationId: UUID): String {
    require(operationId.isNonZero())
    return WEAR_APP_COMMAND_PATH_PREFIX + operationId
}

fun wearAppResultPath(operationId: UUID): String {
    require(operationId.isNonZero())
    return WEAR_APP_RESULT_PATH_PREFIX + operationId
}

fun operationIdFromWearAppCommandPath(path: String): UUID? =
    operationIdFromPath(path, WEAR_APP_COMMAND_PATH_PREFIX)

fun operationIdFromWearAppResultPath(path: String): UUID? =
    operationIdFromPath(path, WEAR_APP_RESULT_PATH_PREFIX)

/** Undo uses the same operation-scoped DataItem paths with an independent payload key. */
fun wearAppUndoCommandPath(operationId: UUID): String = wearAppCommandPath(operationId)

fun wearAppUndoResultPath(operationId: UUID): String = wearAppResultPath(operationId)

fun operationIdFromWearAppUndoCommandPath(path: String): UUID? =
    operationIdFromWearAppCommandPath(path)

fun operationIdFromWearAppUndoResultPath(path: String): UUID? =
    operationIdFromWearAppResultPath(path)

private fun operationIdFromPath(path: String, prefix: String): UUID? = runCatching {
    require(path.startsWith(prefix))
    val value = path.removePrefix(prefix)
    require(value.isNotEmpty() && !value.contains('/'))
    UUID.fromString(value).also {
        require(it.isNonZero())
        require(it.toString() == value)
    }
}.getOrNull()

private fun UUID.isNonZero(): Boolean = this != UUID(0L, 0L)

private object Wire {
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

private fun ByteArray.readBoolean(): Boolean = DataInputStream(ByteArrayInputStream(this)).use { data ->
    data.readBoolean().also { require(data.available() == 0) }
}

private fun ByteArray.readUuid(): UUID = UUID.fromString(readString())

private fun ByteArray.readLocalDate(): LocalDate = LocalDate.parse(readString())

private fun ByteArray.readString(): String = StandardCharsets.UTF_8.newDecoder()
    .onMalformedInput(CodingErrorAction.REPORT)
    .onUnmappableCharacter(CodingErrorAction.REPORT)
    .decode(ByteBuffer.wrap(this))
    .toString()

private inline fun <reified T : Enum<T>> ByteArray.readEnum(): T =
    enumValues<T>().single { it.name == readString() }
