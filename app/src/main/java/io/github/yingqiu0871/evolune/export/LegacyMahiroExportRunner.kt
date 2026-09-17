package io.github.yingqiu0871.evolune.export

import io.github.yingqiu0871.evolune.application.MahiroJsonV1ExportService
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import kotlinx.coroutines.CancellationException

/** Typed legacy Mahiro export outcome (frozen contract §33/§44 P2-1). */
sealed interface LegacyMahiroExportOutcome {
    data class Success(val json: String) : LegacyMahiroExportOutcome

    data object InvalidData : LegacyMahiroExportOutcome

    data object UnexpectedFailure : LegacyMahiroExportOutcome
}

/**
 * Wraps the pre-existing Mahiro v1 export service so no uncaught serializer/domain
 * `IllegalArgumentException` can reach a production UI callback. The wire format itself is
 * untouched (legacy compatibility surface).
 */
internal class LegacyMahiroExportRunner(
    private val exportFunction: (Double, List<DoseEvent>) -> String
) {
    constructor(exportService: MahiroJsonV1ExportService) : this(exportService::export)

    fun export(weight: Double, events: List<DoseEvent>): LegacyMahiroExportOutcome = try {
        LegacyMahiroExportOutcome.Success(exportFunction(weight, events))
    } catch (error: CancellationException) {
        throw error
    } catch (_: IllegalArgumentException) {
        LegacyMahiroExportOutcome.InvalidData
    } catch (_: RuntimeException) {
        LegacyMahiroExportOutcome.UnexpectedFailure
    }
}
