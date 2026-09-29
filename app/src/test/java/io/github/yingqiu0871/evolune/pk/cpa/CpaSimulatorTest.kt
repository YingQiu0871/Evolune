package io.github.yingqiu0871.evolune.pk.cpa

import io.github.yingqiu0871.evolune.core.model.DoseEvent as DomainDoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey as DomainExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan as DomainMedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.pk.DoseEvent
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.math.exp

class CpaSimulatorTest {

    @Test
    fun `single dose matches closed-form Bateman analytic solution`() {
        val doseMG = 50.0
        val bodyWeightKG = 70.0
        val event = cpaEvent(timeH = 0.0, doseMG = doseMG)

        val series = CpaSimulator.simulate(
            events = listOf(event),
            bodyWeightKG = bodyWeightKG,
            startTimeH = 0.0,
            endTimeH = 240.0,
            numberOfSteps = 2401
        )

        val ka = CpaPkParameters.KA_PER_H
        val ke = CpaPkParameters.KE_PER_H
        val f = CpaPkParameters.F
        val vdMl = CpaPkParameters.VD_L_PER_KG * bodyWeightKG * 1000.0

        listOf(1.0, 3.0, 10.0, 48.0, 120.0).forEach { t ->
            val amountMg = doseMG * f * ka / (ka - ke) * (exp(-ke * t) - exp(-ka * t))
            val expectedNgMl = amountMg * 1e6 / vdMl
            val actual = series.concentration(t)!!
            val relError = abs(actual - expectedNgMl) / expectedNgMl
            assertTrue("t=$t expected=$expectedNgMl actual=$actual", relError < 1e-9)
        }
    }

    @Test
    fun `terminal phase halves every t half`() {
        val event = cpaEvent(timeH = 0.0, doseMG = 50.0)
        val series = CpaSimulator.simulate(
            events = listOf(event),
            bodyWeightKG = 70.0,
            startTimeH = 0.0,
            endTimeH = 400.0,
            numberOfSteps = 4001
        )

        val tHalf = 43.9
        // Far enough past Tmax that the curve is in the pure terminal (log-linear) phase.
        val t = 100.0
        val c0 = series.concentration(t)!!
        val c1 = series.concentration(t + tHalf)!!
        val ratio = c1 / c0
        assertTrue("ratio=$ratio", abs(ratio - 0.5) < 0.01)
    }

    @Test
    fun `steady state accumulation matches analytic multi-dose formula within one percent`() {
        val doseMG = 12.5
        val tauH = 24.0
        val bodyWeightKG = 70.0
        val days = 20
        val events = (0 until days).map { day -> cpaEvent(timeH = day * tauH, doseMG = doseMG) }

        val evalTime = (days - 1) * tauH + 12.0
        val series = CpaSimulator.simulate(
            events = events,
            bodyWeightKG = bodyWeightKG,
            startTimeH = 0.0,
            endTimeH = evalTime + 1.0,
            numberOfSteps = ((evalTime + 1.0) * 12).toInt() + 1
        )

        val ka = CpaPkParameters.KA_PER_H
        val ke = CpaPkParameters.KE_PER_H
        val f = CpaPkParameters.F
        val vdL = CpaPkParameters.VD_L_PER_KG * bodyWeightKG

        // Infinite-superposition Bateman amount (mg), then mg -> ng/mL via *1000/Vd_L
        // (Vd in L, so mg/L -> ng/mL is *1000): F*D*ka/(ka-ke) *
        // (e^{-ke t}/(1-e^{-ke tau}) - e^{-ka t}/(1-e^{-ka tau})) * 1000 / Vd_L
        val tSinceLastDose = 12.0
        val amountMg = doseMG * f * ka / (ka - ke) * (
            exp(-ke * tSinceLastDose) / (1 - exp(-ke * tauH)) -
                exp(-ka * tSinceLastDose) / (1 - exp(-ka * tauH))
            )
        val analyticNgPerMl = amountMg * 1000.0 / vdL

        val actual = series.concentration(evalTime)!!
        val relError = abs(actual - analyticNgPerMl) / analyticNgPerMl
        assertTrue("expected=$analyticNgPerMl actual=$actual relError=$relError", relError < 0.01)
    }

    @Test
    fun `doubling the dose doubles the concentration`() {
        val bodyWeightKG = 70.0
        val base = CpaSimulator.simulate(
            events = listOf(cpaEvent(timeH = 0.0, doseMG = 25.0)),
            bodyWeightKG = bodyWeightKG,
            startTimeH = 0.0,
            endTimeH = 100.0,
            numberOfSteps = 1001
        )
        val doubled = CpaSimulator.simulate(
            events = listOf(cpaEvent(timeH = 0.0, doseMG = 50.0)),
            bodyWeightKG = bodyWeightKG,
            startTimeH = 0.0,
            endTimeH = 100.0,
            numberOfSteps = 1001
        )

        listOf(2.0, 10.0, 40.0).forEach { t ->
            val baseConc = base.concentration(t)!!
            val doubledConc = doubled.concentration(t)!!
            assertEquals(baseConc * 2.0, doubledConc, baseConc * 1e-9)
        }
    }

    @Test
    fun `concentration is zero before the first dose`() {
        val series = CpaSimulator.simulate(
            events = listOf(cpaEvent(timeH = 50.0, doseMG = 50.0)),
            bodyWeightKG = 70.0,
            startTimeH = 0.0,
            endTimeH = 100.0,
            numberOfSteps = 1001
        )
        assertEquals(0.0, series.concentration(10.0)!!, 1e-12)
        assertEquals(0.0, series.concentration(49.0)!!, 1e-12)
    }

    @Test
    fun `non-CPA anti-androgen and missing-type events are excluded from the simulation`() {
        val mpaEvent = DoseEvent(
            id = UUID.randomUUID(),
            route = Route.ANTIANDROGEN,
            timeH = 0.0,
            doseMG = 50.0,
            ester = Ester.E2,
            extras = mapOf(DoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE to 1.0) // MPA
        )
        val missingTypeEvent = DoseEvent(
            id = UUID.randomUUID(),
            route = Route.ANTIANDROGEN,
            timeH = 0.0,
            doseMG = 50.0,
            ester = Ester.E2,
            extras = emptyMap()
        )
        val nonAntiandrogenEvent = DoseEvent(
            id = UUID.randomUUID(),
            route = Route.ORAL,
            timeH = 0.0,
            doseMG = 50.0,
            ester = Ester.E2,
            extras = mapOf(DoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE to 0.0)
        )

        val series = CpaSimulator.simulate(
            events = listOf(mpaEvent, missingTypeEvent, nonAntiandrogenEvent),
            bodyWeightKG = 70.0,
            startTimeH = 0.0,
            endTimeH = 100.0,
            numberOfSteps = 1001
        )

        series.concNgMl.forEach { assertEquals(0.0, it, 1e-12) }
    }

    @Test
    fun `cancellation check is invoked once per outer step`() {
        var calls = 0
        CpaSimulator.simulate(
            events = listOf(cpaEvent(timeH = 0.0, doseMG = 50.0)),
            bodyWeightKG = 70.0,
            startTimeH = 0.0,
            endTimeH = 10.0,
            numberOfSteps = 11,
            cancellationCheck = { calls += 1 }
        )
        assertEquals(11, calls)
    }

    @Test
    fun `isCpaEvent selects recorded finite dose CPA anti-androgen events only`() {
        assertTrue(isCpaEvent(domainCpaEvent()))
        assertEquals(false, isCpaEvent(domainCpaEvent(route = Route.ORAL)))
        assertEquals(
            false,
            isCpaEvent(domainCpaEvent(extras = mapOf(DomainExtraKey.ANTI_ANDROGEN_TYPE to 1.0)))
        )
        assertEquals(false, isCpaEvent(domainCpaEvent(extras = emptyMap())))
        assertEquals(false, isCpaEvent(domainCpaEvent(doseMG = 0.0)))
        assertEquals(false, isCpaEvent(domainCpaEvent(doseMG = Double.NaN)))
    }

    @Test
    fun `isCpaPlan selects enabled CPA anti-androgen plans only`() {
        assertTrue(isCpaPlan(domainCpaPlan()))
        assertEquals(false, isCpaPlan(domainCpaPlan(isEnabled = false)))
        assertEquals(false, isCpaPlan(domainCpaPlan(route = Route.ORAL)))
        assertEquals(
            false,
            isCpaPlan(domainCpaPlan(extras = mapOf(DomainExtraKey.ANTI_ANDROGEN_TYPE to 2.0)))
        )
    }

    private fun cpaEvent(timeH: Double, doseMG: Double): DoseEvent = DoseEvent(
        id = UUID.randomUUID(),
        route = Route.ANTIANDROGEN,
        timeH = timeH,
        doseMG = doseMG,
        ester = Ester.E2,
        extras = mapOf(DoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE to 0.0)
    )

    private fun domainCpaEvent(
        status: DoseEventStatus = DoseEventStatus.RECORDED,
        route: Route = Route.ANTIANDROGEN,
        doseMG: Double = 50.0,
        extras: Map<DomainExtraKey, Double> = mapOf(DomainExtraKey.ANTI_ANDROGEN_TYPE to 0.0)
    ): DomainDoseEvent = DomainDoseEvent(
        id = UUID.randomUUID(),
        route = route,
        occurredAt = Instant.EPOCH,
        doseMG = doseMG,
        ester = Ester.E2,
        extras = extras,
        source = DoseEventSource.MANUAL,
        status = status
    )

    private fun domainCpaPlan(
        isEnabled: Boolean = true,
        route: Route = Route.ANTIANDROGEN,
        extras: Map<DomainExtraKey, Double> = mapOf(DomainExtraKey.ANTI_ANDROGEN_TYPE to 0.0)
    ): DomainMedicationPlan {
        val planId = UUID.randomUUID()
        return DomainMedicationPlan(
            id = planId,
            name = "CPA plan",
            route = route,
            ester = Ester.E2,
            doseMG = 50.0,
            scheduleType = ScheduleType.DAILY,
            slots = emptyList(),
            daysOfWeek = emptySet(),
            intervalDays = 1,
            isEnabled = isEnabled,
            extras = extras,
            createdAt = Instant.EPOCH
        )
    }
}
