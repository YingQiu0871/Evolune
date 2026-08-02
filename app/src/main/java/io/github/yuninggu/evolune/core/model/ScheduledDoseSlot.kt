package io.github.yuninggu.evolune.core.model

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.time.LocalTime
import java.util.UUID

data class ScheduledDoseSlot(
    val id: UUID,
    val planId: UUID,
    val localTime: LocalTime,
    val position: Int
) {
    init {
        require(position >= 0) { "position must be non-negative" }
        require(localTime.second == 0 && localTime.nano == 0) {
            "localTime must have minute precision"
        }
    }
}

sealed interface SlotIdResult {
    data class Success(
        val id: UUID,
        val canonicalName: String,
        val projectNamespace: UUID
    ) : SlotIdResult

    data class Failure(val error: SlotIdError) : SlotIdResult
}

sealed interface SlotIdError {
    data class InvalidPlanId(val input: String) : SlotIdError
    data class PlanIdHasSurroundingWhitespace(val input: String) : SlotIdError
    data class InvalidPosition(val position: Int) : SlotIdError
    data class InvalidLocalTimePrecision(val localTime: LocalTime) : SlotIdError
    data class UuidV5Failure(val message: String) : SlotIdError
}

object ScheduledDoseSlotId {
    private const val PROJECT_NAMESPACE_NAME =
        "io.github.yuninggu.evolune:scheduled-dose-slot"
    private const val EXPECTED_PROJECT_NAMESPACE =
        "68559b97-4ddc-5be2-bcbd-9ab409f0d95b"
    private val dnsNamespace = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
    private val expectedProjectNamespace = UUID.fromString(EXPECTED_PROJECT_NAMESPACE)

    fun generate(
        planId: String,
        position: Int,
        localTime: LocalTime
    ): SlotIdResult {
        if (planId != planId.trim()) {
            return SlotIdResult.Failure(
                SlotIdError.PlanIdHasSurroundingWhitespace(planId)
            )
        }
        val parsedPlanId = try {
            UUID.fromString(planId)
        } catch (_: IllegalArgumentException) {
            return SlotIdResult.Failure(SlotIdError.InvalidPlanId(planId))
        }
        return generate(parsedPlanId, position, localTime)
    }

    fun generate(
        planId: UUID,
        position: Int,
        localTime: LocalTime
    ): SlotIdResult {
        if (position < 0) {
            return SlotIdResult.Failure(SlotIdError.InvalidPosition(position))
        }
        if (localTime.second != 0 || localTime.nano != 0) {
            return SlotIdResult.Failure(
                SlotIdError.InvalidLocalTimePrecision(localTime)
            )
        }

        val projectNamespace = when (
            val result = uuidV5(dnsNamespace, PROJECT_NAMESPACE_NAME)
        ) {
            is UuidV5Result.Success -> result.value
            is UuidV5Result.Failure -> return SlotIdResult.Failure(result.error)
        }
        if (projectNamespace != expectedProjectNamespace) {
            return SlotIdResult.Failure(
                SlotIdError.UuidV5Failure("project namespace verification failed")
            )
        }

        val canonicalName = buildString {
            append("slot:v1:plan=")
            append(planId.toString())
            append(";position=")
            append(position.toString())
            append(";time=")
            append(canonicalLocalTime(localTime))
        }
        return when (val result = uuidV5(projectNamespace, canonicalName)) {
            is UuidV5Result.Success -> SlotIdResult.Success(
                id = result.value,
                canonicalName = canonicalName,
                projectNamespace = projectNamespace
            )
            is UuidV5Result.Failure -> SlotIdResult.Failure(result.error)
        }
    }

    private fun canonicalLocalTime(localTime: LocalTime): String =
        localTime.hour.toString().padStart(2, '0') +
            ":" +
            localTime.minute.toString().padStart(2, '0')

    private fun uuidV5(namespace: UUID, name: String): UuidV5Result = try {
        val namespaceBytes = ByteBuffer.allocate(16)
            .putLong(namespace.mostSignificantBits)
            .putLong(namespace.leastSignificantBits)
            .array()
        val digest = MessageDigest.getInstance("SHA-1").apply {
            update(namespaceBytes)
            update(name.toByteArray(StandardCharsets.UTF_8))
        }.digest()
        val uuidBytes = digest.copyOf(16)
        uuidBytes[6] = ((uuidBytes[6].toInt() and 0x0f) or 0x50).toByte()
        uuidBytes[8] = ((uuidBytes[8].toInt() and 0x3f) or 0x80).toByte()
        val buffer = ByteBuffer.wrap(uuidBytes)
        UuidV5Result.Success(UUID(buffer.long, buffer.long))
    } catch (error: GeneralSecurityException) {
        UuidV5Result.Failure(
            SlotIdError.UuidV5Failure(error.message ?: "SHA-1 unavailable")
        )
    }

    private sealed interface UuidV5Result {
        data class Success(val value: UUID) : UuidV5Result
        data class Failure(val error: SlotIdError.UuidV5Failure) : UuidV5Result
    }
}
