package io.github.yingqiu0871.evolune.ui.screens.insights

import org.junit.Assert.assertEquals
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

    /**
     * The UI sources with comments removed: a KDoc sentence that *forbids* a metric is allowed,
     * an identifier that implements one is not (v1.7-B-04 §33).
     */
    private val uiCode: String by lazy {
        uiSources
            .replace(Regex("""/[*][\s\S]*?[*]/"""), " ")
            .replace(Regex("""//[^\n]*"""), " ")
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

    // ---------- v1.7-B-04 §12/§20 hardening ----------

    @Test
    fun `no chart or canvas primitive exists and no chart dependency is declared`() {
        listOf("Canvas(", "drawArc", "drawPath", "drawScope", "Path(").forEach {
            assertAbsent(uiSources, it, "the Insights UI")
        }
        val versions = Files.readString(Path.of("../gradle/libs.versions.toml"))
        val appBuild = Files.readString(Path.of("build.gradle.kts"))
        listOf("vico", "mpandroidchart", "compose-charts", "koalaplot", "charts").forEach { lib ->
            assertFalse(
                "no chart library may be declared ($lib)",
                versions.contains(lib, ignoreCase = true) || appBuild.contains(lib, ignoreCase = true)
            )
        }
        // and the presentation layer keeps consuming fixed-size maps only: it never touches the
        // per-day HistoricalRange at all (no daily series may be derived here)
        val mapper = Files.readString(uiDir.resolve("InsightsPresentation.kt"))
        assertFalse("the mapper must not read the range", mapper.contains("HistoricalRange"))
        assertFalse("the mapper must not read range days", mapper.contains(".days"))
    }

    @Test
    fun `no forbidden metric identifier exists in the Insights UI sources`() {
        listOf(
            "adherence",
            "compliance",
            "completionRate",
            "missedDose",
            "skippedDose",
            "onTimeRate",
            "lateRate",
            "timingDifference",
            "averageDelay",
            "percentage",
            "percent",
            "ratio"
        ).forEach { token ->
            assertAbsent(uiCode, token, "the Insights UI code")
        }
    }

    @Test
    fun `the shipped default locale copy is Chinese and both locales are complete`() {
        // honest documentation of the language strategy: values/ is the Chinese default, so the
        // English-token guard has limited value on its own and the Chinese token guard matters most
        val cjk = Regex("[\\u4e00-\\u9fff]")
        assertTrue(
            "the default Insights copy is expected to be Chinese",
            cjk.containsMatchIn(insightsValue(defaultStrings, "insights_empty"))
        )
        assertEquals(
            "both locale files must expose the same Insights keys",
            insightsKeys(defaultStrings),
            insightsKeys(zhStrings)
        )
        assertTrue("localized Insights copy must exist", insightsKeys(defaultStrings).size >= 40)
    }

    @Test
    fun `the accessibility description resources keep their placeholder contract`() {
        listOf(defaultStrings, zhStrings).forEach { xml ->
            assertEquals(
                listOf("%1\$s", "%2\$d"),
                Regex("%\\d\\\$[sd]").findAll(insightsValue(xml, "insights_count_row_description"))
                    .map { it.value }.toList().sorted()
            )
            assertEquals(
                listOf("%1\$s", "%2\$s"),
                Regex("%\\d\\\$[sd]").findAll(insightsValue(xml, "insights_value_row_description"))
                    .map { it.value }.toList().sorted()
            )
        }
    }

    private fun insightsKeys(xml: String): Set<String> =
        Regex("<string name=\"(insights_[^\"]*)\"").findAll(xml).map { it.groupValues[1] }.toSet()

    private fun insightsValue(xml: String, key: String): String =
        Regex("<string name=\"$key\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)
            ?.groupValues
            ?.get(1)
            ?: error("missing $key")

    /** All `<string name="insights_*">` values of one resource file. */
    private fun insightsStringEntries(xml: String): List<String> =
        Regex("<string name=\"insights_[^\"]*\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()
}
