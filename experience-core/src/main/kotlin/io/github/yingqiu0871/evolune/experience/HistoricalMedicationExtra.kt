package io.github.yingqiu0871.evolune.experience

/**
 * Derived-layer propagation keys for authoritative dose-event extras
 * (V17-C-00 §4, approved minimal extension).
 *
 * Exactly six keys; do not add, remove or rename.
 */
enum class HistoricalMedicationExtraKey {
    CONCENTRATION_MG_ML,
    AREA_CM2,
    RELEASE_RATE_UG_PER_DAY,
    SUBLINGUAL_THETA,
    SUBLINGUAL_TIER,
    ANTI_ANDROGEN_TYPE
}
