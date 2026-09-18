package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.documents.Energy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IntakeTargetDraftTest {
    @Test fun modesPreserveExactIntakeAndAllowBothDirectionsAndMaintenance() {
        val start = IntakeTargetDraft(1885.0, 2385.0).switchMode(IntakeEntryMode.ADJUSTMENT)
        assertEquals(-500.0, start.displayedKcal)
        assertEquals(1885.0, start.switchMode(IntakeEntryMode.INTAKE).displayedKcal)
        assertEquals(2385.0, start.enter(0.0)?.intakeKcal)
        assertEquals(2685.0, start.enter(300.0)?.intakeKcal)
        assertEquals(500.0, start.enter(-1885.0)?.intakeKcal)
        assertEquals(1885.0, start.copy(maintenanceKcal = 2500.0).intakeKcal)
    }

    @Test fun sliderUsesSameAdjustmentInBothModesAndExactInputDoesNotSnap() {
        val draft = IntakeTargetDraft(1885.0, 2385.0)
        assertEquals(1910.0, draft.slide(-482.0)?.intakeKcal)
        assertEquals(
            draft.slide(301.0)?.intakeKcal,
            draft.switchMode(IntakeEntryMode.ADJUSTMENT).slide(301.0)?.intakeKcal,
        )
        assertEquals(1873.0, draft.enter(1873.0)?.intakeKcal)
        assertNull(draft.enter(Double.NaN))
        assertNull(draft.enter(Double.POSITIVE_INFINITY))
        assertNull(draft.enter(-1.0))
    }

    @Test fun missingMaintenanceDoesNotInventAnAdjustment() {
        val draft = IntakeTargetDraft(1800.0, null)
        assertNull(draft.adjustmentKcal)
        assertEquals(IntakeEntryMode.INTAKE, draft.switchMode(IntakeEntryMode.ADJUSTMENT).mode)
        assertEquals(900.0, draft.enter(900.0)?.intakeKcal)
    }

    @Test fun weekdayWeekendAllocationPreservesWeeklyTotalAndFloorMetadata() {
        val energy = IntakeTargetDraft(1800.0, 2300.0).weeklyEnergy(Energy(floorKcal = 1500.0), 200.0)!!
        assertEquals(12600.0, energy.schedule.sum(), 0.000001)
        assertEquals(1720.0, energy.schedule.first())
        assertEquals(2000.0, energy.schedule.last())
        assertEquals(1500.0, energy.floorKcal)
        assertNull(IntakeTargetDraft(100.0, null).weeklyEnergy(energy, 1000.0))
    }
}
