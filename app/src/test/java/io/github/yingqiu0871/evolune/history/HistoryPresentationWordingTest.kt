package io.github.yingqiu0871.evolune.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * A-03 §19 wording guard plus the A-03 §2 architectural guard for the History surface.
 *
 * Two independent risks are pinned here:
 * 1. History must never render adherence judgement wording (missed/skipped/non-adherent/...)
 *    except inside the explicitly required neutral disclaimer;
 * 2. The History UI/presentation/state layer must never re-implement the frozen domain
 *    contract — no future filtering, no `scheduledAt` vs `now` comparison, no status
 *    filtering, no matcher, DAO or repository access, no date/provenance re-derivation.
 */
class HistoryPresentationWordingTest {

    private companion object {
        val BLOCK_COMMENT = Regex("/" + "\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("//[^" + "\\n]*")
    }

    private val uiSources = listOf(
        "history/HistoryPresentation.kt",
        "history/HistoryUiModels.kt",
        "history/HistoryViewModel.kt",
        "history/HistoryFormatting.kt",
        "history/HistoryRangeSource.kt",
        "ui/screens/HistoryScreen.kt"
    )

    @Test
    fun `history labels never use adherence judgement wording`() {
        val labels = historyStrings().filterKeys { name ->
            name.startsWith("history_status_") ||
                name.startsWith("history_summary_") ||
                name.startsWith("history_label_") ||
                name.startsWith("history_cell_") ||
                name.startsWith("history_empty_") ||
                name.startsWith("history_error_") ||
                name.startsWith("history_loading") ||
                name.startsWith("history_month_") ||
                name.startsWith("history_day_") ||
                name.startsWith("history_weekday_") ||
                name.startsWith("history_source_") ||
                name == "history_title" ||
                name == "nav_history"
        }
        assertTrue("history labels must exist", labels.size >= 25)

        val forbidden = listOf(
            "Missed", "missed", "Skipped", "skipped", "Forgot", "forgot",
            "Non-adherent", "Nonadherent", "non-adherent", "adherence", "Adherence",
            "漏服", "漏吃", "跳过", "忘记", "未依从", "依从率", "依从性"
        )
        labels.forEach { (name, text) ->
            forbidden.forEach { token ->
                assertFalse("$name must not contain '$token': $text", text.contains(token))
            }
        }
    }

    @Test
    fun `the neutral no-intake caveat is phrased as a disclaimer`() {
        val notes = historyStrings()
        val caveat = notes.getValue("history_note_not_necessarily_missed")

        assertTrue(
            "the caveat must explicitly deny the blaming reading: $caveat",
            caveat.contains("不一定")
        )
        assertFalse(caveat.contains("依从"))
        assertFalse(caveat.contains("adherence"))
        notes.filterKeys { it.startsWith("history_note_") && it != "history_note_not_necessarily_missed" }
            .forEach { (name, text) ->
                assertFalse("$name must not be blaming: $text", text.contains("漏服"))
                assertFalse("$name must not be blaming: $text", text.contains("Missed"))
            }
    }

    @Test
    fun `the inferred match note no longer exists in any locale authority`() {
        val notes = historyStrings()

        // Slice D: the explanatory sentence was removed from production AND from both locale
        // authorities; re-introducing it requires a deliberate new decision.
        assertFalse(
            "history_note_inferred_match must not exist after Slice D",
            notes.containsKey("history_note_inferred_match")
        )
        notes.filterKeys { it.startsWith("history_note_") }.forEach { (name, text) ->
            listOf("旧版", "老版本", "legacy", "Legacy").forEach { token ->
                assertFalse("$name must not claim a legacy origin: $text", text.contains(token))
            }
        }
        // The legacy-named key of the first candidate must be gone.
        assertFalse(notes.containsKey("history_note_legacy_context"))
    }

    @Test
    fun `history presentation never filters the future or re-derives domain facts`() {
        val forbidden = listOf(
            "futureOccurrences",
            "FutureOccurrenceContext",
            "isAfter(now",
            "isAfter(state.now",
            "scheduledAt.isAfter",
            "scheduledAt.isBefore",
            "MedicationOccurrenceStatus.UPCOMING",
            "MedicationOccurrenceStatus.DUE",
            "status ==",
            "MedicationOccurrencePresentation",
            "MedicationOccurrenceMatcher",
            "MedicationOccurrenceGenerator",
            "DoseEventRepository",
            "MedicationPlanRepository",
            "DoseEventDao",
            "MedicationPlanDao",
            "AppDatabase",
            "Room",
            "ZoneId.systemDefault()"
        )
        uiSources.forEach { relative ->
            // Comments may *describe* the boundary; only executable code is checked.
            val code = code(relative)
            forbidden.forEach { token ->
                assertFalse("$relative must not contain '$token'", code.contains(token))
            }
        }
    }

    @Test
    fun `only the shared rule decides whether an actual intake shows its full date`() {
        val presentation = code("history/HistoryPresentation.kt")

        // Both card paths must use the single decision API ...
        assertEquals(
            2,
            presentation.split("actualTime = HistoryFormatting.actualTimestampPresentation(").size - 1
        )
        // ... and no forbidden signal may decide it (A-03-UI-R1 P1).
        assertFalse(presentation.contains("needsFullDate = entry.crossesLocalDateBoundary"))
        assertFalse(presentation.contains("needsFullDate = false"))
        assertFalse(presentation.contains("needsFullDate = entry.matchProvenance"))
        assertFalse(presentation.contains("needsFullDate = entry.event.localDate"))
        // The only other needsFullDate source is the current-schedule-date rule.
        assertEquals(
            2,
            presentation
                .split("needsFullDate = entry.occurrence.scheduledLocalDateTime.toLocalDate() != entry.displayDate")
                .size - 1
        )
    }

    @Test
    fun `ui reads history only through the history read service seam`() {
        val factory = source("history/HistoryViewModel.kt")
        assertTrue(factory.contains("HistoryRangeSource"))
        assertTrue(factory.contains("historyReadService.readRange("))

        val screen = source("ui/screens/HistoryScreen.kt")
        assertTrue(screen.contains("HistoryPresentation.present(state)"))
        assertFalse(screen.contains("readRange("))
    }

    @Test
    fun `calendar cells are the only place where a calendar date is disabled`() {
        val presentation = source("history/HistoryPresentation.kt")

        // The only date comparison allowed is the calendar interaction rule for future cells.
        assertTrue(presentation.contains("isEnabled = !date.isAfter(state.today)"))
        assertEquals(1, presentation.split("isAfter(state.today)").size - 1)
    }

    private fun source(relativePath: String): String = Files.readString(
        Path.of("src/main/java/io/github/yingqiu0871/evolune/$relativePath")
    )

    /** Source with block and line comments removed, so KDoc cannot mask or trip the guard. */
    private fun code(relativePath: String): String = source(relativePath)
        .replace(BLOCK_COMMENT, "")
        .replace(LINE_COMMENT, "")

    /** name -> text for every `history_*` string, across the default and zh-rCN resources. */
    private fun historyStrings(): Map<String, String> {
        val paths = listOf(
            "src/main/res/values/strings.xml",
            "src/main/res/values-zh-rCN/strings.xml"
        )
        val result = linkedMapOf<String, String>()
        paths.forEach { path ->
            val text = Files.readString(Path.of(path))
            Regex("<string name=\"(history_[a-z_0-9]+)\">([^<]*)</string>")
                .findAll(text)
                .forEach { result[it.groupValues[1]] = it.groupValues[2] }
        }
        return result
    }
}
