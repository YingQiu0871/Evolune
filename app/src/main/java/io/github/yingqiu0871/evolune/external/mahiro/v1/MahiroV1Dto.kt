package io.github.yingqiu0871.evolune.external.mahiro.v1

data class MahiroV1DocumentDto(
    val weight: Double?,
    val events: List<MahiroV1DoseEventDto>
)

data class MahiroV1DoseEventDto(
    val id: String?,
    val route: String,
    val ester: String,
    val timeH: Double,
    val doseMG: Double,
    val extras: Map<String, Double> = emptyMap()
)

sealed interface MahiroV1DecodeResult {
    data class Success(
        val document: MahiroV1DocumentDto,
        val diagnostics: List<MahiroV1EntryDiagnostic>
    ) : MahiroV1DecodeResult

    data class Failure(val error: MahiroV1DocumentError) : MahiroV1DecodeResult
}

data class MahiroV1EntryDiagnostic(
    val index: Int,
    val error: MahiroV1EntryError
)

sealed interface MahiroV1DocumentError {
    data class Syntax(val detail: String) : MahiroV1DocumentError
    data class InvalidRepresentation(val detail: String) : MahiroV1DocumentError
}

sealed interface MahiroV1EntryError {
    data class MissingField(val field: String) : MahiroV1EntryError
    data class InvalidFieldType(val field: String) : MahiroV1EntryError
    data object ExpectedObject : MahiroV1EntryError
}
