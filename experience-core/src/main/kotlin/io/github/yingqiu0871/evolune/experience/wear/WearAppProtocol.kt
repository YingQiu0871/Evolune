package io.github.yingqiu0871.evolune.experience.wear

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Android-free contract for the read-only Wear Companion App snapshot.
 *
 * The existing legacy HRT transport is intentionally not represented here. This
 * contract is additive and is used only by the v1 Wear App paths.
 */
object WearAppProtocol {
    const val PROTOCOL_VERSION = 1
    const val SNAPSHOT_PATH = "/hrt/v1/wear-app/snapshot"
    const val REQUEST_PATH = "/hrt/v1/wear-app/request"
    const val KEY_PAYLOAD = "snapshot_payload"
    const val KEY_PROTOCOL_VERSION = "protocol_version"
}

enum class WearAppOverallStatus {
    READY,
    EMPTY
}

enum class WearAppOccurrenceStatus {
    UPCOMING,
    DUE
}

enum class WearAppConcentrationStatus {
    AVAILABLE,
    EMPTY,
    STALE,
    ERROR
}

data class WearAppRecentDose(
    val eventId: UUID,
    val planId: UUID?,
    val slotId: UUID?,
    val localDate: LocalDate?,
    val occurredAt: Instant,
    val medicationName: String,
    val route: String,
    val dose: Double,
    val doseUnit: String,
    val source: String
)

data class WearAppUpcomingOccurrence(
    val occurrenceId: UUID,
    val planId: UUID,
    val slotId: UUID,
    val localDate: LocalDate,
    val scheduledAt: Instant,
    val medicationName: String,
    val route: String,
    val dose: Double,
    val doseUnit: String,
    val status: WearAppOccurrenceStatus
)

data class WearAppConcentration(
    val status: WearAppConcentrationStatus,
    val value: Double? = null,
    val unit: String? = null,
    val calculatedAt: Instant? = null
)

data class WearAppProducerIdentity(
    val producerInstanceId: UUID,
    val producerGeneration: Long
)

object WearAppProducerIdentityRules {
    const val INITIAL_GENERATION = 1L

    fun isValid(identity: WearAppProducerIdentity): Boolean =
        identity.producerInstanceId != UUID(0L, 0L) && identity.producerGeneration > 0L
}

data class WearAppRequest(
    val protocolVersion: Int,
    val requestId: UUID,
    val observedProducerInstanceId: UUID?,
    val observedProducerGeneration: Long?,
    val observedSnapshotRevision: Long?,
    val requestedAt: Instant
)

object WearAppRequestRules {
    const val MAX_PAYLOAD_BYTES = 4 * 1024

    fun isValid(request: WearAppRequest): Boolean = runCatching {
        require(request.protocolVersion == WearAppProtocol.PROTOCOL_VERSION)
        require(request.requestId != UUID(0L, 0L))
        require(request.requestedAt.toEpochMilli() > 0L)
        require((request.observedProducerInstanceId == null) ==
            (request.observedProducerGeneration == null))
        request.observedProducerInstanceId?.let { instanceId ->
            require(instanceId != UUID(0L, 0L))
            require(request.observedProducerGeneration!! > 0L)
            request.observedSnapshotRevision?.let { require(it > 0L) }
        } ?: require(request.observedSnapshotRevision == null)
    }.isSuccess
}

sealed interface WearAppProducerNegotiationResult {
    data class Accepted(val identity: WearAppProducerIdentity) : WearAppProducerNegotiationResult
    data object InvalidObservedProducer : WearAppProducerNegotiationResult
    data object GenerationExhausted : WearAppProducerNegotiationResult
}

fun negotiateWearAppProducerIdentity(
    current: WearAppProducerIdentity,
    observedProducerInstanceId: UUID?,
    observedProducerGeneration: Long?
): WearAppProducerNegotiationResult {
    if (!WearAppProducerIdentityRules.isValid(current)) {
        return WearAppProducerNegotiationResult.InvalidObservedProducer
    }
    if ((observedProducerInstanceId == null) != (observedProducerGeneration == null)) {
        return WearAppProducerNegotiationResult.InvalidObservedProducer
    }
    if (observedProducerInstanceId == null) {
        return WearAppProducerNegotiationResult.Accepted(current)
    }
    if (observedProducerInstanceId == UUID(0L, 0L) || observedProducerGeneration!! <= 0L) {
        return WearAppProducerNegotiationResult.InvalidObservedProducer
    }
    if (
        observedProducerInstanceId == current.producerInstanceId ||
        observedProducerGeneration < current.producerGeneration
    ) {
        return WearAppProducerNegotiationResult.Accepted(current)
    }
    if (observedProducerGeneration == Long.MAX_VALUE) {
        return WearAppProducerNegotiationResult.GenerationExhausted
    }
    return WearAppProducerNegotiationResult.Accepted(
        current.copy(producerGeneration = observedProducerGeneration + 1L)
    )
}

data class WearAppSnapshot(
    val protocolVersion: Int,
    val snapshotRevision: Long,
    val generatedAt: Instant,
    val zoneId: String,
    val overallStatus: WearAppOverallStatus,
    val recentDose: WearAppRecentDose?,
    val upcomingOccurrences: List<WearAppUpcomingOccurrence>,
    val concentrationState: WearAppConcentration,
    /**
     * Identity of the Phone producer instance. This is intentionally part of
     * the transport contract, but is not rendered by either app.
     */
    val producerInstanceId: UUID,
    /** Monotonic generation assigned when the producer instance is created. */
    val producerGeneration: Long
)

object WearAppSnapshotRules {
    const val DOSE_UNIT_MILLIGRAM = "mg"
    const val CONCENTRATION_UNIT_PG_ML = "pg/mL"
    const val MAX_UPCOMING_OCCURRENCES = 5

    private val upcomingOrder = compareBy<WearAppUpcomingOccurrence>(
        { it.scheduledAt },
        { it.occurrenceId.toString() }
    )

    fun sortUpcoming(
        occurrences: Iterable<WearAppUpcomingOccurrence>
    ): List<WearAppUpcomingOccurrence> = occurrences.sortedWith(upcomingOrder)

    /**
     * Orders producer generations independently from snapshot revisions. The
     * producer id tie-break makes simultaneous generations deterministic.
     */
    fun compareProducers(left: WearAppSnapshot, right: WearAppSnapshot): Int =
        compareValues(left.producerGeneration, right.producerGeneration).takeIf { it != 0 }
            ?: left.producerInstanceId.toString().compareTo(right.producerInstanceId.toString())

    fun isValid(snapshot: WearAppSnapshot): Boolean = runCatching {
        require(snapshot.protocolVersion == WearAppProtocol.PROTOCOL_VERSION)
        require(snapshot.snapshotRevision > 0L)
        require(WearAppProducerIdentityRules.isValid(
            WearAppProducerIdentity(snapshot.producerInstanceId, snapshot.producerGeneration)
        ))
        require(snapshot.generatedAt.toEpochMilli() > 0L)
        ZoneId.of(snapshot.zoneId)
        require(snapshot.upcomingOccurrences.size <= MAX_UPCOMING_OCCURRENCES)
        require(snapshot.upcomingOccurrences == sortUpcoming(snapshot.upcomingOccurrences))
        require(snapshot.upcomingOccurrences.map { it.occurrenceId }.toSet().size ==
            snapshot.upcomingOccurrences.size)
        snapshot.recentDose?.let(::validateRecent)
        snapshot.upcomingOccurrences.forEach(::validateUpcoming)
        validateConcentration(snapshot.concentrationState)
    }.isSuccess

    private fun validateRecent(recent: WearAppRecentDose) {
        require(recent.occurredAt.toEpochMilli() > 0L)
        require(recent.medicationName.isNotBlank())
        require(recent.route.isNotBlank())
        require(recent.dose.isFinite() && recent.dose >= 0.0)
        require(recent.doseUnit == DOSE_UNIT_MILLIGRAM)
        require(recent.source.isNotBlank())
    }

    private fun validateUpcoming(occurrence: WearAppUpcomingOccurrence) {
        require(occurrence.scheduledAt.toEpochMilli() > 0L)
        require(occurrence.medicationName.isNotBlank())
        require(occurrence.route.isNotBlank())
        require(occurrence.dose.isFinite() && occurrence.dose >= 0.0)
        require(occurrence.doseUnit == DOSE_UNIT_MILLIGRAM)
    }

    private fun validateConcentration(concentration: WearAppConcentration) {
        concentration.value?.let { value ->
            require(value.isFinite() && value >= 0.0)
            require(concentration.unit == CONCENTRATION_UNIT_PG_ML)
        }
        concentration.calculatedAt?.let { require(it.toEpochMilli() > 0L) }
        when (concentration.status) {
            WearAppConcentrationStatus.AVAILABLE -> {
                require(concentration.value != null)
                require(concentration.unit == CONCENTRATION_UNIT_PG_ML)
            }
            WearAppConcentrationStatus.EMPTY,
            WearAppConcentrationStatus.ERROR -> require(concentration.value == null)
            WearAppConcentrationStatus.STALE -> Unit
        }
    }
}

/**
 * Small tagged-field codec shared by Phone and Wear. It uses only the JDK so
 * the existing module dependency graph remains unchanged. Unknown fields are
 * skipped, while malformed known fields reject the complete snapshot.
 */
object WearAppSnapshotCodec {
    private const val MAGIC = 0x45574150 // "EWAP"
    private const val MAX_PAYLOAD_BYTES = 256 * 1024

    fun encode(snapshot: WearAppSnapshot): ByteArray {
        require(WearAppSnapshotRules.isValid(snapshot))
        return fields {
            int(1, snapshot.protocolVersion)
            long(2, snapshot.snapshotRevision)
            long(3, snapshot.generatedAt.toEpochMilli())
            string(4, snapshot.zoneId)
            string(5, snapshot.overallStatus.name)
            snapshot.recentDose?.let { bytes(6, encodeRecent(it)) }
            bytes(7, encodeUpcoming(snapshot.upcomingOccurrences))
            bytes(8, encodeConcentration(snapshot.concentrationState))
            string(9, snapshot.producerInstanceId.toString())
            long(10, snapshot.producerGeneration)
        }.prependMagic()
    }

    fun decode(payload: ByteArray): WearAppSnapshot? = runCatching {
        require(payload.size <= MAX_PAYLOAD_BYTES)
        val top = readFields(payload.removeMagic())
        val protocolVersion = top.required(1).readInt()
        require(protocolVersion == WearAppProtocol.PROTOCOL_VERSION)
        val snapshot = WearAppSnapshot(
            protocolVersion = protocolVersion,
            snapshotRevision = top.required(2).readLong(),
            generatedAt = Instant.ofEpochMilli(top.required(3).readLong()),
            zoneId = top.required(4).readString(),
            overallStatus = enumValue(top.required(5).readString()),
            recentDose = top.optional(6)?.let(::decodeRecent),
            upcomingOccurrences = decodeUpcoming(top.required(7)),
            concentrationState = decodeConcentration(top.required(8)),
            producerInstanceId = UUID.fromString(top.required(9).readString()),
            producerGeneration = top.required(10).readLong()
        )
        require(WearAppSnapshotRules.isValid(snapshot))
        snapshot
    }.getOrNull()

    private fun encodeRecent(recent: WearAppRecentDose): ByteArray = fields {
        string(1, recent.eventId.toString())
        recent.planId?.let { string(2, it.toString()) }
        recent.slotId?.let { string(3, it.toString()) }
        recent.localDate?.let { string(4, it.toString()) }
        long(5, recent.occurredAt.toEpochMilli())
        string(6, recent.medicationName)
        string(7, recent.route)
        double(8, recent.dose)
        string(9, recent.doseUnit)
        string(10, recent.source)
    }

    private fun decodeRecent(bytes: ByteArray): WearAppRecentDose {
        val fields = readFields(bytes)
        return WearAppRecentDose(
            eventId = UUID.fromString(fields.required(1).readString()),
            planId = fields.optional(2)?.readString()?.let(UUID::fromString),
            slotId = fields.optional(3)?.readString()?.let(UUID::fromString),
            localDate = fields.optional(4)?.readString()?.let(LocalDate::parse),
            occurredAt = Instant.ofEpochMilli(fields.required(5).readLong()),
            medicationName = fields.required(6).readString(),
            route = fields.required(7).readString(),
            dose = fields.required(8).readDouble(),
            doseUnit = fields.required(9).readString(),
            source = fields.required(10).readString()
        )
    }

    private fun encodeUpcoming(
        occurrences: List<WearAppUpcomingOccurrence>
    ): ByteArray = listBytes(occurrences.map { occurrence ->
        fields {
            string(1, occurrence.occurrenceId.toString())
            string(2, occurrence.planId.toString())
            string(3, occurrence.slotId.toString())
            string(4, occurrence.localDate.toString())
            long(5, occurrence.scheduledAt.toEpochMilli())
            string(6, occurrence.medicationName)
            string(7, occurrence.route)
            double(8, occurrence.dose)
            string(9, occurrence.doseUnit)
            string(10, occurrence.status.name)
        }
    })

    private fun decodeUpcoming(bytes: ByteArray): List<WearAppUpcomingOccurrence> =
        readList(bytes).map { item ->
            val fields = readFields(item)
            WearAppUpcomingOccurrence(
                occurrenceId = UUID.fromString(fields.required(1).readString()),
                planId = UUID.fromString(fields.required(2).readString()),
                slotId = UUID.fromString(fields.required(3).readString()),
                localDate = LocalDate.parse(fields.required(4).readString()),
                scheduledAt = Instant.ofEpochMilli(fields.required(5).readLong()),
                medicationName = fields.required(6).readString(),
                route = fields.required(7).readString(),
                dose = fields.required(8).readDouble(),
                doseUnit = fields.required(9).readString(),
                status = enumValue(fields.required(10).readString())
            )
        }

    private fun encodeConcentration(
        concentration: WearAppConcentration
    ): ByteArray = fields {
        string(1, concentration.status.name)
        concentration.value?.let { double(2, it) }
        concentration.unit?.let { string(3, it) }
        concentration.calculatedAt?.let { long(4, it.toEpochMilli()) }
    }

    private fun decodeConcentration(bytes: ByteArray): WearAppConcentration {
        val fields = readFields(bytes)
        return WearAppConcentration(
            status = enumValue(fields.required(1).readString()),
            value = fields.optional(2)?.readDouble(),
            unit = fields.optional(3)?.readString(),
            calculatedAt = fields.optional(4)?.readLong()?.let(Instant::ofEpochMilli)
        )
    }

    private fun fields(block: FieldWriter.() -> Unit): ByteArray =
        FieldWriter().apply(block).toByteArray()

    private fun listBytes(items: List<ByteArray>): ByteArray =
        ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(items.size)
                items.forEach { item ->
                    data.writeInt(item.size)
                    data.write(item)
                }
            }
            output.toByteArray()
        }

    private fun readList(bytes: ByteArray): List<ByteArray> =
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            val count = data.readInt()
            require(count in 0..WearAppSnapshotRules.MAX_UPCOMING_OCCURRENCES)
            buildList(count) {
                repeat(count) {
                    val size = data.readInt()
                    require(size in 0..MAX_PAYLOAD_BYTES && size <= data.available())
                    add(ByteArray(size).also(data::readFully))
                }
                require(data.available() == 0)
            }
        }

    private fun ByteArray.prependMagic(): ByteArray =
        ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(MAGIC)
                data.write(this)
            }
            output.toByteArray()
        }

    private fun ByteArray.removeMagic(): ByteArray =
        DataInputStream(ByteArrayInputStream(this)).use { data ->
            require(data.readInt() == MAGIC)
            ByteArray(data.available()).also(data::readFully)
        }

    private class FieldWriter {
        private val output = ByteArrayOutputStream()
        private val data = DataOutputStream(output)

        fun int(tag: Int, value: Int) = write(tag) { writeInt(value) }
        fun long(tag: Int, value: Long) = write(tag) { writeLong(value) }
        fun double(tag: Int, value: Double) = write(tag) { writeDouble(value) }
        fun string(tag: Int, value: String) =
            write(tag) { write(value.toByteArray(StandardCharsets.UTF_8)) }
        fun bytes(tag: Int, value: ByteArray) = write(tag) { write(value) }

        fun toByteArray(): ByteArray = output.toByteArray()

        private fun write(tag: Int, block: DataOutputStream.() -> Unit) {
            val value = ByteArrayOutputStream().use { valueOutput ->
                DataOutputStream(valueOutput).use(block)
                valueOutput.toByteArray()
            }
            data.writeInt(tag)
            data.writeInt(value.size)
            data.write(value)
        }
    }

    private class FieldSet(private val values: Map<Int, List<ByteArray>>) {
        fun required(tag: Int): ByteArray = values[tag].orEmpty().single()
        fun optional(tag: Int): ByteArray? = values[tag]?.also { require(it.size == 1) }?.single()
    }

    private fun readFields(bytes: ByteArray): FieldSet {
        require(bytes.size <= MAX_PAYLOAD_BYTES)
        val values = mutableMapOf<Int, MutableList<ByteArray>>()
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            while (data.available() > 0) {
                require(data.available() >= 8)
                val tag = data.readInt()
                val size = data.readInt()
                require(size in 0..MAX_PAYLOAD_BYTES && size <= data.available())
                val value = ByteArray(size).also(data::readFully)
                values.getOrPut(tag) { mutableListOf() }.add(value)
            }
        }
        return FieldSet(values)
    }

    private fun ByteArray.readInt(): Int =
        DataInputStream(ByteArrayInputStream(this)).use { data ->
            data.readInt().also { require(data.available() == 0) }
        }

    private fun ByteArray.readLong(): Long =
        DataInputStream(ByteArrayInputStream(this)).use { data ->
            data.readLong().also { require(data.available() == 0) }
        }

    private fun ByteArray.readDouble(): Double =
        DataInputStream(ByteArrayInputStream(this)).use { data ->
            data.readDouble().also { require(data.available() == 0) }
        }

    private fun ByteArray.readString(): String =
        toString(StandardCharsets.UTF_8)

    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        enumValues<T>().single { it.name == value }
}

/** Versioned request codec shared by Phone and Wear without Android dependencies. */
object WearAppRequestCodec {
    private const val MAGIC = 0x45574152 // "EWAR"

    fun encode(request: WearAppRequest): ByteArray {
        require(WearAppRequestRules.isValid(request))
        return fields {
            int(1, request.protocolVersion)
            string(2, request.requestId.toString())
            request.observedProducerInstanceId?.let { string(3, it.toString()) }
            request.observedProducerGeneration?.let { long(4, it) }
            request.observedSnapshotRevision?.let { long(5, it) }
            long(6, request.requestedAt.toEpochMilli())
        }.prependMagic()
    }

    fun decode(payload: ByteArray): WearAppRequest? = runCatching {
        require(payload.size <= WearAppRequestRules.MAX_PAYLOAD_BYTES)
        val top = readFields(payload.removeMagic())
        val request = WearAppRequest(
            protocolVersion = top.required(1).readInt(),
            requestId = UUID.fromString(top.required(2).readString()),
            observedProducerInstanceId = top.optional(3)?.readString()?.let(UUID::fromString),
            observedProducerGeneration = top.optional(4)?.readLong(),
            observedSnapshotRevision = top.optional(5)?.readLong(),
            requestedAt = Instant.ofEpochMilli(top.required(6).readLong())
        )
        require(WearAppRequestRules.isValid(request))
        request
    }.getOrNull()

    private fun fields(block: FieldWriter.() -> Unit): ByteArray =
        FieldWriter().apply(block).toByteArray()

    private fun ByteArray.prependMagic(): ByteArray =
        ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.writeInt(MAGIC)
                data.write(this)
            }
            output.toByteArray()
        }

    private fun ByteArray.removeMagic(): ByteArray =
        DataInputStream(ByteArrayInputStream(this)).use { data ->
            require(data.readInt() == MAGIC)
            ByteArray(data.available()).also(data::readFully)
        }

    private class FieldWriter {
        private val output = ByteArrayOutputStream()
        private val data = DataOutputStream(output)

        fun int(tag: Int, value: Int) = write(tag) { writeInt(value) }
        fun long(tag: Int, value: Long) = write(tag) { writeLong(value) }
        fun string(tag: Int, value: String) =
            write(tag) { write(value.toByteArray(StandardCharsets.UTF_8)) }

        fun toByteArray(): ByteArray = output.toByteArray()

        private fun write(tag: Int, block: DataOutputStream.() -> Unit) {
            val value = ByteArrayOutputStream().use { valueOutput ->
                DataOutputStream(valueOutput).use(block)
                valueOutput.toByteArray()
            }
            data.writeInt(tag)
            data.writeInt(value.size)
            data.write(value)
        }
    }

    private class FieldSet(private val values: Map<Int, List<ByteArray>>) {
        fun required(tag: Int): ByteArray = values[tag].orEmpty().single()
        fun optional(tag: Int): ByteArray? = values[tag]?.also { require(it.size == 1) }?.single()
    }

    private fun readFields(bytes: ByteArray): FieldSet {
        require(bytes.size <= WearAppRequestRules.MAX_PAYLOAD_BYTES)
        val values = mutableMapOf<Int, MutableList<ByteArray>>()
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            while (data.available() > 0) {
                require(data.available() >= 8)
                val tag = data.readInt()
                val size = data.readInt()
                require(size in 0..WearAppRequestRules.MAX_PAYLOAD_BYTES && size <= data.available())
                val value = ByteArray(size).also(data::readFully)
                values.getOrPut(tag) { mutableListOf() }.add(value)
            }
        }
        return FieldSet(values)
    }

    private fun ByteArray.readInt(): Int =
        DataInputStream(ByteArrayInputStream(this)).use { data ->
            data.readInt().also { require(data.available() == 0) }
        }

    private fun ByteArray.readLong(): Long =
        DataInputStream(ByteArrayInputStream(this)).use { data ->
            data.readLong().also { require(data.available() == 0) }
        }

    private fun ByteArray.readString(): String = toString(StandardCharsets.UTF_8)
}
