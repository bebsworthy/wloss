package app.wlo.core.documents

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shipped template library (F01 §3) is data, not code: the JSON resource
 * parses into [DietTemplate]s, and the deterministic applier turns a template
 * + quiz into a valid Targets draft.
 */
class OnboardingTemplatesTest {
    private val templates: List<DietTemplate> by lazy { OnboardingTemplates.load() }

    @Test
    fun libraryShipsTheF01Seed() {
        val ids = templates.map { it.id }
        assertTrue(ids.size >= 9, "v1 seed library: F01 §3 lists 9 templates")
        assertTrue(
            listOf(
                "balanced-deficit",
                "high-protein",
                "keto",
                "mediterranean",
                "low-carb",
                "if-16-8",
                "if-5-2",
                "if-6-1",
                "custom",
            ).all { it in ids },
            "missing: ${F01_IDS.filterNot { it in ids }}",
        )
        assertEquals(1, templates.count { it.isDefault }, "exactly one default (Balanced Deficit)")
        assertEquals("balanced-deficit", templates.first { it.isDefault }.id)
    }

    @Test
    fun ketoTemplateCarbsItsRing() {
        val keto = templates.first { it.id == "keto" }
        assertEquals(30.0, keto.carbCapG)
        assertTrue("carb-limit-ring" in keto.surfaces)
    }

    @Test
    fun ifTemplatesCarryTimingRules() {
        val if168 = templates.first { it.id == "if-16-8" }
        assertNotNull(if168.eatingWindow)
        assertEquals(12, if168.eatingWindow?.startHour)
        assertEquals(20, if168.eatingWindow?.endHour)
        val if52 = templates.first { it.id == "if-5-2" }
        assertEquals(Cadence.WEEKLY, if52.cadence)
    }

    @Test
    fun applierBuildsValidDocumentsForEveryTemplate() {
        templates.forEach { template ->
            val draft =
                DietTemplateApplier.toTargetsDocument(
                    template = template,
                    context =
                        DietTemplateApplier.Context(
                            sex = "female",
                            birthYear = 1994,
                            heightCm = 165.0,
                            currentWeightKg = 84.2,
                            goalWeightKg = 78.0,
                            pacePctPerWeek = 0.5,
                            formulaTdeeKcal = 2_406.0,
                        ),
                )
            assertEquals(
                emptyList(),
                TargetsInvariants.validate(draft),
                "template ${template.id} produced an invalid draft: " +
                    TargetsInvariants.validate(draft).joinToString { it.detail },
            )
            assertEquals(78.0, draft.goal.targetWeightKg)
        }
    }

    @Test
    fun applierWeeklyCadenceScheduleSumsExactly() {
        val draft =
            DietTemplateApplier.toTargetsDocument(
                template = templates.first { it.id == "if-5-2" },
                context =
                    DietTemplateApplier.Context(
                        goalWeightKg = 80.0,
                        formulaTdeeKcal = 2_000.0,
                        deficitFraction = 0.2,
                    ),
            )
        val energy = draft.energy
        assertEquals(Cadence.WEEKLY, energy.cadence)
        assertEquals(7, energy.schedule.size)
        assertEquals(energy.weeklyBudgetKcal, energy.schedule.sum())
    }

    @Test
    fun applierPicksFloorFromSex() {
        val draft =
            DietTemplateApplier.toTargetsDocument(
                template = templates.first(),
                context = DietTemplateApplier.Context(sex = "male", goalWeightKg = 80.0),
            )
        assertEquals(1_500.0, draft.energy.floorKcal)
    }

    @Test
    fun planDocumentWrapsTargetsAndQuiz() {
        val template = templates.first { it.id == "mediterranean" }
        val targets =
            DietTemplateApplier.toTargetsDocument(template, DietTemplateApplier.Context(goalWeightKg = 78.0))
        val record =
            DietTemplateApplier.toDietPlanDocument(
                template = template,
                context = DietTemplateApplier.Context(goalWeightKg = 78.0),
                preferences =
                    PreferenceProfile(
                        allergies = listOf("shellfish"),
                        dislikes = listOf("olives"),
                        householdSize = 4,
                    ),
                targets = targets,
                version = 1,
                createdAtEpochMs = 42L,
            )
        assertEquals(
            "shellfish",
            record.document.foodRules.allergies
                .single(),
        )
        assertEquals(
            "olives",
            record.document.foodRules.dislikes
                .single(),
        )
        assertEquals(4, record.document.household.size)
        assertTrue(
            "fish twice a week" in
                record.document.foodRules.hints[0]
                    .lowercase(),
        )
        // Round-trip through the diet-plan funnel.
        assertEquals(record, DietPlanDocumentIO.decode(DietPlanDocumentIO.encode(record)))
    }
}

private val F01_IDS =
    listOf(
        "balanced-deficit",
        "high-protein",
        "keto",
        "mediterranean",
        "low-carb",
        "if-16-8",
        "if-5-2",
        "if-6-1",
        "custom",
    )
