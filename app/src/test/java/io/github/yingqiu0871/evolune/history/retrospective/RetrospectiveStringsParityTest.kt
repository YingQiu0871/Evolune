package io.github.yingqiu0871.evolune.history.retrospective

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * V17-C-04 §14.10 N5 — resource parity and frozen copy.
 *
 * Both shipped resource files are Simplified Chinese; C-04 declares no English runtime locale.
 * The two files must carry identical retrospective key sets and identical placeholder signatures,
 * and the mandated disclosure/label copy must be present verbatim.
 */
class RetrospectiveStringsParityTest {

    private val paths = listOf(
        "src/main/res/values/strings.xml",
        "src/main/res/values-zh-rCN/strings.xml"
    )

    private fun stringsOf(path: String): Map<String, String> {
        val text = Files.readString(Path.of(path))
        return Regex("<string name=\"(retrospective_[a-z_0-9]+)\">([^<]*)</string>")
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(value: String): List<String> =
        Regex("%[0-9]+\\$[sd]").findAll(value).map { it.value }.sorted().toList()

    @Test
    fun `both shipped resources carry the same retrospective key set`() {
        val default = stringsOf(paths[0])
        val zh = stringsOf(paths[1])
        assertTrue("the default resource must contain every retrospective key", default.isNotEmpty())
        assertEquals(default.keys, zh.keys)
        assertEquals(20, default.size)
    }

    @Test
    fun `every retrospective key keeps an identical placeholder signature in both files`() {
        val default = stringsOf(paths[0])
        val zh = stringsOf(paths[1])
        default.forEach { (key, value) ->
            assertEquals("placeholder drift for $key", placeholders(value), placeholders(zh.getValue(key)))
        }
    }

    @Test
    fun `the mandatory disclosure and marker labels are verbatim frozen`() {
        val default = stringsOf(paths[0])
        val zh = stringsOf(paths[1])

        listOf(default, zh).forEach { strings ->
            assertEquals("模型估算——并非实测血药浓度。", strings.getValue("retrospective_disclosure"))
            assertEquals(
                "方案标记表示当前方案上下文；摄入标记显示与本次估算关联的当前记录。若记录刚刚被修改，标记内容可能比曲线更新。",
                strings.getValue("retrospective_marker_legend_disclosure")
            )
            assertEquals("当前方案上下文", strings.getValue("retrospective_marker_schedule_context"))
            assertEquals("已记录摄入", strings.getValue("retrospective_marker_recorded_intake"))
            assertEquals("此估算暂无可用的已记录摄入。", strings.getValue("retrospective_unavailable_no_eligible_intakes"))
            assertEquals("该时间范围无法计算。", strings.getValue("retrospective_unavailable_invalid_interval"))
            assertEquals("估算暂不可用。", strings.getValue("retrospective_unavailable_generic"))
            assertEquals("历史记录未能完整读取，请重试。", strings.getValue("retrospective_unavailable_history_unavailable"))
        }
    }

    @Test
    fun `the window caption keeps exactly one timestamp placeholder`() {
        val default = stringsOf(paths[0])
        assertEquals(listOf("%1\$s"), placeholders(default.getValue("retrospective_window_caption")))
    }
}
