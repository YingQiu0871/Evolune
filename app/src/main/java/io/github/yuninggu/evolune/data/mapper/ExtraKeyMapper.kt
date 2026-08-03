package io.github.yuninggu.evolune.data.mapper

import io.github.yuninggu.evolune.core.model.ExtraKey as DomainExtraKey
import io.github.yuninggu.evolune.pk.DoseEvent.ExtraKey as PkExtraKey

fun extraKeyFromLegacyStorage(value: String): MappingResult<DomainExtraKey> = when (value) {
    "CONCENTRATION_MG_ML" -> MappingResult.Success(DomainExtraKey.CONCENTRATION_MG_ML)
    "AREA_CM2" -> MappingResult.Success(DomainExtraKey.AREA_CM2)
    "RELEASE_RATE_UG_PER_DAY" -> MappingResult.Success(DomainExtraKey.RELEASE_RATE_UG_PER_DAY)
    "SUBLINGUAL_THETA" -> MappingResult.Success(DomainExtraKey.SUBLINGUAL_THETA)
    "SUBLINGUAL_TIER" -> MappingResult.Success(DomainExtraKey.SUBLINGUAL_TIER)
    "ANTI_ANDROGEN_TYPE" -> MappingResult.Success(DomainExtraKey.ANTI_ANDROGEN_TYPE)
    else -> MappingResult.Failure(MappingError.InvalidExtraKey(value))
}

fun DomainExtraKey.toLegacyStorageKey(): String = when (this) {
    DomainExtraKey.CONCENTRATION_MG_ML -> "CONCENTRATION_MG_ML"
    DomainExtraKey.AREA_CM2 -> "AREA_CM2"
    DomainExtraKey.RELEASE_RATE_UG_PER_DAY -> "RELEASE_RATE_UG_PER_DAY"
    DomainExtraKey.SUBLINGUAL_THETA -> "SUBLINGUAL_THETA"
    DomainExtraKey.SUBLINGUAL_TIER -> "SUBLINGUAL_TIER"
    DomainExtraKey.ANTI_ANDROGEN_TYPE -> "ANTI_ANDROGEN_TYPE"
}

fun DomainExtraKey.toPkExtraKey(): PkExtraKey = when (this) {
    DomainExtraKey.CONCENTRATION_MG_ML -> PkExtraKey.CONCENTRATION_MG_ML
    DomainExtraKey.AREA_CM2 -> PkExtraKey.AREA_CM2
    DomainExtraKey.RELEASE_RATE_UG_PER_DAY -> PkExtraKey.RELEASE_RATE_UG_PER_DAY
    DomainExtraKey.SUBLINGUAL_THETA -> PkExtraKey.SUBLINGUAL_THETA
    DomainExtraKey.SUBLINGUAL_TIER -> PkExtraKey.SUBLINGUAL_TIER
    DomainExtraKey.ANTI_ANDROGEN_TYPE -> PkExtraKey.ANTI_ANDROGEN_TYPE
}

fun PkExtraKey.toDomainExtraKey(): DomainExtraKey = when (this) {
    PkExtraKey.CONCENTRATION_MG_ML -> DomainExtraKey.CONCENTRATION_MG_ML
    PkExtraKey.AREA_CM2 -> DomainExtraKey.AREA_CM2
    PkExtraKey.RELEASE_RATE_UG_PER_DAY -> DomainExtraKey.RELEASE_RATE_UG_PER_DAY
    PkExtraKey.SUBLINGUAL_THETA -> DomainExtraKey.SUBLINGUAL_THETA
    PkExtraKey.SUBLINGUAL_TIER -> DomainExtraKey.SUBLINGUAL_TIER
    PkExtraKey.ANTI_ANDROGEN_TYPE -> DomainExtraKey.ANTI_ANDROGEN_TYPE
}

internal fun Map<String, Double>.toDomainExtras(): MappingResult<Map<DomainExtraKey, Double>> {
    val mapped = linkedMapOf<DomainExtraKey, Double>()
    for ((key, value) in this) {
        when (val result = extraKeyFromLegacyStorage(key)) {
            is MappingResult.Success -> mapped[result.value] = value
            is MappingResult.Failure -> return result
        }
    }
    return MappingResult.Success(mapped)
}
