package io.github.yingqiu0871.evolune.pk.cpa

import io.github.yingqiu0871.evolune.core.model.DoseEvent as DomainDoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey as DomainExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan as DomainMedicationPlan
import io.github.yingqiu0871.evolune.pk.DoseEvent
import io.github.yingqiu0871.evolune.pk.Route
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Stable extra-key code for CPA (`AntiAndrogen.CPA.ordinal`, frozen per
 * `MedicationPlanEditor.toStableCode()` / the record sheet). Never renumbered — see
 * docs/evolune/v1.10/V110_CPA_CURVE_PLAN.md §1.1.
 */
private const val ANTI_ANDROGEN_TYPE_CPA_CODE = 0.0

/**
 * Frozen v1.10 one-compartment oral PK parameters for cyproterone acetate (CPA), derived in
 * docs/evolune/v1.10/V110_CPA_CURVE_PLAN.md §1.3 from the Androcur SmPC/PI (t½ = 43.9 h,
 * CL = 3.5 mL/min/kg, bioavailability ≈ 88 %) and cross-checked against Oyama's reference
 * one-compartment implementation (ka 1.0 h⁻¹, ke 0.017 h⁻¹, F 0.7, Vd 14 L/kg).
 *
 * Known limitation (must be disclosed in UI copy, S8): the real CPA disposition is biphasic.
 * This one-compartment terminal-phase model reproduces AUC, accumulation and steady-state
 * trough/average levels, but **underestimates single-dose peaks** (≈ 40-45 ng/mL predicted for
 * 50 mg at 70 kg vs ≈ 140 ng/mL reported for 50 mg). A two-compartment model is deferred
 * (out of scope, plan §4) until fitted parameters with a citable source exist.
 */
object CpaPkParameters {
    /** Absorption rate constant (h⁻¹). Reference Tmax is ~3 h; ka = 1.0 gives Tmax ≈ 4-5 h. */
    const val KA_PER_H: Double = 1.0

    /** Elimination rate constant (h⁻¹) = ln(2) / t½, t½ = 43.9 h (Androcur SmPC). */
    val KE_PER_H: Double = ln(2.0) / 43.9

    /** Absolute oral bioavailability. */
    const val F: Double = 0.88

    /** Apparent volume of distribution (L/kg). */
    const val VD_L_PER_KG: Double = 13.3
}

/**
 * CPA simulation result. Output unit is **ng/mL** — never summed with, or plotted on the same
 * axis as, the pg/mL E2 series (S5).
 *
 * @param timeH time points (hours)
 * @param concNgMl concentration series (ng/mL)
 */
data class CpaSeries(
    val timeH: List<Double>,
    val concNgMl: List<Double>
) {
    /**
     * Interpolates the concentration at [hour]. Same contract as
     * [io.github.yingqiu0871.evolune.pk.SimulationResult.concentration].
     */
    fun concentration(hour: Double): Double? {
        if (timeH.isEmpty() || timeH.size != concNgMl.size) return null
        if (hour <= timeH.first()) return concNgMl.first()
        if (hour >= timeH.last()) return concNgMl.last()

        var low = 0
        var high = timeH.size - 1

        while (high - low > 1) {
            val mid = (low + high) / 2
            when {
                timeH[mid] == hour -> return concNgMl[mid]
                timeH[mid] < hour -> low = mid
                else -> high = mid
            }
        }

        val t0 = timeH[low]
        val t1 = timeH[high]
        val c0 = concNgMl[low]
        val c1 = concNgMl[high]

        if (t1 <= t0) return c0

        val ratio = (hour - t0) / (t1 - t0)
        return c0 + (c1 - c0) * ratio
    }
}

/**
 * Returns true when [event] is a recorded CPA anti-androgen dose (S3): status RECORDED,
 * route ANTIANDROGEN, extras type code == CPA, and a finite positive dose. Missing or unknown
 * type codes are excluded — never guessed.
 */
fun isCpaEvent(event: DomainDoseEvent): Boolean =
    event.status == DoseEventStatus.RECORDED &&
        event.route == Route.ANTIANDROGEN &&
        event.extras[DomainExtraKey.ANTI_ANDROGEN_TYPE] == ANTI_ANDROGEN_TYPE_CPA_CODE &&
        event.doseMG > 0.0 &&
        event.doseMG.isFinite()

/**
 * Returns true when [plan] is an enabled CPA anti-androgen plan (S3): route ANTIANDROGEN and
 * extras type code == CPA. Used to select plans for [CpaSimulator] future-dose prediction via
 * the existing `MedicationPlanPredictor`.
 */
fun isCpaPlan(plan: DomainMedicationPlan): Boolean =
    plan.isEnabled &&
        plan.route == Route.ANTIANDROGEN &&
        plan.extras[DomainExtraKey.ANTI_ANDROGEN_TYPE] == ANTI_ANDROGEN_TYPE_CPA_CODE

/**
 * Simulates estimated plasma CPA concentration (ng/mL) from a list of already-adapted
 * [DoseEvent]s using a per-event oral Bateman one-compartment model with [CpaPkParameters],
 * superposed linearly (S4). Entirely separate from [io.github.yingqiu0871.evolune.pk.SimulationEngine]
 * and the frozen E2 pipeline (S2) — never invoked unless the CPA display setting is enabled (S6).
 */
object CpaSimulator {

    /**
     * @param events dose events to superpose; defensively re-filtered to CPA-typed
     *   anti-androgen events with a finite positive dose (non-CPA and missing-type events are
     *   ignored rather than trusted from the caller)
     * @param bodyWeightKG body weight used to scale the volume of distribution
     * @param startTimeH simulation window start (hours)
     * @param endTimeH simulation window end (hours)
     * @param numberOfSteps number of time points to compute
     * @param cancellationCheck invoked once per outer step; throws to cooperatively cancel
     */
    fun simulate(
        events: List<DoseEvent>,
        bodyWeightKG: Double,
        startTimeH: Double,
        endTimeH: Double,
        numberOfSteps: Int,
        cancellationCheck: () -> Unit = {}
    ): CpaSeries {
        if (startTimeH >= endTimeH || numberOfSteps <= 1 ||
            !bodyWeightKG.isFinite() || bodyWeightKG <= 0.0
        ) {
            return CpaSeries(emptyList(), emptyList())
        }

        val cpaEvents = events.filter { event ->
            event.route == Route.ANTIANDROGEN &&
                event.extras[DoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE] ==
                ANTI_ANDROGEN_TYPE_CPA_CODE &&
                event.doseMG > 0.0 &&
                event.doseMG.isFinite()
        }

        val ka = CpaPkParameters.KA_PER_H
        val ke = CpaPkParameters.KE_PER_H
        val f = CpaPkParameters.F
        val vdMl = CpaPkParameters.VD_L_PER_KG * bodyWeightKG * 1000.0
        if (vdMl <= 0.0) {
            return CpaSeries(emptyList(), emptyList())
        }

        val stepSize = (endTimeH - startTimeH) / (numberOfSteps - 1)
        val timeArr = ArrayList<Double>(numberOfSteps)
        val concArr = ArrayList<Double>(numberOfSteps)

        for (i in 0 until numberOfSteps) {
            cancellationCheck()
            val t = startTimeH + i * stepSize
            var totalAmountMg = 0.0
            for (event in cpaEvents) {
                val tau = t - event.timeH
                if (tau >= 0.0) {
                    totalAmountMg += batemanAmount(event.doseMG, f, ka, ke, tau)
                }
            }
            // mg -> ng (×1e6), volume already converted to mL above.
            val conc = totalAmountMg * 1e6 / vdMl

            timeArr.add(t)
            concArr.add(conc)
        }

        return CpaSeries(timeArr, concArr)
    }

    private fun batemanAmount(doseMG: Double, f: Double, ka: Double, ke: Double, t: Double): Double {
        if (doseMG <= 0.0 || ka <= 0.0) return 0.0

        if (abs(ka - ke) < 1e-9) {
            return doseMG * f * ka * t * exp(-ke * t)
        }

        return doseMG * f * ka / (ka - ke) * (exp(-ke * t) - exp(-ka * t))
    }
}
