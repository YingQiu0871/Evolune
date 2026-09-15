package io.github.yingqiu0871.evolune.history.retrospective

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.yingqiu0871.evolune.data.SettingsStore
import io.github.yingqiu0871.evolune.data.isValidBodyWeight
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import io.github.yingqiu0871.evolune.history.AllAvailableHistorySource
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkSource
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * V17-C-04 §4/§6/§8 — retrospective surface ViewModel.
 *
 * One load = one capture + up to three sequential NON-ATOMIC reads (coordinator), published
 * only when the generation token still owns the state:
 *
 * - body-weight gate first: an invalid current body weight performs ZERO history reads;
 * - Read 1 Unavailable/failure stops before any marker read;
 * - CONTENT requires all three reads plus successful marker derivation;
 * - a Read 2/Read 3 failure never publishes partial curve/marker content.
 *
 * Refresh (contract §8): activation-based only. The first composition entry is owned by the
 * initial load; a re-entry and a real stop → start refresh once; a refresh arriving while a
 * load runs is coalesced into exactly one follow-up; a manual retry starts a completely new
 * load/generation with a fresh capture (the rolling window advances). There is no live
 * subscription, no polling, no result persistence and no cache.
 *
 * Boundary: this class consumes only the three approved seams plus the read-only current
 * settings store. It never touches repositories/DAOs/Room, the concrete `HistoryReadService`,
 * the retrospective extractor, parameter resolution or the simulation engine (those live at
 * the composition root or inside the approved seams).
 */
class RetrospectivePkViewModel(
    private val retrospectivePkSource: RetrospectivePkSource,
    private val allAvailableHistorySource: AllAvailableHistorySource,
    private val historyRangeSource: HistoryRangeSource,
    private val settingsStore: SettingsStore,
    private val clock: Clock = Clock.systemUTC(),
    private val displayZone: () -> ZoneId = ZoneId::systemDefault,
    operationScope: CoroutineScope? = null
) : ViewModel() {

    private val scope = operationScope ?: viewModelScope
    private val coordinator = RetrospectivePkSurfaceCoordinator(
        retrospectivePkSource = retrospectivePkSource,
        allAvailableHistorySource = allAvailableHistorySource,
        historyRangeSource = historyRangeSource
    )

    private val initialWindow = captureWindow()
    private val initialZone = displayZone()
    private val _uiState = MutableStateFlow(
        RetrospectivePkUiState(
            windowStart = initialWindow.startInclusive,
            windowEnd = initialWindow.endInclusive,
            displayZone = initialZone,
            phase = RetrospectivePhase.LOADING
        )
    )
    val uiState: StateFlow<RetrospectivePkUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var generation = 0
    private var surfaceShownOnce = false

    /** A refresh that arrived while a load was running: coalesced into one follow-up. */
    private var pendingRefresh = false

    init {
        startLoad()
    }

    // ---------- intents ----------

    /** Retries the current surface with a brand-new load/generation and a fresh capture. */
    fun retry() {
        refresh()
    }

    /**
     * The retrospective surface entered composition. The first entry is owned by the initial
     * load; every later entry refreshes once.
     */
    fun onSurfaceShown() {
        if (!surfaceShownOnce) {
            surfaceShownOnce = true
            return
        }
        refresh()
    }

    /** The app returned to the foreground while this surface is active (real stop → start only). */
    fun onAppForegrounded() {
        refresh()
    }

    // ---------- loading ----------

    private fun refresh() {
        if (loadJob?.isActive == true) {
            pendingRefresh = true
            return
        }
        startLoad()
    }

    private fun startLoad() {
        pendingRefresh = false
        val token = ++generation
        loadJob?.cancel()

        val zone = displayZone()
        val window = captureWindow()
        _uiState.value = _uiState.value.copy(
            windowStart = window.startInclusive,
            windowEnd = window.endInclusive,
            displayZone = zone,
            phase = RetrospectivePhase.LOADING,
            result = null,
            markers = emptyList(),
            failure = null
        )

        loadJob = scope.launch {
            try {
                // §6.3: the body-weight gate runs BEFORE any seam call. Invalid → zero history reads.
                val bodyWeightKg = try {
                    settingsStore.userSettings.first().bodyWeight
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    publish(token) {
                        it.copy(
                            phase = RetrospectivePhase.ERROR,
                            result = null,
                            markers = emptyList(),
                            failure = RetrospectiveLoadFailure.ReadFailure(error)
                        )
                    }
                    return@launch
                }
                if (!isValidBodyWeight(bodyWeightKg)) {
                    publish(token) {
                        it.copy(
                            phase = RetrospectivePhase.ERROR,
                            result = null,
                            markers = emptyList(),
                            failure = RetrospectiveLoadFailure.InvalidBodyWeight
                        )
                    }
                    return@launch
                }

                val capture = RetrospectivePkLoadCapture(
                    capturedAt = window.endInclusive,
                    displayZone = zone,
                    window = window,
                    policy = MedicationOccurrencePolicy(),
                    bodyWeightKg = bodyWeightKg
                )
                when (val outcome = coordinator.load(capture)) {
                    is RetrospectivePkLoadOutcome.Content -> publish(token) {
                        it.copy(
                            phase = RetrospectivePhase.CONTENT,
                            result = outcome.result,
                            markers = outcome.markers,
                            failure = null
                        )
                    }

                    is RetrospectivePkLoadOutcome.Unavailable -> publish(token) {
                        it.copy(
                            phase = RetrospectivePhase.UNAVAILABLE,
                            result = outcome.result,
                            markers = emptyList(),
                            failure = null
                        )
                    }

                    is RetrospectivePkLoadOutcome.Failed -> publish(token) {
                        it.copy(
                            phase = RetrospectivePhase.ERROR,
                            result = null,
                            markers = emptyList(),
                            failure = outcome.failure
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                publish(token) {
                    it.copy(
                        phase = RetrospectivePhase.ERROR,
                        result = null,
                        markers = emptyList(),
                        failure = RetrospectiveLoadFailure.ReadFailure(error)
                    )
                }
            } finally {
                if (token == generation) runPendingRefresh()
            }
        }
    }

    private fun runPendingRefresh() {
        if (!pendingRefresh) return
        pendingRefresh = false
        startLoad()
    }

    /** Publishes only while [token] still owns the current generation (stale-result rejection). */
    private fun publish(
        token: Int,
        transform: (RetrospectivePkUiState) -> RetrospectivePkUiState
    ) {
        if (token != generation) return
        _uiState.value = transform(_uiState.value)
    }

    /**
     * §D-4 fixed window: `[capturedAt − 30 days, capturedAt]`, with `capturedAt` normalized to
     * epoch-millisecond precision. Exactly 720 hours; no calendar-month semantics.
     */
    private fun captureWindow(): RetrospectivePkWindow {
        val capturedAt = Instant.ofEpochMilli(clock.instant().toEpochMilli())
        return RetrospectivePkWindow(
            startInclusive = capturedAt.minus(Duration.ofDays(30)),
            endInclusive = capturedAt
        )
    }
}

/**
 * Factory for the retrospective surface (V17-C-04 §2.5).
 *
 * Boundary note for reviewers: this factory receives ONLY the three approved seams, the
 * read-only [SettingsStore], the clock and the display-zone provider. The concrete
 * `HistoryReadService` is never passed in — it may appear only at the composition root
 * (MainActivity), where the three seams are constructed and handed to this factory. C-04
 * orchestration must not be confused with that composition-root precedent.
 */
class RetrospectivePkViewModelFactory(
    private val retrospectivePkSource: RetrospectivePkSource,
    private val allAvailableHistorySource: AllAvailableHistorySource,
    private val historyRangeSource: HistoryRangeSource,
    private val settingsStore: SettingsStore,
    private val clock: Clock = Clock.systemUTC(),
    private val displayZone: () -> ZoneId = ZoneId::systemDefault
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T = create(modelClass, CreationExtras.Empty)

    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (!modelClass.isAssignableFrom(RetrospectivePkViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class")
        }
        @Suppress("UNCHECKED_CAST")
        return RetrospectivePkViewModel(
            retrospectivePkSource = retrospectivePkSource,
            allAvailableHistorySource = allAvailableHistorySource,
            historyRangeSource = historyRangeSource,
            settingsStore = settingsStore,
            clock = clock,
            displayZone = displayZone
        ) as T
    }
}
