package io.github.yingqiu0871.evolune.ui.screens.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

/**
 * V17-D-04 §35 — architecture / forbidden-surface guards (F1–F32) and the functional-string
 * wording guard over the D-04 production files.
 *
 * A reference to the approved read seam (`HistoryRangeSource`) and to the closed D-01/D-03 types
 * is required; everything on the forbidden list must be absent from the code (comments are
 * stripped before scanning, so documentation may still name the closed surfaces).
 */
class TimelineUiArchitectureGuardTest {

    private val d04Files = listOf(
        "src/main/java/io/github/yingqiu0871/evolune/history/timeline/TimelineViewModel.kt",
        "src/main/java/io/github/yingqiu0871/evolune/ui/screens/timeline/TimelineScreen.kt",
        "src/main/java/io/github/yingqiu0871/evolune/ui/screens/timeline/TimelinePresentation.kt",
        "src/main/java/io/github/yingqiu0871/evolune/ui/screens/timeline/TimelineSurfaceLifecycle.kt"
    )

    private val vmFile = d04Files[0]
    private val screenFile = d04Files[1]
    private val presentationFile = d04Files[2]
    private val lifecycleFile = d04Files[3]

    private val blockComment = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val lineComment = Regex("//[^\\n]*")

    private fun code(path: String): String = Files.readString(Path.of(path))
        .replace(blockComment, "")
        .replace(lineComment, "")

    private fun assertAbsentInAll(terms: List<String>) {
        d04Files.forEach { path ->
            val fileCode = code(path)
            terms.forEach { term ->
                assertFalse("$path must not reference '$term'", fileCode.contains(term))
            }
        }
    }

    // ---------- F1–F4: closed surfaces only, no new read/matcher/PK path ----------

    @Test
    fun `F1 the closed D-01 and D-03 types are consumed but never redeclared`() {
        d04Files.forEach { path ->
            val fileCode = code(path)
            assertFalse(fileCode.contains("data class TimelineMonthRequest"))
            assertFalse(fileCode.contains("class TimelineRangeCoordinator("))
            assertFalse(fileCode.contains("object TimelineProjectionBuilder"))
            assertFalse(fileCode.contains("data class TimelineReadModel"))
        }
        assertTrue(code(vmFile).contains("TimelineRangeCoordinator"))
        assertTrue(code(vmFile).contains("HistoryRangeSource"))
    }

    @Test
    fun `F2 no direct repository DAO or Room access exists`() {
        assertAbsentInAll(
            listOf(
                "HistoryReadService",
                "data.repository",
                "DoseEventDao",
                "MedicationPlanDao",
                "DoseEventRepository",
                "MedicationPlanRepository",
                "AppDatabase",
                "androidx.room",
                "@dao",
                "@entity",
                "@database"
            )
        )
    }

    @Test
    fun `F3 no matcher generator or second truth store exists`() {
        assertAbsentInAll(
            listOf(
                "MedicationOccurrenceMatcher",
                "MedicationOccurrenceGenerator",
                "MedicationOccurrencePolicy",
                "truthStore",
                "newReader"
            )
        )
    }

    @Test
    fun `F4 no Phase-C or PK coupling exists`() {
        assertAbsentInAll(
            listOf(
                "history.retrospective",
                "RetrospectivePk",
                "SimulationEngine",
                "ThreeCompartmentModel",
                "ParameterResolver",
                "io.github.yingqiu0871.evolune.pk."
            )
        )
    }

    // ---------- F5–F9: no forbidden semantics or identity ----------

    @Test
    fun `F5 no timing delta or adherence semantics exists in code`() {
        val forbiddenWords = listOf(
            "timingDelta", "onTime", "overdue", "adherence", "compliance", "punctuality",
            "Duration.between", "missed", "skipped", "delta"
        )
        assertAbsentInAll(forbiddenWords)
        val wordBoundary = Pattern.compile("\\b(late|early)\\b", Pattern.CASE_INSENSITIVE)
        d04Files.forEach { path ->
            assertFalse("$path must not contain early/late semantics", wordBoundary.matcher(code(path)).find())
        }
    }

    @Test
    fun `F6 no future Timeline rows are reachable`() {
        assertAbsentInAll(listOf("futureOccurrences", "FutureOccurrence", "futureRows"))
    }

    @Test
    fun `F7 F8 no planName identity and no antiandrogen guessing exists`() {
        assertAbsentInAll(listOf("planName", "antiAndrogen", "antiandrogenGuess"))
        val presentation = code(presentationFile)
        assertTrue(presentation.contains("timeline_identity_unavailable"))
        assertTrue(presentation.contains("timeline_identity_partial"))
    }

    @Test
    fun `F9 internal comparator and identity fields are never rendered`() {
        listOf(screenFile, presentationFile).forEach { path ->
            val fileCode = code(path)
            listOf("planId", "slotPosition", "slotId", "rowId", "occurrenceId", "eventId", "sortKey").forEach { term ->
                assertFalse("$path must never touch '$term'", fileCode.contains(term))
            }
        }
    }

    // ---------- F10–F14: no orchestration duplication, timers, persistence, literals, scope ----------

    @Test
    fun `F10 F26 the VM logical intent is not a second state machine`() {
        val vm = code(vmFile)
        listOf(
            "TimelineRangePhase",
            "TimelineReadModel",
            "TimelineDay(",
            "readInFlight",
            "pendingContext",
            "generationCounter"
        ).forEach { term ->
            assertFalse("the VM must not own '$term'", vm.contains(term))
        }
        assertTrue(vm.contains("TimelineUiRequestIntent"))
    }

    @Test
    fun `F11 no polling timer or live subscription exists`() {
        assertAbsentInAll(listOf("delay(", "Timer(", "repeat(", "while (true)"))
        d04Files.forEach { path ->
            assertFalse(
                "$path must not start a live collection",
                Regex("\\bcollect\\(").containsMatchIn(code(path))
            )
        }
    }

    @Test
    fun `F12 no persisted Timeline state store exists`() {
        assertAbsentInAll(listOf("SavedStateHandle", "DataStore", "persist", "lastViewedTimeline"))
    }

    @Test
    fun `F13 user-visible copy never appears as a literal in the UI code`() {
        val cjk = Regex("[\\u4e00-\\u9fff]")
        d04Files.forEach { path ->
            assertFalse("$path must not contain literal user-visible copy", cjk.containsMatchIn(code(path)))
        }
        val presentation = code(presentationFile)
        assertTrue(presentation.contains("R.string.timeline_today"))
        assertTrue(presentation.contains("R.string.timeline_yesterday"))
        assertTrue(code(screenFile).contains("R.string.timeline_section_date_weekday"))
    }

    @Test
    fun `F14 no global or internally created coroutine scope exists`() {
        assertAbsentInAll(listOf("GlobalScope", "runBlocking", "CoroutineScope(SupervisorJob"))
    }

    // ---------- F15–F21: navigation, selection and centering boundaries ----------

    @Test
    fun `F15 F16 the Timeline is a sub-route with no bottom tab and no date argument`() {
        val navigation = Files.readString(
            Path.of("src/main/java/io/github/yingqiu0871/evolune/navigation/AppNavigation.kt")
        )
        // v1.7.3: the sub-route identity lives in the single chrome policy file.
        val chromePolicy = Files.readString(
            Path.of("src/main/java/io/github/yingqiu0871/evolune/navigation/PrimaryNavigationChrome.kt")
        )
        assertTrue(
            "the route must carry no argument",
            chromePolicy.contains("internal const val TIMELINE_ROUTE = \"timeline\"")
        )
        assertFalse(navigation.contains("\"timeline/"))
        assertTrue(
            "Timeline must stay a chrome-hiding sub-route",
            chromePolicy.contains("FULL_SCREEN_ROUTES") &&
                chromePolicy.contains("TIMELINE_ROUTE")
        )
        assertTrue(navigation.contains("PrimaryNavigationChrome.shows(currentRoute)"))

        val screen = Files.readString(
            Path.of("src/main/java/io/github/yingqiu0871/evolune/navigation/Screen.kt")
        )
        assertFalse("no sixth bottom tab may exist", screen.lowercase().contains("timeline"))
        assertEquals(
            "the bottom-tab enum must keep exactly five entries",
            5,
            Regex("""[A-Z_]{2,}\(""").findAll(screen).count()
        )
    }

    @Test
    fun `F17 the selection never filters the model into a single day`() {
        listOf(screenFile, presentationFile).forEach { path ->
            val fileCode = code(path)
            assertFalse("$path must not filter sections by the selection", fileCode.contains("filter {"))
            assertFalse("$path must not filter days by the selection", fileCode.contains(".filter("))
        }
        assertTrue(code(presentationFile).contains("days.asReversed()"))
    }

    @Test
    fun `F18 canonical D-01 row order is never re-sorted`() {
        d04Files.forEach { path ->
            val fileCode = code(path)
            listOf("sortedWith(", "sortedBy", ".sorted(", ".reversed(").forEach { term ->
                assertFalse("$path must not re-sort rows with '$term'", fileCode.contains(term))
            }
        }
    }

    @Test
    fun `F19 no manual nudge or asymmetric centering exists`() {
        val screen = code(screenFile)
        listOf("offset(", "baselineShift", "absoluteOffset", "nudge").forEach { term ->
            assertFalse("the screen must not contain '$term'", screen.contains(term))
        }
        assertTrue("structural centering is required", screen.contains("contentAlignment = Alignment.Center"))
        assertTrue(screen.contains("horizontalAlignment = Alignment.CenterHorizontally"))
    }

    @Test
    fun `F20 the next-month slot is structurally reserved, never conditionally removed`() {
        val screen = code(screenFile)
        assertFalse("the slot must not be conditional", screen.contains("if (model.canGoToNextMonth)"))
        assertTrue(screen.contains("enabled = model.canGoToNextMonth"))
        assertTrue(
            "both navigation slots must use the same fixed size",
            Regex("MONTH_CONTROL_SLOT").findAll(screen).count() >= 3
        )
    }

    @Test
    fun `F21 the Timeline body is not globally centered`() {
        val screen = code(screenFile)
        assertEquals(
            "only the month title may use centered text alignment",
            1,
            Regex("textAlign = TextAlign\\.Center").findAll(screen).count()
        )
        val sectionBlock = screen
            .substringAfter("private fun TimelineSection(")
            .substringBefore("private fun TimelineRowCard(")
        assertFalse(sectionBlock.contains("Alignment.CenterHorizontally"))
    }

    // ---------- F22/F27–F32: clock and zone authority ----------

    @Test
    fun `F22 F27 F28 the renderer never observes a clock or a system zone`() {
        listOf(screenFile, presentationFile, lifecycleFile).forEach { path ->
            val fileCode = code(path)
            listOf("LocalDate.now", "Instant.now", "systemDefault", "Clock.system", "LocalTime.now").forEach { term ->
                assertFalse("$path must not read '$term'", fileCode.contains(term))
            }
        }
    }

    @Test
    fun `F25 F29 F30 command targeting never uses the last published state`() {
        val vm = code(vmFile)
        assertFalse("no command may read the published state", vm.contains("state.value"))
        assertFalse("the selection may not consult a live wall clock", vm.contains("LocalDate.now"))
        assertFalse(vm.contains("Instant.now"))
        assertFalse(vm.contains("ZoneId.systemDefault()"))
        assertTrue("same-zone commands use the frozen refresh path", vm.contains("coordinator.refresh("))
        assertTrue("changed-zone commands submit exactly one load", vm.contains("coordinator.load("))
    }

    @Test
    fun `F31 every generation-producing command advances requestToday from the same capture`() {
        val vm = code(vmFile)
        assertTrue(
            "activation, load and zone-change commands must all mirror requestToday",
            Regex("requestToday = capture\\.today").findAll(vm).count() >= 3
        )
    }

    @Test
    fun `F32 the logical requestToday is never rendered as the snapshot today`() {
        listOf(screenFile, presentationFile).forEach { path ->
            val fileCode = code(path)
            assertFalse("$path must not render the logical requestToday", fileCode.contains("requestToday"))
        }
        assertTrue(code(presentationFile).contains("state.today"))
    }

    // ---------- functional strings: wording, parity, forbidden semantics ----------

    @Test
    fun `functional timeline strings never contain forbidden semantics and stay in parity`() {
        val values = Files.readString(Path.of("src/main/res/values/strings.xml"))
        val zh = Files.readString(Path.of("src/main/res/values-zh-rCN/strings.xml"))

        val forbidden = listOf(
            "missed", "skipped", "overdue", "late", "early", "adherence", "compliance",
            "punctuality", "delta", "漏服", "错过", "迟到", "逾期", "依从", "准点", "未服"
        )
        val timelineEntries = Regex("<string name=\"(timeline_[A-Za-z0-9_]+)\">([^<]*)</string>")
        val valuesEntries = timelineEntries.findAll(values).associate { it.groupValues[1] to it.groupValues[2] }
        val zhEntries = timelineEntries.findAll(zh).associate { it.groupValues[1] to it.groupValues[2] }

        assertTrue("the functional Timeline keys must exist", valuesEntries.size >= 20)
        assertEquals(
            "both resource files must carry the same functional keys",
            valuesEntries.keys,
            zhEntries.keys
        )
        (valuesEntries + zhEntries).forEach { (key, text) ->
            forbidden.forEach { term ->
                assertFalse("$key must not contain '$term'", text.contains(term, ignoreCase = true))
            }
        }
        assertTrue(valuesEntries.containsKey("timeline_no_recorded_intake"))
        assertTrue(valuesEntries.containsKey("timeline_today"))
        assertTrue(valuesEntries.containsKey("timeline_yesterday"))
    }

    @Test
    fun `the lifecycle bridge keeps the approved first-entry and stop-start discipline`() {
        val lifecycle = code(lifecycleFile)
        assertTrue(lifecycle.contains("LaunchedEffect(Unit) { viewModel.onSurfaceShown() }"))
        assertTrue(lifecycle.contains("Lifecycle.Event.ON_STOP"))
        assertTrue(lifecycle.contains("Lifecycle.Event.ON_START"))
        assertTrue(lifecycle.contains("viewModel.onAppForegrounded()"))
    }

    // ---------- V17-D-05 accessibility/localization guards (DG10/DG11/DG18/DG19/DG20/DG21) ----------

    @Test
    fun `D05 timeline accessibility keys exist with full weekdays in both files`() {
        val values = Files.readString(Path.of("src/main/res/values/strings.xml"))
        val zh = Files.readString(Path.of("src/main/res/values-zh-rCN/strings.xml"))
        val pattern = Regex("<string name=\"(timeline_a11y_[A-Za-z0-9_]+)\">([^<]*)</string>")
        val valuesEntries = pattern.findAll(values).associate { it.groupValues[1] to it.groupValues[2] }
        val zhEntries = pattern.findAll(zh).associate { it.groupValues[1] to it.groupValues[2] }

        assertTrue("the accessibility key family must exist", valuesEntries.size >= 12)
        assertEquals(
            "accessibility keys must exist in both resource authorities",
            valuesEntries.keys,
            zhEntries.keys
        )
        val weekdays = listOf(
            "timeline_a11y_weekday_mon" to "星期一",
            "timeline_a11y_weekday_tue" to "星期二",
            "timeline_a11y_weekday_wed" to "星期三",
            "timeline_a11y_weekday_thu" to "星期四",
            "timeline_a11y_weekday_fri" to "星期五",
            "timeline_a11y_weekday_sat" to "星期六",
            "timeline_a11y_weekday_sun" to "星期日"
        )
        weekdays.forEach { (key, expected) ->
            assertEquals("$key (values)", expected, valuesEntries[key])
            assertEquals("$key (zh-rCN)", expected, zhEntries[key])
        }
        listOf(
            "timeline_a11y_day_format",
            "timeline_a11y_date_weekday",
            "timeline_a11y_day_cell_today",
            "timeline_a11y_day_cell_disabled",
            "timeline_a11y_row_side"
        ).forEach { key ->
            assertTrue("$key must exist in values/", valuesEntries.containsKey(key))
            assertTrue("$key must exist in values-zh-rCN/", zhEntries.containsKey(key))
        }
    }

    @Test
    fun `D05 placeholder parity holds for every timeline formatted key`() {
        val values = Files.readString(Path.of("src/main/res/values/strings.xml"))
        val zh = Files.readString(Path.of("src/main/res/values-zh-rCN/strings.xml"))
        val pattern = Regex("<string name=\"(timeline_[A-Za-z0-9_]+)\">([^<]*)</string>")
        val valuesEntries = pattern.findAll(values).associate { it.groupValues[1] to it.groupValues[2] }
        val zhEntries = pattern.findAll(zh).associate { it.groupValues[1] to it.groupValues[2] }
        val placeholderPattern = Regex("%(\\d+)\\$([sd])")

        assertEquals(valuesEntries.keys, zhEntries.keys)
        valuesEntries.keys.forEach { key ->
            val valuesPlaceholders = placeholderPattern.findAll(valuesEntries.getValue(key))
                .map { it.groupValues[1] to it.groupValues[2] }
                .toList()
            val zhPlaceholders = placeholderPattern.findAll(zhEntries.getValue(key))
                .map { it.groupValues[1] to it.groupValues[2] }
                .toList()
            assertEquals("placeholder sequence mismatch for $key", valuesPlaceholders, zhPlaceholders)
        }
        assertEquals(
            listOf("1" to "d", "2" to "d"),
            placeholderPattern.findAll(valuesEntries.getValue("timeline_a11y_day_format"))
                .map { it.groupValues[1] to it.groupValues[2] }
                .toList()
        )
        assertEquals(
            listOf("1" to "s", "2" to "s", "3" to "s", "4" to "s"),
            placeholderPattern.findAll(valuesEntries.getValue("timeline_a11y_row_side"))
                .map { it.groupValues[1] to it.groupValues[2] }
                .toList()
        )
    }

    @Test
    fun `D05 no programmatic focus or live-region APIs exist in the hardened sources`() {
        val forbidden = listOf(
            "requestFocus(",
            "moveFocus(",
            "FocusManager",
            "focusRequester",
            "liveRegion",
            "LiveRegion",
            "announceForAccessibility("
        )
        listOf(vmFile, screenFile, presentationFile, lifecycleFile).forEach { path ->
            val fileCode = code(path)
            forbidden.forEach { term ->
                assertFalse("$path must not contain '$term' (DG9/DG10)", fileCode.contains(term))
            }
        }
        // The frozen speech vocabulary is resource-backed and must include full weekday names.
        assertTrue(code(screenFile).contains("timeline_a11y_row_side"))
        assertTrue(code(screenFile).contains("SemanticsProperties.TestTag"))
    }
}
