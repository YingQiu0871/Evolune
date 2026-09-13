# B-02 state / API snapshot (declarations only)
# Extraction rule: for each listed Kotlin file keep lines starting with
#   enum class / sealed interface / data class / data object / class / object / interface / fun /
#   "    val " / "    fun " / "    override fun " / "    data class " / "    data object " (verbatim).

## InsightsRangeSelection.kt

```kotlin
sealed interface InsightsRangeSelection {
    data object Last7Days : InsightsRangeSelection
    data object Last30Days : InsightsRangeSelection
    data object Last90Days : InsightsRangeSelection
    data object CurrentMonth : InsightsRangeSelection
    data class Custom(val startDate: LocalDate, val endDate: LocalDate) : InsightsRangeSelection
    val isRelativeToToday: Boolean
enum class InsightsRangeValidationError {
sealed interface InsightsRangeResolution {
    data class Resolved(val startDate: LocalDate, val endDate: LocalDate) : InsightsRangeResolution
    data class Invalid(val error: InsightsRangeValidationError) : InsightsRangeResolution
object InsightsRangeResolver {
    fun resolve(selection: InsightsRangeSelection, today: LocalDate): InsightsRangeResolution =
```

## InsightsUiState.kt

```kotlin
enum class InsightsPhase {
sealed interface InsightsLoadFailure {
    data class ReadFailure(val cause: Throwable) : InsightsLoadFailure
    data class ContractViolation(val cause: Throwable) : InsightsLoadFailure
data class InsightsUiState(
    val selection: InsightsRangeSelection = InsightsRangeSelection.DEFAULT,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val today: LocalDate,
    val displayZone: ZoneId,
    val phase: InsightsPhase = InsightsPhase.LOADING,
    val summary: MedicationInsightsSummary? = null,
    val failure: InsightsLoadFailure? = null,
    val validationError: InsightsRangeValidationError? = null
```

## InsightsViewModel.kt

```kotlin
class InsightsViewModel(
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()
    fun selectRange(selection: InsightsRangeSelection) {
    fun retry() {
    fun onSurfaceShown() {
    fun onAppForegrounded() {
class InsightsViewModelFactory(
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create(modelClass, CreationExtras.Empty)
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
```

## InsightsSurfaceLifecycle.kt

```kotlin
fun InsightsSurfaceLifecycle(viewModel: InsightsViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
```
