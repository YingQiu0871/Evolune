package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.LabResult
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1Codec
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1DocumentDto
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1DoseEventAdapter
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1ExportMappingResult
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1LabExportMappingResult
import io.github.yingqiu0871.evolune.external.mahiro.v1.MahiroV1LabResultAdapter
import java.time.Clock

class MahiroJsonV1ExportService(
    private val adapter: MahiroV1DoseEventAdapter = MahiroV1DoseEventAdapter(),
    private val labAdapter: MahiroV1LabResultAdapter = MahiroV1LabResultAdapter(),
    clock: Clock = Clock.systemUTC()
) {
    private val codec = MahiroV1Codec(clock)

    fun export(
        weight: Double,
        events: List<DoseEvent>,
        labResults: List<LabResult> = emptyList()
    ): String {
        val projectedEvents = events.mapIndexed { index, event ->
            when (val result = adapter.fromDomain(event)) {
                is MahiroV1ExportMappingResult.Success -> result.event
                is MahiroV1ExportMappingResult.Failure -> throw IllegalArgumentException(
                    "Dose event at index $index cannot be represented by Mahiro JSON v1"
                )
            }
        }
        // Oldest first, matching the order lab results are entered in Mahiro.
        val projectedLabs = labResults
            .sortedWith(compareBy<LabResult> { it.measuredAt }.thenBy { it.id.toString() })
            .mapIndexed { index, lab ->
                when (val result = labAdapter.fromDomain(lab)) {
                    is MahiroV1LabExportMappingResult.Success -> result.result
                    is MahiroV1LabExportMappingResult.Failure -> throw IllegalArgumentException(
                        "Lab result at index $index cannot be represented by Mahiro JSON v1"
                    )
                }
            }
        return codec.encode(
            MahiroV1DocumentDto(
                weight = weight,
                events = projectedEvents,
                labResults = projectedLabs
            )
        )
    }
}
