package io.github.yingqiu0871.evolune.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * V17 Phase E E4.1–E4.4 + EG3–EG6/EG9/EG13/EG14/EG21/EG22 — static architecture guards for the
 * Phase-E production packages (frozen contract §4/§9/§32/§43/§47).
 */
class PortableArchitectureGuardTest {

    private val exportDir = Path.of("src/main/java/io/github/yingqiu0871/evolune/export")
    private val navigationFile = Path.of("src/main/java/io/github/yingqiu0871/evolune/navigation/AppNavigation.kt")
    private val screenFile = Path.of("src/main/java/io/github/yingqiu0871/evolune/ui/screens/settings/SettingsImportExportBlock.kt")

    private val exportFiles: List<Path> = Files.list(exportDir).use { stream ->
        stream.filter { it.fileName.toString().endsWith(".kt") }.toList()
    }.sorted()

    private val blockComment = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val lineComment = Regex("//[^\\n]*")

    private fun code(path: Path): String = Files.readString(path)
        .replace(blockComment, "")
        .replace(lineComment, "")

    private fun codeOf(name: String): String {
        val path = exportFiles.singleOrNull { it.fileName.toString() == name }
            ?: error("missing export file $name")
        return code(path)
    }

    private fun functionBody(path: Path, functionName: String): String {
        val text = Files.readString(path)
        val start = text.indexOf("fun $functionName(")
        require(start >= 0) { "function $functionName not found" }
        val bodyStart = text.indexOf('{', start)
        require(bodyStart >= 0) { "function $functionName body not found" }
        var depth = 0
        var index = bodyStart
        while (index < text.length) {
            when (text[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return text.substring(bodyStart, index + 1)
                }
            }
            index += 1
        }
        error("unbalanced braces for $functionName")
    }

    @Test
    fun `the export package carries the expected production files`() {
        val names = exportFiles.map { it.fileName.toString() }
        listOf(
            "PortableModels.kt",
            "PortableJsonText.kt",
            "PortableRangeMapping.kt",
            "PortableJsonCodec.kt",
            "PortableJsonDuplicateKeyScanner.kt",
            "PortableCsvCodec.kt",
            "PortableExportService.kt",
            "PortableImportService.kt",
            "PortableImportBounds.kt",
            "LegacyMahiroExportRunner.kt"
        ).forEach { expected ->
            assertTrue("missing $expected", expected in names)
        }
    }

    @Test
    fun `no backup, restore or drive coupling exists in the export package`() {
        val forbidden = listOf(
            "io.github.yingqiu0871.evolune.backup",
            "EvoluneBackup",
            "BackupRestore",
            "evbackup",
            "appDataFolder",
            "GoogleDrive",
            "cloud.google"
        )
        exportFiles.forEach { path ->
            val fileCode = code(path)
            forbidden.forEach { term ->
                assertFalse("$path must not reference '$term'", fileCode.contains(term))
            }
        }
    }

    @Test
    fun `no timeline, history, retrospective, matcher or occurrence source exists in the export package`() {
        val forbidden = listOf(
            "Timeline",
            "HistoryRangeSource",
            "HistoryReadService",
            "HistoricalProjectionBuilder",
            "Retrospective",
            "MedicationOccurrence",
            "MedicationIdentity",
            "matcher",
            "occurrence",
            "HRTViewModel",
            "observeAll",
            "getEventsForPk"
        )
        exportFiles.forEach { path ->
            val fileCode = code(path)
            forbidden.forEach { term ->
                assertFalse("$path must not reference '$term'", fileCode.contains(term))
            }
        }
    }

    @Test
    fun `no android, compose, clipboard or resource dependency exists in the export package`() {
        val forbidden = listOf(
            "androidx.",
            "import android",
            "R.string",
            "android.content.Context",
            "Clipboard",
            "HealthConnect",
            "io.github.yingqiu0871.evolune.widget",
            "io.github.yingqiu0871.evolune.wear"
        )
        exportFiles.forEach { path ->
            val fileCode = code(path)
            forbidden.forEach { term ->
                assertFalse("$path must not reference '$term'", fileCode.contains(term))
            }
        }
    }

    @Test
    fun `no hidden clock or system time exists in the export package`() {
        val forbidden = listOf(
            "Instant.now",
            "LocalDate.now",
            "ZoneId.systemDefault",
            "Clock.system",
            "System.currentTimeMillis",
            "currentTimeMillis()"
        )
        exportFiles.forEach { path ->
            val fileCode = code(path)
            forbidden.forEach { term ->
                assertFalse("$path must not contain '$term'", fileCode.contains(term))
            }
        }
    }

    @Test
    fun `the serialization path performs no repository writes`() {
        val serializationFiles = listOf(
            "PortableJsonCodec.kt",
            "PortableCsvCodec.kt",
            "PortableJsonText.kt",
            "PortableRangeMapping.kt",
            "PortableExportService.kt",
            "PortableModels.kt",
            "PortableJsonDuplicateKeyScanner.kt",
            "PortableImportBounds.kt"
        )
        val writeVocabulary = listOf(".insert(", ".update(", ".delete", "upsert", ".restore", "deleteAll")
        serializationFiles.forEach { name ->
            val fileCode = codeOf(name)
            writeVocabulary.forEach { term ->
                assertFalse("$name must not contain write vocabulary '$term'", fileCode.contains(term))
            }
        }
    }

    @Test
    fun `the canonical import path inserts through the public seam and never updates, deletes or restores`() {
        val importCode = codeOf("PortableImportService.kt")
        assertTrue(importCode.contains("repository.insert("))
        assertTrue(importCode.contains("repository.getById("))
        listOf(".update(", "delete", "upsert", "restore", "replace", "randomUUID").forEach { term ->
            assertFalse("PortableImportService must not contain '$term'", importCode.contains(term))
        }
    }

    @Test
    fun `canonical import never regenerates identities`() {
        listOf("PortableImportService.kt", "PortableJsonCodec.kt", "PortableModels.kt").forEach { name ->
            val fileCode = codeOf(name)
            assertFalse("$name must not regenerate ids", fileCode.contains("randomUUID"))
        }
    }

    @Test
    fun `canonical serialization order is explicit and independent of read order`() {
        val serviceCode = codeOf("PortableExportService.kt")
        assertTrue(serviceCode.contains("sortedWith"))
        assertTrue(serviceCode.contains("compareBy"))
        assertTrue(serviceCode.contains("findOccurredBetween"))
        assertTrue(serviceCode.contains("findAllOccurredUpTo"))
    }

    @Test
    fun `canonical bytes are written only to the SAF destination and never to the clipboard`() {
        val writeBody = functionBody(navigationFile, "writePendingPortableExport")
        assertTrue(writeBody.contains("openOutputStream"))
        assertFalse(writeBody.contains("Clipboard"))
        val legacyClipboardBody = functionBody(navigationFile, "exportLegacyToClipboard")
        assertTrue(legacyClipboardBody.contains("setPrimaryClip"))
        val navigationCode = Files.readString(navigationFile)
        assertEquals(1, Regex("setPrimaryClip").findAll(navigationCode).count())
        val screenCode = Files.readString(screenFile)
        assertFalse(screenCode.contains("ClipboardManager"))
    }

    @Test
    fun `the import service validates the byte and event bounds before any repository call`() {
        val importCode = codeOf("PortableImportService.kt")
        val boundsCheck = importCode.indexOf("maxInputBytes")
        val decodeCall = importCode.indexOf("PortableJsonCodec.decode")
        val firstRepositoryCall = importCode.indexOf("repository.getById(")
        assertTrue(boundsCheck in 0 until decodeCall)
        assertTrue(decodeCall < firstRepositoryCall)
    }
}
