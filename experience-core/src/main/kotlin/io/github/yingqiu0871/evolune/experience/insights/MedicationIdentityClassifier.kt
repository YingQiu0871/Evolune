package io.github.yingqiu0871.evolune.experience.insights

import io.github.yingqiu0871.evolune.experience.MedicationMatchKey

/**
 * Pure classifier for the medication identity of an authoritative intake (v1.7-B-00 section 13.1).
 *
 * The classifier reads only the frozen match key of the **authoritative event** and never the
 * current schedule key: the projection already distinguishes them, and the actual medication
 * identity must come from the recorded fact.
 *
 * The anti-androgen route carries the ester slot as a placeholder, so its real drug is not
 * recoverable from the frozen projection; it is therefore reported as
 * [MedicationIdentityStatus.UNAVAILABLE] and never mapped to an ester or to a guessed drug name.
 */
object MedicationIdentityClassifier {

    /** Route key used by the production mapper for anti-androgen medications. */
    const val ANTI_ANDROGEN_ROUTE_KEY = "ANTIANDROGEN"

    private val knownKeys: Map<String, MedicationIdentityKey> =
        MedicationIdentityKey.entries.associateBy { it.name }

    fun classify(matchKey: MedicationMatchKey): MedicationIdentity {
        if (matchKey.routeKey == ANTI_ANDROGEN_ROUTE_KEY) {
            return MedicationIdentity(MedicationIdentityStatus.UNAVAILABLE)
        }
        val key = knownKeys[matchKey.medicationKey]
            ?: return MedicationIdentity(MedicationIdentityStatus.PARTIAL)
        return MedicationIdentity(MedicationIdentityStatus.KNOWN, key)
    }
}
