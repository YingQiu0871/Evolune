package io.github.yingqiu0871.evolune.backup

import android.os.Debug
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhysicalBackupPerformanceTest {
    private val json = Json
    private val deriveKeyMethod: Method = EvoluneBackupCodec::class.java.getDeclaredMethod(
        "deriveKey",
        CharArray::class.java,
        ByteArray::class.java,
        Int::class.javaPrimitiveType
    ).apply {
        isAccessible = true
    }

    @Test
    fun physicalKdf600kBenchmark() {
        runBlocking(Dispatchers.Main.immediate) {
        val threadName = AtomicReference<String>()
        val workerOnMain = AtomicBoolean(true)
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread({
                threadName.set(Thread.currentThread().name)
                workerOnMain.set(Looper.myLooper() == Looper.getMainLooper())
                runnable.run()
            }, "evolune-r6-kdf-worker")
        }
        val worker = executor.asCoroutineDispatcher()
        val random = SecureRandom()
        val timingsNanos = mutableListOf<Long>()
        val iterations = EvoluneBackupFormat.DEFAULT_KDF_ITERATIONS

        try {
            repeat(2) { index ->
                val elapsed = withContext(worker) {
                    val secret = R6_PASSPHRASE.toCharArray()
                    val salt = ByteArray(EvoluneBackupFormat.SALT_BYTES).also(random::nextBytes)
                    try {
                        val started = SystemClock.elapsedRealtimeNanos()
                        deriveProductionKey(secret, salt, iterations).wipeCopy()
                        SystemClock.elapsedRealtimeNanos() - started
                    } finally {
                        secret.fill('\u0000')
                    }
                }
                Log.i(TAG, "KDF WARMUP ${index + 1}: ${nanosToMillis(elapsed)} ms")
            }

            repeat(7) { index ->
                val measurement = withContext(worker) {
                    val secret = R6_PASSPHRASE.toCharArray()
                    val salt = ByteArray(EvoluneBackupFormat.SALT_BYTES).also(random::nextBytes)
                    try {
                        val started = SystemClock.elapsedRealtimeNanos()
                        val key = deriveProductionKey(secret, salt, iterations)
                        val keyLength = key.wipeCopy()
                        val elapsed = SystemClock.elapsedRealtimeNanos() - started
                        Triple(elapsed, keyLength, Looper.myLooper() == Looper.getMainLooper())
                    } finally {
                        secret.fill('\u0000')
                    }
                }
                assertEquals(EvoluneBackupFormat.DERIVED_KEY_LENGTH_BITS / 8, measurement.second)
                assertFalse("KDF ran on the main looper", measurement.third)
                timingsNanos += measurement.first
                Log.i(
                    TAG,
                    "KDF MEASURED ${index + 1}: ${nanosToMillis(measurement.first)} ms " +
                        "thread=${threadName.get()}"
                )
            }

            val statistics = statistics(timingsNanos)
            assertEquals(iterations, EvoluneBackupFormat.DEFAULT_KDF_ITERATIONS)
            assertFalse("injected KDF worker was the main looper", workerOnMain.get())
            Log.i(
                TAG,
                "KDF STATS algorithm=${EvoluneBackupFormat.KDF_ALGORITHM} " +
                    "iterations=$iterations keyBits=${EvoluneBackupFormat.DERIVED_KEY_LENGTH_BITS} " +
                    "min=${statistics.minMs} ms median=${statistics.medianMs} ms " +
                    "max=${statistics.maxMs} ms mean=${statistics.meanMs} ms " +
                    "warmups=2 measured=${timingsNanos.size}"
            )
        } finally {
            worker.close()
            executor.shutdownNow()
        }
        }
    }

    @Test
    fun physicalLargeHistoryNativeBackupPipeline() {
        runBlocking(Dispatchers.Main.immediate) {
        val threadName = AtomicReference<String>()
        val workerOnMain = AtomicBoolean(true)
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread({
                threadName.set(Thread.currentThread().name)
                workerOnMain.set(Looper.myLooper() == Looper.getMainLooper())
                runnable.run()
            }, "evolune-r6-crypto-worker")
        }
        val worker = executor.asCoroutineDispatcher()
        val codec = EvoluneBackupCodec(cryptoDispatcher = worker)
        val passphrase = R6_PASSPHRASE.toCharArray()
        val metadata = BackupProducerMetadataV1(
            createdAt = "2026-08-26T00:00:00Z",
            producerAppVersionName = "1.2.0-debug",
            producerAppVersionCode = 102_000_000
        )
        val beforePssKb = currentPssKb()

        try {
            val snapshotStarted = SystemClock.elapsedRealtimeNanos()
            val payload = largeSyntheticPayload()
            val snapshotNanos = SystemClock.elapsedRealtimeNanos() - snapshotStarted

            assertEquals(100, payload.medicationPlans.size)
            assertEquals(1_000, payload.scheduledDoseSlots.size)
            assertEquals(10_000, payload.doseEvents.size)
            assertTrue(codec.validate(payload) is BackupValidationResult.Valid)

            val encodeStarted = SystemClock.elapsedRealtimeNanos()
            val encoded = when (
                val result = codec.encodeOnCryptoDispatcher(
                    payload = payload,
                    passphrase = passphrase.copyOf(),
                    metadata = metadata
                )
            ) {
                is BackupEncodeResult.Success -> result.bytes
                is BackupEncodeResult.Failure -> error("large-history encode failed: ${result.error}")
            }
            val encryptNanos = SystemClock.elapsedRealtimeNanos() - encodeStarted
            val sizes = encodedSizes(encoded)
            assertTrue(sizes.plaintextBytes > 0)
            assertTrue(sizes.encryptedBytes >= sizes.plaintextBytes)

            val decryptStarted = SystemClock.elapsedRealtimeNanos()
            val decoded = when (
                val result = codec.decodeAndValidateOnCryptoDispatcher(
                    bytes = encoded,
                    passphrase = passphrase.copyOf()
                )
            ) {
                is BackupDecodeResult.Success -> result
                is BackupDecodeResult.Failure -> error("large-history decode failed: ${result.error}")
            }
            val decryptToValidatedNanos = SystemClock.elapsedRealtimeNanos() - decryptStarted
            val expectedCanonicalPayload = EvoluneBackupPayloadV1(
                medicationPlans = payload.medicationPlans.sortedBy { it.id },
                scheduledDoseSlots = payload.scheduledDoseSlots.sortedWith(
                    compareBy<BackupScheduledDoseSlotV1> { it.planId }
                        .thenBy { it.position }
                        .thenBy { it.id }
                ),
                doseEvents = payload.doseEvents.sortedWith(
                    compareBy<BackupDoseEventV1> { Instant.parse(it.occurredAt).toString() }
                        .thenBy { it.id }
                ),
                settings = payload.settings
            )
            assertEquals(expectedCanonicalPayload, decoded.payload.payload)

            val previewStarted = SystemClock.elapsedRealtimeNanos()
            val preview = restorePreview(decoded.payload.payload, decoded.metadata)
            val previewNanos = SystemClock.elapsedRealtimeNanos() - previewStarted
            assertEquals(payload.medicationPlans.size, preview.medicationPlanCount)
            assertEquals(payload.scheduledDoseSlots.size, preview.scheduledDoseSlotCount)
            assertEquals(payload.doseEvents.size, preview.doseEventCount)
            assertEquals(payload.settings.bodyWeightKg, preview.bodyWeightKg, 0.0)

            val afterPssKb = currentPssKb()
            assertFalse("crypto dispatcher ran on the main looper", workerOnMain.get())
            Log.i(
                TAG,
                "LARGE HISTORY plans=${payload.medicationPlans.size} " +
                    "slots=${payload.scheduledDoseSlots.size} events=${payload.doseEvents.size} " +
                    "settings=1 plaintextBytes=${sizes.plaintextBytes} " +
                    "encryptedBytes=${sizes.encryptedBytes} " +
                    "snapshot=${nanosToMillis(snapshotNanos)} ms " +
                    "encryptPipeline=${nanosToMillis(encryptNanos)} ms " +
                    "decryptToValidated=${nanosToMillis(decryptToValidatedNanos)} ms " +
                    "preview=${nanosToMillis(previewNanos)} ms " +
                    "decryptToPreview=${nanosToMillis(decryptToValidatedNanos + previewNanos)} ms " +
                    "thread=${threadName.get()} pssBeforeKb=$beforePssKb pssAfterKb=$afterPssKb"
            )
            Log.i(
                TAG,
                "LARGE HISTORY RESULT countsMatch=true oom=false processDeath=false anr=false " +
                    "mainThreadCrypto=false wrongPassphrase=NOT_RUN"
            )
        } finally {
            passphrase.fill('\u0000')
            worker.close()
            executor.shutdownNow()
        }
    }
    }

    private fun deriveProductionKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int
    ): SecretKeySpec = deriveKeyMethod.invoke(
        EvoluneBackupCodec(),
        *arrayOf<Any>(passphrase, salt, iterations)
    ) as SecretKeySpec

    private fun SecretKeySpec.wipeCopy(): Int {
        val bytes = encoded
        val length = bytes.size
        bytes.fill(0)
        return length
    }

    private fun currentPssKb(): Int = Debug.MemoryInfo().let { info ->
        Debug.getMemoryInfo(info)
        info.totalPss
    }

    private fun encodedSizes(bytes: ByteArray): EncodedSizes {
        val root = json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8)).jsonObject
        val ciphertext = Base64.getDecoder().decode(
            root.getValue("ciphertext").jsonPrimitive.content
        )
        return EncodedSizes(
            plaintextBytes = ciphertext.size - EvoluneBackupFormat.GCM_TAG_BITS / 8,
            encryptedBytes = bytes.size
        )
    }

    private fun largeSyntheticPayload(): EvoluneBackupPayloadV1 {
        val plans = (0 until 100).map { index ->
            BackupMedicationPlanV1(
                id = stableId("plan", index),
                name = "Synthetic plan $index",
                route = "ORAL",
                ester = "E2",
                doseMG = 1.0,
                scheduleType = "CUSTOM",
                daysOfWeek = emptyList(),
                intervalDays = 1,
                isEnabled = index % 2 == 0,
                extras = emptyMap(),
                createdAt = "2026-08-26T00:00:00Z"
            )
        }
        val slots = (0 until 1_000).map { index ->
            val planIndex = index / 10
            val position = index % 10
            BackupScheduledDoseSlotV1(
                id = stableId("slot", index),
                planId = plans[planIndex].id,
                localTime = String.format(Locale.US, "%02d:00", 6 + position),
                position = position
            )
        }
        val events = (0 until 10_000).map { index ->
            BackupDoseEventV1(
                id = stableId("event", index),
                route = "ORAL",
                occurredAt = "2026-08-26T00:00:00Z",
                zoneId = null,
                localDate = null,
                doseMG = 1.0,
                ester = "E2",
                extras = emptyMap(),
                slotId = slots[index % slots.size].id,
                source = "MANUAL",
                status = "RECORDED",
                revision = 1L
            )
        }
        return EvoluneBackupPayloadV1(
            medicationPlans = plans,
            scheduledDoseSlots = slots,
            doseEvents = events,
            settings = BackupSettingsV1(
                bodyWeightKg = 55.0,
                themeMode = "LIGHT",
                colorTheme = "BUILTIN",
                autoCheckUpdates = true,
                timeFormat = "HOUR_24"
            )
        )
    }

    private fun stableId(kind: String, index: Int): String =
        UUID.nameUUIDFromBytes("$kind-$index".toByteArray(StandardCharsets.UTF_8)).toString()

    private fun statistics(values: List<Long>): Statistics {
        val sorted = values.sorted()
        return Statistics(
            minMs = nanosToMillis(sorted.first()),
            medianMs = nanosToMillis(sorted[sorted.lastIndex / 2]),
            maxMs = nanosToMillis(sorted.last()),
            meanMs = nanosToMillis(values.sum() / values.size)
        )
    }

    private fun nanosToMillis(nanos: Long): String =
        String.format(Locale.US, "%.3f", nanos / 1_000_000.0)

    private data class Statistics(
        val minMs: String,
        val medianMs: String,
        val maxMs: String,
        val meanMs: String
    )

    private data class EncodedSizes(
        val plaintextBytes: Int,
        val encryptedBytes: Int
    )

    private companion object {
        const val TAG = "EvoluneR6"
        const val R6_PASSPHRASE = "synthetic-r6-passphrase"
    }
}
