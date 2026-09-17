package io.github.yingqiu0871.evolune.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * V17 Phase E E6.5/EG11/EG25 — Phase-E string family parity, placeholder parity and
 * forbidden-vocabulary scan (frozen contract §35/§42/§47).
 */
class PortableStringsParityTest {

    private val paths = listOf(
        "src/main/res/values/strings.xml",
        "src/main/res/values-zh-rCN/strings.xml"
    )

    private fun stringsOf(path: String): Map<String, String> {
        val text = Files.readString(Path.of(path))
        return Regex("<string name=\"((?:portable_|settings_data_legacy_)[a-z_0-9]+)\">([^<]*)</string>")
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(value: String): List<String> =
        Regex("%[0-9]+\\$[sd]").findAll(value).map { it.value }.sorted().toList()

    @Test
    fun `both shipped resources carry the same Phase-E key family`() {
        val default = stringsOf(paths[0])
        val zh = stringsOf(paths[1])
        assertEquals(28, default.size)
        assertEquals(default.keys, zh.keys)
        assertEquals(27, default.keys.count { it.startsWith("portable_") })
        assertTrue(default.containsKey("settings_data_legacy_title"))
    }

    @Test
    fun `every Phase-E key keeps an identical placeholder signature in both files`() {
        val default = stringsOf(paths[0])
        val zh = stringsOf(paths[1])
        default.forEach { (key, value) ->
            assertEquals("placeholder drift for $key", placeholders(value), placeholders(zh.getValue(key)))
        }
        assertEquals(listOf("%1\$d", "%2\$d"), placeholders(default.getValue("portable_import_success")))
        assertEquals(
            listOf("%1\$d", "%2\$d", "%3\$d"),
            placeholders(default.getValue("portable_import_conflicts"))
        )
        assertEquals(
            listOf("%1\$d", "%2\$d", "%3\$d"),
            placeholders(default.getValue("portable_import_partial"))
        )
    }

    @Test
    fun `no adherence or grading vocabulary appears in the Phase-E string family`() {
        val forbidden = listOf(
            "missed",
            "late",
            "on-time",
            "on time",
            "overdue",
            "adherence",
            "compliance",
            "skipped",
            "漏服",
            "迟服",
            "按时",
            "依从",
            "合规",
            "未服"
        )
        listOf(paths[0], paths[1]).forEach { path ->
            stringsOf(path).forEach { (key, value) ->
                val lower = value.lowercase()
                forbidden.forEach { term ->
                    assertTrue(
                        "$key in $path must not contain '$term'",
                        !lower.contains(term.lowercase())
                    )
                }
            }
        }
    }

    @Test
    fun `the legacy label distinguishes Mahiro compatibility from the canonical format`() {
        listOf(paths[0], paths[1]).forEach { path ->
            val value = stringsOf(path).getValue("settings_data_legacy_title")
            assertTrue(value.contains("Mahiro JSON v1"))
            assertTrue(value.contains("Legacy"))
        }
    }
}
