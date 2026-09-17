package io.github.yingqiu0871.evolune.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * W-DH-2 (Decision H): the Widget rejection feedback string must exist in both shipped
 * resource authorities with identical content and neutral (non-grading) wording.
 */
class WidgetStringsParityTest {

    private val paths = listOf(
        "src/main/res/values/strings.xml",
        "src/main/res/values-zh-rCN/strings.xml"
    )

    private val key = "widget_dose_rejected"

    private fun valueOf(path: String): String? {
        val text = Files.readString(Path.of(path))
        return Regex("<string name=\"$key\">([^<]*)</string>")
            .find(text)
            ?.groupValues
            ?.get(1)
    }

    @Test
    fun `the rejection feedback string exists in both authorities with identical text`() {
        val values = paths.map { valueOf(it) }
        values.forEachIndexed { index, value ->
            assertTrue("${paths[index]} must carry $key", value != null)
        }
        assertEquals(values[0], values[1])
        assertTrue("the rejection message must not use placeholders", !values[0]!!.contains("%"))
    }

    @Test
    fun `the rejection feedback wording stays neutral and factually scoped`() {
        val forbidden = listOf(
            "missed",
            "late",
            "on-time",
            "overdue",
            "adherence",
            "compliance",
            "漏服",
            "迟服",
            "按时",
            "依从",
            "合规"
        )
        paths.forEach { path ->
            val value = valueOf(path)!!.lowercase()
            forbidden.forEach { term ->
                assertTrue("$path must not contain '$term'", !value.contains(term.lowercase()))
            }
        }
    }
}
