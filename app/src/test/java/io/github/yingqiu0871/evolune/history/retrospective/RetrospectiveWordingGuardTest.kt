package io.github.yingqiu0871.evolune.history.retrospective

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * V17-C-04 §12 wording guards and §14.5 T6 — no retrospective user-visible string may classify the
 * user (adherence family) and no copy may claim the current Read2 marker payload is the exact /
 * same numerical snapshot consumed by the Read1 curve.
 *
 * The two mandated disclosure strings are exempted from the concentration-terms scan because they
 * are the frozen contract copy itself; every other retrospective string is scanned strictly.
 */
class RetrospectiveWordingGuardTest {

    private val paths = listOf(
        "src/main/res/values/strings.xml",
        "src/main/res/values-zh-rCN/strings.xml"
    )

    private val mandatedKeys = setOf(
        "retrospective_disclosure",
        "retrospective_marker_legend_disclosure"
    )

    private fun stringsOf(path: String): Map<String, String> {
        val text = Files.readString(Path.of(path))
        return Regex("<string name=\"(retrospective_[a-z_0-9]+)\">([^<]*)</string>")
            .findAll(text)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun `no retrospective string classifies the user with adherence wording`() {
        val forbidden = listOf(
            "adherence", "compliance", "missed", "skipped", "late", "on-time", "delay",
            "依从", "漏服", "跳过", "迟到", "准时", "按时", "延迟"
        )
        paths.forEach { path ->
            stringsOf(path).forEach { (key, value) ->
                forbidden.forEach { term ->
                    assertFalse(
                        "$path:$key must not contain '$term'",
                        value.contains(term, ignoreCase = true)
                    )
                }
            }
        }
    }

    @Test
    fun `no retrospective string implies a measured concentration outside the mandated disclosure`() {
        val forbidden = listOf(
            "measured", "actual concentration", "true blood level", "lab result",
            "真实血药浓度", "化验"
        )
        paths.forEach { path ->
            stringsOf(path)
                .filterKeys { it !in mandatedKeys }
                .forEach { (key, value) ->
                    forbidden.forEach { term ->
                        assertFalse(
                            "$path:$key must not contain '$term'",
                            value.contains(term, ignoreCase = true)
                        )
                    }
                }
        }
    }

    @Test
    fun `no copy claims the marker payload is the exact or same snapshot of the curve input`() {
        val forbidden = listOf(
            "exact input", "same snapshot", "used payload", "synchronized", "atomic",
            "精确输入", "同一快照", "所用输入", "已同步", "原子"
        )
        paths.forEach { path ->
            stringsOf(path).forEach { (key, value) ->
                forbidden.forEach { term ->
                    assertFalse(
                        "$path:$key must not contain '$term'",
                        value.contains(term, ignoreCase = true)
                    )
                }
            }
        }
    }

    @Test
    fun `the mandatory copy keeps the truthful non-atomic semantics`() {
        val legend = stringsOf(paths[0])
        val legendText = legend.getValue("retrospective_marker_legend_disclosure")
        assertTrue(legendText.contains("当前方案上下文"))
        assertTrue(legendText.contains("本次估算关联的当前记录"))
        assertTrue(legendText.contains("标记内容可能比曲线更新"))

        val disclosure = legend.getValue("retrospective_disclosure")
        assertTrue(disclosure.contains("模型估算"))
        assertTrue(disclosure.contains("并非实测血药浓度"))
    }

    @Test
    fun `the retrospective ui sources carry no hardcoded user-visible copy`() {
        val blockComment = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val lineComment = Regex("//[^\\n]*")
        val cjk = Regex("[\\u4e00-\\u9fff]")

        listOf(
            "src/main/java/io/github/yingqiu0871/evolune/history/retrospective/RetrospectivePkSurfaceCoordinator.kt",
            "src/main/java/io/github/yingqiu0871/evolune/history/retrospective/RetrospectivePkViewModel.kt",
            "src/main/java/io/github/yingqiu0871/evolune/history/retrospective/RetrospectiveMarkerModels.kt",
            "src/main/java/io/github/yingqiu0871/evolune/history/retrospective/RetrospectivePkUiState.kt",
            "src/main/java/io/github/yingqiu0871/evolune/ui/screens/retrospective/RetrospectivePkScreen.kt",
            "src/main/java/io/github/yingqiu0871/evolune/ui/screens/retrospective/RetrospectiveConcentrationChart.kt"
        ).forEach { path ->
            val code = Files.readString(Path.of(path))
                .replace(blockComment, "")
                .replace(lineComment, "")
            assertFalse(
                "$path must not hardcode user-visible copy; use string resources",
                cjk.containsMatchIn(code)
            )
        }
    }
}
