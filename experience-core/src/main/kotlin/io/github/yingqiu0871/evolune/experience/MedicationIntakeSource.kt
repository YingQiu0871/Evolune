package io.github.yingqiu0871.evolune.experience

/**
 * Origin of a recorded intake as far as derived historical surfaces need it.
 *
 * Mirrors the authoritative Phone `DoseEventSource` values without depending on
 * the Android module. The historical projection must never guess this value and
 * must never promote a non-manual origin to `MANUAL`: only `MANUAL` events may be
 * presented as a manual intake.
 */
enum class MedicationIntakeSource {
    LEGACY,
    MANUAL,
    JSON_V1,
    REMINDER,
    WIDGET,
    WEAR
}
