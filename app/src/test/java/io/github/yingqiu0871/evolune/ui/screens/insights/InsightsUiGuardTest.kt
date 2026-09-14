package io.github.yingqiu0871.evolune.ui.screens.insights

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * v1.7-B-03 §37/§38: the Insights UI may not invent metrics and may not recompute frozen ones.
 *
 * The guard scans the Insights UI sources and the shipped string resources. Its token lists are
 * declared here, so a token cannot hide inside the file that scans for it.
 */
class InsightsUiGuardTest {

    private val uiDir = Path.of("src/main/java/io/github/yingqiu0871/evolune/ui/screens/insights")

    private val uiSources: String by lazy {
        Files.walk(uiDir).use { paths ->
            paths.filter(Files::isRegularFile)
                .filter { it.toString().endsWith(".kt") }
                .map(Files::readString)
                .toList()
                .joinToString("\n")
        }
    }

    private val defaultStrings: String by lazy {
        Files.readString(Path.of("src/main/res/values/strings.xml"))
    }

    private val zhStrings: String by lazy {
        Files.readString(Path.of("src/main/res/values-zh-rCN/strings.xml"))
    }

    private fun assertAbsent(source: String, token: String, what: String) {
        val pattern = Regex("(?<![A-Za-z0-9_])" + Regex.escape(token) + "(?![A-Za-z0-9_])", RegexOption.IGNORE_CASE)
        assertFalse("$what must not mention '$token'", pattern.containsMatchIn(source))
    }

    @Test
    fun `no metric recomputation primitive exists in the Insights UI`() {
        listOf(
            ".groupBy(",
            ".sumOf(",
            ".mapValues(",
            "HistoryRangeSource",
            "HistoryReadService",
            "AppDatabase",
            "MedicationIdentityClassifier",
            "MedicationOccurrenceMatcher",
            "MedicationOccurrenceGenerator",
            "InsightsContractViolationException"
        ).forEach { assertAbsent(uiSources, it, "the Insights UI") }
    }

    @Test
    fun `the UI consumes the frozen summary fields instead of restating them`() {
        // The presentation mapper is the only file allowed to read summary fields, and it must read
        // them straight off the aggregate.
        val presentation = Files.readString(uiDir.resolve("InsightsPresentation.kt"))
        listOf(
            "summary.recordedIntakeCount",
            "summary.recordedDayCount",
            "summary.matchedOccurrenceCount",
            "summary.unrecordedOccurrenceCount",
            "summary.sourceCounts",
            "summary.bindingConfidenceCounts",
            "summary.perMedicationDoseTotalsMg",
            "summary.unknownIdentityRecordedIntakeCount",
            "summary.containsCurrentTimezoneDerivedDates"
        ).forEach { field ->
            assertTrue("$field must come from the frozen summary", presentation.contains(field))
        }
        // and the render path must go through the mapper instead of touching the summary directly
        // (the previews file only builds sample state, which §31 explicitly allows)
        assertFalse(
            "InsightsScreen.kt must not read aggregate fields directly",
            Files.readString(uiDir.resolve("InsightsScreen.kt")).contains("summary.")
        )
    }

    @Test
    fun `no forbidden metric vocabulary is shipped as an Insights string`() {
        val forbiddenEnglish = listOf(
            "adherence",
            "compliance",
            "completion rate",
            "missed dose",
            "skipped",
            "on time",
            "late",
            "delay",
            "percentage",
            "percent"
        )
        val insightsStrings = insightsStringEntries(defaultStrings) + insightsStringEntries(zhStrings)
        assertTrue("the Insights copy must be present", insightsStrings.isNotEmpty())
        forbiddenEnglish.forEach { token ->
            assertFalse(
                "no Insights string may use '$token'",
                insightsStrings.any { it.contains(token, ignoreCase = true) }
            )
        }
    }

    @Test
    fun `no forbidden Chinese metric vocabulary is shipped as an Insights string`() {
        val forbiddenChinese = listOf("漏服", "依从率", "准时率", "延迟", "完成率", "合规")
        val insightsStrings = insightsStringEntries(defaultStrings) + insightsStringEntries(zhStrings)
        forbiddenChinese.forEach { token ->
            assertFalse(
                "no Insights string may use '$token'",
                insightsStrings.any { it.contains(token) }
            )
        }
    }

    @Test
    fun `the frozen disclosure and the linked-record subtitle are shipped verbatim`() {
        listOf(defaultStrings, zhStrings).forEach { xml ->
            assertTrue(
                "the schedule-context disclosure must ship",
                xml.contains("计划时点由当前方案上下文生成。")
            )
            assertTrue(
                "the ambiguity disclosure must ship",
                xml.contains("没有记录并不能证明当时没有服药。")
            )
            assertTrue(
                "the frozen coverage subtitle must ship",
                xml.contains("与生成的计划时点关联的记录")
            )
        }
    }

    @Test
    fun `the Insights copy never names an unrecorded occurrence with blame`() {
        val insightsStrings = insightsStringEntries(defaultStrings) + insightsStringEntries(zhStrings)
        listOf("missed", "skipped", "failed", "forgot", "overdue", "漏", "跳过", "忘记", "未完成").forEach { token ->
            assertFalse(
                "unrecorded occurrences must not be named '$token'",
                insightsStrings.any { it.contains(token, ignoreCase = true) }
            )
        }
    }

    /** All `<string name="insights_*">` values of one resource file. */
    private fun insightsStringEntries(xml: String): List<String> =
        Regex("<string name=\"insights_[^\"]*\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()
}
