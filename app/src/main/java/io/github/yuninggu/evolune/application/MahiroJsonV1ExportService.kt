package io.github.yuninggu.evolune.application

import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.external.mahiro.v1.MahiroV1Codec
import io.github.yuninggu.evolune.external.mahiro.v1.MahiroV1DocumentDto
import io.github.yuninggu.evolune.external.mahiro.v1.MahiroV1DoseEventAdapter
import io.github.yuninggu.evolune.external.mahiro.v1.MahiroV1ExportMappingResult
import java.time.Clock

class MahiroJsonV1ExportService(
    private val adapter: MahiroV1DoseEventAdapter = MahiroV1DoseEventAdapter(),
    clock: Clock = Clock.systemUTC()
) {
    private val codec = MahiroV1Codec(clock)

    fun export(weight: Double, events: List<DoseEvent>): String {
        val projectedEvents = events.mapIndexed { index, event ->
            when (val result = adapter.fromDomain(event)) {
                is MahiroV1ExportMappingResult.Success -> result.event
                is MahiroV1ExportMappingResult.Failure -> throw IllegalArgumentException(
                    "Dose event at index $index cannot be represented by Mahiro JSON v1"
                )
            }
        }
        return codec.encode(
            MahiroV1DocumentDto(
                weight = weight,
                events = projectedEvents
            )
        )
    }
}
