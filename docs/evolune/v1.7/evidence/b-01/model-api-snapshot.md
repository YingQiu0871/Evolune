# B-01 model / API snapshot (declarations only)
# Extraction rule: for each Insights production Kotlin file, keep lines starting with
#   enum class / data class / class / object / interface / fun / "    val " / "    fun " / "    override fun "
# (verbatim, indentation preserved); KDoc and bodies are omitted.

## InsightsModels.kt

```kotlin
enum class MedicationIdentityKey {
enum class MedicationIdentityStatus {
data class MedicationIdentity(
    val status: MedicationIdentityStatus,
    val key: MedicationIdentityKey? = null
enum class InsightsBindingConfidence {
data class MedicationInsightsSummary(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val recordedIntakeCount: Int,
    val recordedDayCount: Int,
    val matchedOccurrenceCount: Int,
    val unrecordedOccurrenceCount: Int,
    val unmatchedActualIntakeCount: Int,
    val sourceCounts: Map<MedicationIntakeSource, Int>,
    val bindingConfidenceCounts: Map<InsightsBindingConfidence, Int>,
    val perMedicationDoseTotalsMg: Map<MedicationIdentityKey, Double>,
    val unknownIdentityRecordedIntakeCount: Int,
    val containsCurrentTimezoneDerivedDates: Boolean
```

## MedicationIdentityClassifier.kt

```kotlin
object MedicationIdentityClassifier {
    fun classify(matchKey: MedicationMatchKey): MedicationIdentity {
```

## MedicationInsightsAggregator.kt

```kotlin
class InsightsContractViolationException(message: String) : IllegalStateException(message)
interface MedicationInsightsAggregator {
    fun aggregate(range: HistoricalRange): MedicationInsightsSummary
object ReadOnlyMedicationInsightsAggregator : MedicationInsightsAggregator {
    override fun aggregate(range: HistoricalRange): MedicationInsightsSummary =
fun HistoricalRange.toInsightsSummary(): MedicationInsightsSummary =
```
