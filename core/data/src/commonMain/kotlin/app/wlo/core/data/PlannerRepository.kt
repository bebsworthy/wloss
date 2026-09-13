package app.wlo.core.data

import androidx.room3.withWriteTransaction
import app.wlo.core.common.AppError
import app.wlo.core.common.ClockPort
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.database.PlanSlotEntity
import app.wlo.core.database.PlanVersionEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.engines.AdherenceMetrics
import app.wlo.core.engines.PlannerEngine
import app.wlo.core.model.PlannedSlot
import app.wlo.core.model.PlannedSlotState
import app.wlo.core.model.Recipe
import app.wlo.core.model.RecipeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * The planner door (F03; R-B1 state machine owner). Generation is the
 * deterministic engine's output persisted as an immutable plan version +
 * append-only slot records; swaps retire + insert (never rewrite), and every
 * mutation refreshes the touched days' projections so the day record's
 * planned-vs-logged distinction stays true.
 */
public interface PlannerRepository {
    /** The active (non-superseded) plan with its slots and "why this plan" report. */
    public suspend fun currentPlan(profileId: String): WloResult<PlanView?>

    public fun observeCurrentPlan(profileId: String): Flow<WloResult<PlanView?>>

    /** Slots in a day range regardless of which plan version dealt them. */
    public suspend fun slots(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<PlannedSlot>>

    public fun observeSlots(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<WloResult<List<PlannedSlot>>>

    /**
     * Generates (or re-deals) the plan: supersedes any active plan and
     * persists the fresh deal against the CURRENT targets (F03 §7 — today's
     * budget, never onboarding's).
     */
    public suspend fun generateWeek(request: GenerateWeekPlan): WloResult<PlanView>

    /** planned → confirmed: adopts the recipe nutrition (one tap, F03 §1). */
    public suspend fun confirmSlot(
        slotId: String,
        at: Instant,
    ): WloResult<PlannedSlot>

    /** planned → skipped: the neutral, honest miss (F03 §6). */
    public suspend fun skipSlot(
        slotId: String,
        at: Instant,
    ): WloResult<PlannedSlot>

    /** planned → replaced: links the F02 entry, which owns the nutrition (R-B1). */
    public suspend fun replaceSlot(
        slotId: String,
        diaryEntryId: String,
        at: Instant,
    ): WloResult<PlannedSlot>

    /**
     * Swap with rebalancing: retires the planned slot (→ swapped) and deals a
     * fresh planned successor with the new recipe (F03 §3). History stays
     * append-only; the day projection re-folds instantly.
     */
    public suspend fun swapSlot(
        slotId: String,
        newRecipeId: RecipeId,
        at: Instant,
    ): WloResult<PlannedSlot>

    /** Top-3 swaps that best restore the day's fit (F03 §3; allergens hard-blocked). */
    public suspend fun swapSuggestions(
        slotId: String,
        limit: Int = 3,
    ): WloResult<List<Recipe>>

    /** R-B4 adherence metrics (F03 computes; F11 consumes read-only). */
    public suspend fun adherence(
        profileId: String,
        asOfDayEpochDay: Long,
    ): WloResult<AdherenceMetrics.AdherenceReport>
}

public class RoomPlannerRepository public constructor(
    private val db: WloDatabase,
    private val targets: TargetsRepository,
    private val recipes: RecipeRepository,
    private val projector: DayProjector,
    private val clock: ClockPort,
) : PlannerRepository {
    private val plans = db.planVersions()
    private val slots = db.planSlots()

    override suspend fun currentPlan(profileId: String): WloResult<PlanView?> =
        storageGuard("planner.currentPlan") { assemble(plan = plans.current(profileId)) }

    override fun observeCurrentPlan(profileId: String): Flow<WloResult<PlanView?>> =
        plans
            .observeCurrent(profileId)
            .map { entity -> WloResult.ok(assemble(plan = entity)) }
            .catch { emit(WloResult.err(AppError.Storage(cause = it, detail = "planner.observeCurrentPlan"))) }

    override suspend fun slots(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): WloResult<List<PlannedSlot>> = storageGuard("planner.slots") { slots.range(profileId, fromDay, toDay).map { it.toDomain() } }

    override fun observeSlots(
        profileId: String,
        fromDay: Long,
        toDay: Long,
    ): Flow<WloResult<List<PlannedSlot>>> =
        slots
            .observeRange(profileId, fromDay, toDay)
            .map { rows -> WloResult.ok(rows.map { it.toDomain() }) }
            .catch { emit(WloResult.err(AppError.Storage(cause = it, detail = "planner.observeSlots"))) }

    override suspend fun generateWeek(request: GenerateWeekPlan): WloResult<PlanView> {
        // 1. Today's targets per day (A.3 discipline: nobody parses the schedule).
        val current =
            targets.current(request.profileId).getOrNull()
                ?: return WloResult.err(AppError.InvalidInput("no active targets for ${request.profileId} — create the diet plan first"))
        val document = current.document
        val dayTargets =
            (0 until request.days).mapNotNull { offset ->
                val day = request.startDayEpochDay + offset
                projectionFor(document, day)?.let { projection ->
                    PlannerEngine.DayTargets(
                        dayEpochDay = day,
                        kcal = projection.kcal,
                        proteinG = projection.proteinG,
                        carbG = projection.carbG,
                        fatG = projection.fatG,
                        fiberG = projection.fiberG,
                    )
                }
            }
        if (dayTargets.isEmpty()) {
            return WloResult.err(AppError.InvalidInput("targets carry no budget for the requested range"))
        }

        // 2. The library (seeded on first generation — the F01 CTA contract).
        recipes.ensureSeeded(request.profileId, clock.now()).getOrNull()
        val library = recipes.library(request.profileId).getOrNull().orEmpty()

        // 3. Deal.
        val dealt =
            PlannerEngine.generate(
                library,
                PlannerEngine.PlanRequest(
                    startDayEpochDay = request.startDayEpochDay,
                    days = request.days,
                    slots = request.mealSlots,
                    targets = dayTargets,
                    settings = request.settings,
                    seed = request.seed,
                ),
            )

        // 4. Persist: supersede the active plan, insert version + slot records.
        val now = clock.now()
        val nowMs = now.toEpochMilliseconds()
        val recipeById = library.associateBy { it.id }
        val planId = UuidStrings.newId()
        return storageGuard("planner.generateWeek") {
            db.withWriteTransaction {
                plans.supersedeActive(request.profileId, nowMs)
                val version = (plans.maxVersion(request.profileId) ?: 0) + 1
                plans.insert(
                    PlanVersionEntity(
                        id = planId,
                        profileId = request.profileId,
                        version = version,
                        startDayEpochDay = request.startDayEpochDay,
                        endDayEpochDay = request.startDayEpochDay + request.days - 1,
                        seed = request.seed,
                        settingsJson = encodeSettings(request.settings),
                        reportJson = encodeReport(dealt.report),
                        createdAtEpochMs = nowMs,
                    ),
                )
                val entities =
                    dealt.slots.map { draft ->
                        val recipe = draft.recipeId?.let { recipeById[it] }
                        slotEntity(planId, request.profileId, draft, recipe, nowMs)
                    }
                // Link leftover children to the cook slot of their parent day
                // (first cook event of that day wins when there are several).
                val cookSlotIdByDay =
                    entities
                        .filter { it.isCookEvent }
                        .associateBy({ it.dayEpochDay }, { it.id })
                val linked =
                    entities.mapIndexed { index, entity ->
                        val parentDay = dealt.slots[index].parentCookDay
                        if (parentDay != null) {
                            entity.copy(parentSlotId = cookSlotIdByDay[parentDay])
                        } else {
                            entity
                        }
                    }
                slots.insertAll(linked)
            }
            // 5. Fold the planned side of the touched days into the day record.
            projector.refresh(
                request.profileId,
                request.startDayEpochDay,
                request.startDayEpochDay + request.days - 1,
            )
            assemble(plans.byId(planId)) ?: throw IllegalStateException("plan $planId vanished mid-write")
        }
    }

    override suspend fun confirmSlot(
        slotId: String,
        at: Instant,
    ): WloResult<PlannedSlot> = transitionSlot(slotId, PlannedSlotState.CONFIRMED, null, at)

    override suspend fun skipSlot(
        slotId: String,
        at: Instant,
    ): WloResult<PlannedSlot> = transitionSlot(slotId, PlannedSlotState.SKIPPED, null, at)

    override suspend fun replaceSlot(
        slotId: String,
        diaryEntryId: String,
        at: Instant,
    ): WloResult<PlannedSlot> = transitionSlot(slotId, PlannedSlotState.REPLACED, diaryEntryId, at)

    override suspend fun swapSlot(
        slotId: String,
        newRecipeId: RecipeId,
        at: Instant,
    ): WloResult<PlannedSlot> {
        val load = storageGuard("planner.swap.load") { slots.byId(slotId) }
        val current = load.getOrNull() ?: return notFoundSlotOr(load, slotId)
        if (!PlannerEngine.canTransition(current.state.toState(), PlannedSlotState.SWAPPED)) {
            return WloResult.err(AppError.InvalidInput("slot $slotId is ${current.state}; only planned slots can swap"))
        }
        val recipeResult = recipes.byId(newRecipeId)
        val recipe = recipeResult.getOrNull() ?: return notFoundRecipeOr(recipeResult, newRecipeId)
        if (recipe.slots.isNotEmpty() && !recipe.slots.contains(current.mealSlot)) {
            return WloResult.err(AppError.InvalidInput("recipe ${recipe.id} does not suit the ${current.mealSlot} slot"))
        }
        return storageGuard("planner.swap.write") {
            val nowMs = at.toEpochMilliseconds()
            val successorId = UuidStrings.newId()
            val successor =
                PlanSlotEntity(
                    id = successorId,
                    planId = current.planId,
                    profileId = current.profileId,
                    dayEpochDay = current.dayEpochDay,
                    mealSlot = current.mealSlot,
                    recipeId = recipe.id,
                    recipeVersion = recipe.version,
                    recipeName = recipe.name,
                    servings = current.servings,
                    state = PlannedSlotState.PLANNED.wireName,
                    replacesSlotId = current.id,
                    isCookEvent = false,
                    kcalPerServing = recipe.nutrition.kcal,
                    proteinGPerServing = recipe.nutrition.proteinG,
                    carbGPerServing = recipe.nutrition.carbG,
                    fatGPerServing = recipe.nutrition.fatG,
                    fiberGPerServing = recipe.nutrition.fiberG,
                    createdAtEpochMs = nowMs,
                )
            db.withWriteTransaction {
                slots.setState(current.id, PlannedSlotState.SWAPPED.wireName, nowMs)
                slots.setSuccessor(current.id, successorId, nowMs)
                slots.insert(successor)
            }
            projector.refresh(current.profileId, current.dayEpochDay, current.dayEpochDay)
            successor.toDomain()
        }
    }

    override suspend fun swapSuggestions(
        slotId: String,
        limit: Int,
    ): WloResult<List<Recipe>> {
        val load = storageGuard("planner.suggestions.load") { slots.byId(slotId) }
        val current = load.getOrNull() ?: return notFoundSlotOr(load, slotId)
        val library = recipes.library(current.profileId).getOrNull().orEmpty()
        val targets =
            targetsFor(current.profileId, current.dayEpochDay)
                ?: return WloResult.err(AppError.InvalidInput("no targets for day ${current.dayEpochDay}"))
        val constraints =
            PlannerEngine.Constraints(
                // v1: rule strictness comes from the profile's diet rules via
                // the caller-side settings; suggestions filter allergens only.
            )
        val slotsOfDay =
            current
                .planId
                .let { plans.byId(it) }
                ?.let { plan -> slots.forPlan(plan.id).filter { it.dayEpochDay == current.dayEpochDay }.map { it.mealSlot } }
                .orEmpty()
                .ifEmpty { listOf(current.mealSlot) }
        return WloResult.ok(
            PlannerEngine.swapSuggestions(
                recipes = library,
                slot =
                    PlannerEngine.SlotDraft(
                        dayEpochDay = current.dayEpochDay,
                        mealSlot = current.mealSlot,
                        recipeId = current.recipeId,
                        servings = current.servings,
                    ),
                dayTargets = targets,
                slotsOfDay = slotsOfDay,
                constraints = constraints,
                limit = limit,
            ),
        )
    }

    override suspend fun adherence(
        profileId: String,
        asOfDayEpochDay: Long,
    ): WloResult<AdherenceMetrics.AdherenceReport> =
        storageGuard("planner.adherence") {
            val allSlots =
                plans
                    .current(profileId)
                    ?.let { plan -> slots.forPlan(plan.id) }
                    .orEmpty()
                    .map { it.toDomain() }
            val firstDay = allSlots.minOfOrNull { it.dayEpochDay } ?: asOfDayEpochDay
            val actuals =
                db
                    .diaryEntries()
                    .range(profileId, firstDay, asOfDayEpochDay)
                    .groupBy({ it.dayEpochDay }, { it.computedKcal })
                    .mapValues { (_, values) -> values.sum() }
            AdherenceMetrics.compute(
                AdherenceMetrics.Input(
                    slots = allSlots,
                    actualKcalByDay = actuals,
                    asOfDayEpochDay = asOfDayEpochDay,
                ),
            )
        }

    // --- internals ---------------------------------------------------------------

    private suspend fun assemble(plan: PlanVersionEntity?): PlanView? {
        plan ?: return null
        val rows = slots.forPlan(plan.id)
        return PlanView(
            planId = plan.id,
            profileId = plan.profileId,
            version = plan.version,
            startDayEpochDay = plan.startDayEpochDay,
            endDayEpochDay = plan.endDayEpochDay,
            seed = plan.seed,
            settings = decodeSettings(plan.settingsJson),
            report = decodeReport(plan.reportJson),
            createdAt = Instant.fromEpochMilliseconds(plan.createdAtEpochMs),
            slots = rows.map { it.toDomain() },
        )
    }

    private suspend fun transitionSlot(
        slotId: String,
        to: PlannedSlotState,
        diaryEntryId: String?,
        at: Instant,
    ): WloResult<PlannedSlot> {
        val load = storageGuard("planner.transition.load") { slots.byId(slotId) }
        val current = load.getOrNull() ?: return notFoundSlotOr(load, slotId)
        if (!PlannerEngine.canTransition(current.state.toState(), to)) {
            return WloResult.err(
                AppError.InvalidInput("illegal transition ${current.state} → ${to.wireName} (R-B1 state machine)"),
            )
        }
        return storageGuard("planner.transition.write") {
            if (to == PlannedSlotState.REPLACED) {
                slots.setReplaced(slotId, to.wireName, diaryEntryId ?: "", at.toEpochMilliseconds())
            } else {
                slots.setState(slotId, to.wireName, at.toEpochMilliseconds())
            }
            projector.refresh(current.profileId, current.dayEpochDay, current.dayEpochDay)
            slots.byId(slotId)!!.toDomain()
        }
    }

    private suspend fun targetsFor(
        profileId: String,
        day: Long,
    ): PlannerEngine.DayTargets? {
        val current = targets.current(profileId).getOrNull() ?: return null
        val projection = projectionFor(current.document, day) ?: return null
        return PlannerEngine.DayTargets(
            dayEpochDay = day,
            kcal = projection.kcal,
            proteinG = projection.proteinG,
            carbG = projection.carbG,
            fatG = projection.fatG,
            fiberG = projection.fiberG,
        )
    }

    private fun projectionFor(
        document: TargetsDocument,
        day: Long,
    ): Projection? {
        val isoDay = LocalDate.fromEpochDays(day.toInt()).dayOfWeek.isoDayNumber
        val budget = document.budgetForDay(isoDay) ?: return null
        val custom = document.macros.split as? app.wlo.core.documents.MacroSplit.Custom
        return Projection(
            kcal = budget,
            proteinG = custom?.proteinPct?.let { it / 100.0 * budget / app.wlo.core.model.ConstantsRegistry.KCAL_PER_G_PROTEIN },
            carbG = custom?.carbPct?.let { it / 100.0 * budget / app.wlo.core.model.ConstantsRegistry.KCAL_PER_G_PROTEIN },
            fatG = custom?.fatPct?.let { it / 100.0 * budget / app.wlo.core.model.ConstantsRegistry.KCAL_PER_G_FAT },
            fiberG = document.fiber.targetG,
        )
    }

    private data class Projection(
        val kcal: Double,
        val proteinG: Double?,
        val carbG: Double?,
        val fatG: Double?,
        val fiberG: Double?,
    )

    private fun slotEntity(
        planId: String,
        profileId: String,
        draft: PlannerEngine.SlotDraft,
        recipe: Recipe?,
        nowMs: Long,
    ): PlanSlotEntity =
        PlanSlotEntity(
            id = UuidStrings.newId(),
            planId = planId,
            profileId = profileId,
            dayEpochDay = draft.dayEpochDay,
            mealSlot = draft.mealSlot,
            recipeId = draft.recipeId,
            recipeVersion = draft.recipeVersion,
            recipeName = recipe?.name,
            servings = draft.servings,
            state = PlannedSlotState.PLANNED.wireName,
            isCookEvent = draft.isCookEvent,
            batchServings = draft.batchServings,
            kcalPerServing = recipe?.nutrition?.kcal,
            proteinGPerServing = recipe?.nutrition?.proteinG,
            carbGPerServing = recipe?.nutrition?.carbG,
            fatGPerServing = recipe?.nutrition?.fatG,
            fiberGPerServing = recipe?.nutrition?.fiberG,
            createdAtEpochMs = nowMs,
        )

    private fun String.toState(): PlannedSlotState = PlannedSlotState.fromWireName(this) ?: PlannedSlotState.PLANNED

    private fun encodeSettings(settings: PlannerEngine.Settings): String =
        PlanningJson.encodeToString(PlannerEngine.Settings.serializer(), settings)

    private fun decodeSettings(json: String): PlannerEngine.Settings? =
        runCatching { PlanningJson.decodeFromString(PlannerEngine.Settings.serializer(), json) }.getOrNull()

    private fun encodeReport(report: PlannerEngine.Report): String = PlanningJson.encodeToString(PlannerEngine.Report.serializer(), report)

    private fun decodeReport(json: String): PlannerEngine.Report? =
        runCatching { PlanningJson.decodeFromString(PlannerEngine.Report.serializer(), json) }.getOrNull()

    private fun notFoundSlotOr(
        load: WloResult<PlanSlotEntity?>,
        slotId: String,
    ): WloResult<Nothing> =
        when (load) {
            is WloResult.Err -> WloResult.err(load.error)
            is WloResult.Ok -> WloResult.err(AppError.InvalidInput("no such slot: $slotId"))
        }

    private fun notFoundRecipeOr(
        load: WloResult<Recipe?>,
        recipeId: RecipeId,
    ): WloResult<Nothing> =
        when (load) {
            is WloResult.Err -> WloResult.err(load.error)
            is WloResult.Ok -> WloResult.err(AppError.InvalidInput("no such recipe: $recipeId"))
        }
}
