package app.wlo.core.data

import app.wlo.core.common.AppError
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.database.DiaryEntryEntity
import app.wlo.core.database.DiaryEntryRevisionEntity
import app.wlo.core.database.ProvenanceEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.engines.FoodMath
import app.wlo.core.engines.InputsHash
import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.DiaryEntry
import app.wlo.core.model.DiaryRevision
import app.wlo.core.model.EntryVia
import app.wlo.core.model.MealSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * The food diary door (R-B1: F02 owns the diary; R-B8: event-level rows,
 * day-level rendering; F02 §3 correction audit). Editing appends the prior
 * state to the revision chain before the entry row moves — history is never
 * rewritten. "Delete" archives (R-B7 hide-not-delete); the day view and the
 * intake projection skip archived entries.
 */
public interface DiaryRepository {
    /** Logs one entry; numbers are computed here and written with provenance. */
    public suspend fun logEntry(
        entry: NewDiaryEntry,
        at: Instant,
    ): WloResult<DiaryEntry>

    /** Corrects an entry: prior state → revision row, then the entry updates. */
    public suspend fun editEntry(
        entryId: String,
        edit: EditDiaryEntry,
        at: Instant,
    ): WloResult<DiaryEntry>

    /** Entry "delete" — an archive, reversible, never a row deletion (R-B7). */
    public suspend fun archiveEntry(
        entryId: String,
        at: Instant,
    ): WloResult<Unit>

    /** The full audit chain of one entry, oldest first (the "ground-truth pair"). */
    public suspend fun revisionsOf(entryId: String): WloResult<List<DiaryRevision>>

    public suspend fun day(
        profileId: String,
        day: Long,
    ): WloResult<DayDiary>

    public fun observeDay(
        profileId: String,
        day: Long,
    ): Flow<WloResult<DayDiary>>
}

/** Log input. Portion math: `unit` g/ml → grams; `serving` × [NewDiaryEntry.servingGrams]. */
@Serializable
public data class NewDiaryEntry(
    public val profileId: String,
    public val dayEpochDay: Long,
    public val mealSlot: MealSlot,
    public val foodItemId: String? = null,
    public val textHint: String? = null,
    public val quantity: Double = 0.0,
    /** "g" | "ml" | "serving" (v1 portion vocabulary). */
    public val unit: String = "g",
    /** Grams per serving when [unit] == "serving" (the picked preset's size). */
    public val servingGrams: Double? = null,
    /** F02 §4 kcal-only quick-add path — bypasses portion math when set. */
    public val kcalOnly: Double? = null,
    public val enteredVia: EntryVia = EntryVia.MANUAL_SEARCH,
    /**
     * AI-estimate metadata for the provenance row (F02 §5: "AI-estimated
     * (on-device, v1.4)"): set ONLY by capture-flow saves. When present, the
     * entry's `provenance` row carries the model id as the formula version
     * and the model/confidence/consent state as inputs — the "how we got
     * here" sheet can name the model, the confidence and the consent state.
     * Additive field (M4): null for every pre-existing save path.
     */
    public val estimate: EstimateProvenance? = null,
    /**
     * Planned-meal replay ("log as planned", R-B1): the F03 slot's
     * denormalized per-serving nutrition, already scaled by the slot's
     * servings. A recipe has no catalog row, so the plan's own numbers ARE
     * the entry's numbers and the provenance row names the slot — the diary
     * keeps the single record (R-D11). Set ONLY by the planner's
     * one-tap replay; null for every pre-existing save path.
     */
    public val planNutrition: PlanNutrition? = null,
    public val operationId: String? = null,
    public val itemJson: String? = null,
)

/** Model + consent paperwork for one AI-assisted save (no schema change — rides the provenance row). */
@Serializable
public data class EstimateProvenance(
    /** Zoo model id, e.g. `food-classifier/1`. */
    public val modelId: String,
    /** Top-1 (or per-item) confidence at save time. */
    public val confidence: Double? = null,
    /** Consent state at save time — on-device inference needs none (false = none used). */
    public val consentGranted: Boolean = false,
    /** True when the analyzer held this estimate (low confidence); the UI says so. */
    public val held: Boolean = false,
)

/**
 * The planned-slot numbers one "log as planned" save replays verbatim
 * (R-B1): the F03 slot's denormalized per-serving macros × servings, named
 * back to the slot so the entry's provenance row can say "planned recipe,
 * slot <id>" instead of guessing a portion math path.
 */
@Serializable
public data class PlanNutrition(
    /** The [app.wlo.core.model.PlannedSlot.id] this entry replays. */
    public val slotId: String,
    public val kcal: Double?,
    public val proteinG: Double?,
    public val carbG: Double?,
    public val fatG: Double?,
    public val fiberG: Double?,
)

/** Edit input; every accepted edit bumps the entry's revision chain. */
@Serializable
public data class EditDiaryEntry(
    public val mealSlot: MealSlot,
    public val foodItemId: String? = null,
    public val textHint: String? = null,
    public val quantity: Double,
    public val unit: String = "g",
    public val servingGrams: Double? = null,
    public val kcalOnly: Double? = null,
    public val planNutrition: PlanNutrition? = null,
    public val itemJson: String? = null,
)

/** One day's diary, grouped by meal slot with the day's totals. */
public data class DayDiary(
    public val profileId: String,
    public val dayEpochDay: Long,
    /** Slot → entries, in [MealSlot] declaration order; empty slots are absent. */
    public val slots: Map<MealSlot, List<DiaryEntry>>,
    /** Every visible entry, creation order. */
    public val entries: List<DiaryEntry>,
    public val totals: DayDiaryTotals,
)

@Serializable
public data class DayDiaryTotals(
    public val kcal: Double?,
    public val proteinG: Double?,
    public val carbG: Double?,
    public val fatG: Double?,
    public val fiberG: Double?,
)

internal fun DiaryEntryEntity.toDomain(): DiaryEntry =
    DiaryEntry(
        id = id,
        profileId = profileId,
        dayEpochDay = dayEpochDay,
        mealSlot = MealSlot.fromWireName(mealSlot) ?: MealSlot.SNACK,
        foodItemId = foodItemId,
        textHint = textHint,
        quantity = quantity,
        unit = unit,
        kcal = computedKcal,
        itemJson = itemJson,
        proteinG = computedProteinG,
        carbG = computedCarbG,
        fatG = computedFatG,
        fiberG = computedFiberG,
        enteredVia = EntryVia.fromWireName(enteredVia) ?: EntryVia.MANUAL_SEARCH,
        provenanceScalar = provenanceScalar,
        revision = revision,
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
        editedAt = editedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
        archivedAt = archivedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
        hiddenAt = hiddenAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
        hiddenReason = hiddenReason,
    )

internal fun DiaryEntryRevisionEntity.toDomain(): DiaryRevision =
    DiaryRevision(
        id = id,
        entryId = entryId,
        revision = revision,
        mealSlot = MealSlot.fromWireName(mealSlot) ?: MealSlot.SNACK,
        foodItemId = foodItemId,
        textHint = textHint,
        quantity = quantity,
        unit = unit,
        kcal = computedKcal,
        itemJson = itemJson,
        proteinG = computedProteinG,
        carbG = computedCarbG,
        fatG = computedFatG,
        fiberG = computedFiberG,
        enteredVia = EntryVia.fromWireName(enteredVia) ?: EntryVia.MANUAL_SEARCH,
        editedAt = Instant.fromEpochMilliseconds(editedAtEpochMs),
    )

/** Computed numbers for one entry (portion math or quick-add), provenance-ready. */
internal data class ComputedEntry(
    val grams: Double,
    val kcal: Double?,
    val proteinG: Double?,
    val carbG: Double?,
    val fatG: Double?,
    val fiberG: Double?,
    val method: String,
    val formulaVersion: String,
    val inputs: List<String>,
)

public class RoomDiaryRepository public constructor(
    private val db: WloDatabase,
    private val projector: DayProjector,
    private val foodRepository: FoodRepository,
) : DiaryRepository {
    private val dao = db.diaryEntries()
    private val revisions = db.diaryEntryRevisions()

    override suspend fun logEntry(
        entry: NewDiaryEntry,
        at: Instant,
    ): WloResult<DiaryEntry> {
        val food = resolveFood(entry.foodItemId)
        val item = food.getOrNull()
        if (entry.foodItemId != null && item == null) {
            return WloResult.err(AppError.InvalidInput("no such food: ${entry.foodItemId}"))
        }
        val computed = compute(entry, item, entry.kcalOnly)

        return storageGuard("diary.logEntry") {
            val id = entry.operationId ?: Uuid.random().toString()
            dao.byId(id)?.let { existing ->
                require(existing.profileId == entry.profileId)
                return@storageGuard existing.toDomain()
            }
            val scalar = "diary/kcal/$id"
            val entity =
                DiaryEntryEntity(
                    id = id,
                    profileId = entry.profileId,
                    dayEpochDay = entry.dayEpochDay,
                    mealSlot = entry.mealSlot.wireName,
                    foodItemId = entry.foodItemId,
                    textHint = entry.textHint,
                    quantity = entry.quantity,
                    unit = entry.unit,
                    computedKcal = computed.kcal,
                    itemJson = entry.itemJson,
                    computedProteinG = computed.proteinG,
                    computedCarbG = computed.carbG,
                    computedFatG = computed.fatG,
                    computedFiberG = computed.fiberG,
                    enteredVia = entry.enteredVia.wireName,
                    provenanceScalar = scalar,
                    revision = 1,
                    createdAtEpochMs = at.toEpochMilliseconds(),
                )
            dao.insert(entity)
            writeProvenance(entry.profileId, entry.dayEpochDay, scalar, computed, at, entry.estimate)
            projector.refresh(entry.profileId, entry.dayEpochDay, entry.dayEpochDay)
            entity.toDomain()
        }
    }

    override suspend fun editEntry(
        entryId: String,
        edit: EditDiaryEntry,
        at: Instant,
    ): WloResult<DiaryEntry> {
        val load = storageGuard("diary.editEntry.load") { dao.byId(entryId) }
        val current = load.getOrNull() ?: return WloResult.err(notFoundError(load, entryId))
        if (current.archivedAtEpochMs != null) {
            return WloResult.err(AppError.InvalidInput("entry is archived: $entryId"))
        }
        val food = resolveFood(edit.foodItemId)
        val item = food.getOrNull()
        if (edit.foodItemId != null && item == null) {
            return WloResult.err(AppError.InvalidInput("no such food: ${edit.foodItemId}"))
        }
        val editInput =
            NewDiaryEntry(
                profileId = current.profileId,
                dayEpochDay = current.dayEpochDay,
                mealSlot = edit.mealSlot,
                foodItemId = edit.foodItemId,
                textHint = edit.textHint,
                quantity = edit.quantity,
                unit = edit.unit,
                servingGrams = edit.servingGrams,
                kcalOnly = edit.kcalOnly,
                itemJson = edit.itemJson,
                planNutrition = edit.planNutrition,
                enteredVia = current.toDomain().enteredVia,
            )
        val computed = compute(editInput, item, edit.kcalOnly)

        return storageGuard("diary.editEntry.write") {
            // 1. Append the prior state to the audit chain (R-B8: ground truth
            //    pair — the estimate and the correction both stay queryable).
            revisions.insert(
                DiaryEntryRevisionEntity(
                    id = Uuid.random().toString(),
                    entryId = current.id,
                    revision = current.revision,
                    mealSlot = current.mealSlot,
                    foodItemId = current.foodItemId,
                    textHint = current.textHint,
                    quantity = current.quantity,
                    unit = current.unit,
                    computedKcal = current.computedKcal,
                    itemJson = current.itemJson,
                    computedProteinG = current.computedProteinG,
                    computedCarbG = current.computedCarbG,
                    computedFatG = current.computedFatG,
                    computedFiberG = current.computedFiberG,
                    enteredVia = current.enteredVia,
                    editedAtEpochMs = at.toEpochMilliseconds(),
                ),
            )
            // 2. Move the entry pointer forward.
            val updated =
                current.copy(
                    mealSlot = edit.mealSlot.wireName,
                    foodItemId = edit.foodItemId,
                    textHint = edit.textHint,
                    quantity = edit.quantity,
                    unit = edit.unit,
                    computedKcal = computed.kcal,
                    itemJson = edit.itemJson,
                    computedProteinG = computed.proteinG,
                    computedCarbG = computed.carbG,
                    computedFatG = computed.fatG,
                    computedFiberG = computed.fiberG,
                    revision = current.revision + 1,
                    editedAtEpochMs = at.toEpochMilliseconds(),
                )
            dao.update(updated)
            writeProvenance(current.profileId, current.dayEpochDay, current.provenanceScalar, computed, at)
            projector.refresh(current.profileId, current.dayEpochDay, current.dayEpochDay)
            updated.toDomain()
        }
    }

    override suspend fun archiveEntry(
        entryId: String,
        at: Instant,
    ): WloResult<Unit> {
        val load = storageGuard("diary.archiveEntry.load") { dao.byId(entryId) }
        val current = load.getOrNull() ?: return WloResult.err(notFoundError(load, entryId))
        return storageGuard("diary.archiveEntry.write") {
            dao.archive(entryId, at.toEpochMilliseconds())
            projector.refresh(current.profileId, current.dayEpochDay, current.dayEpochDay)
        }
    }

    override suspend fun revisionsOf(entryId: String): WloResult<List<DiaryRevision>> =
        storageGuard("diary.revisionsOf") { revisions.forEntry(entryId).map { it.toDomain() } }

    override suspend fun day(
        profileId: String,
        day: Long,
    ): WloResult<DayDiary> = storageGuard("diary.day") { assemble(profileId, day, dao.day(profileId, day)) }

    override fun observeDay(
        profileId: String,
        day: Long,
    ): Flow<WloResult<DayDiary>> =
        dao
            .observeDay(profileId, day)
            .map { entries -> WloResult.ok(assemble(profileId, day, entries)) }
            .catch { emit(WloResult.err(AppError.Storage(cause = it, detail = "diary.observeDay"))) }

    // --- internals ---

    private fun assemble(
        profileId: String,
        day: Long,
        entries: List<DiaryEntryEntity>,
    ): DayDiary {
        val domain = entries.map { it.toDomain() }
        return DayDiary(
            profileId = profileId,
            dayEpochDay = day,
            slots =
                domain
                    .groupBy { it.mealSlot }
                    .toSortedMap(compareBy { slot: MealSlot -> MealSlot.entries.indexOf(slot) }),
            entries = domain,
            totals =
                DayDiaryTotals(
                    kcal = nullableSum(domain.map { it.kcal }),
                    proteinG = nullableSum(domain.map { it.proteinG }),
                    carbG = nullableSum(domain.map { it.carbG }),
                    fatG = nullableSum(domain.map { it.fatG }),
                    fiberG = nullableSum(domain.map { it.fiberG }),
                ),
        )
    }

    private suspend fun resolveFood(foodItemId: String?): WloResult<app.wlo.core.model.FoodItem?> =
        if (foodItemId == null) {
            WloResult.ok(null)
        } else {
            foodRepository.byId(foodItemId)
        }

    private fun compute(
        input: NewDiaryEntry,
        item: app.wlo.core.model.FoodItem?,
        kcalOnly: Double?,
    ): ComputedEntry =
        when {
            // R-B1 plan replay: the F03 slot's denormalized nutrition IS the
            // number (R-D11: the diary keeps the single record — the plan's
            // claim never gets re-derived here).
            input.planNutrition != null -> {
                val plan = input.planNutrition
                ComputedEntry(
                    grams = 0.0,
                    kcal = plan.kcal,
                    proteinG = plan.proteinG,
                    carbG = plan.carbG,
                    fatG = plan.fatG,
                    fiberG = plan.fiberG,
                    method = PROVENANCE_METHOD_PLAN,
                    formulaVersion = PLAN_REPLAY_FORMULA_VERSION,
                    inputs = listOf("via=plan", "slot=${plan.slotId}", "kcal=${plan.kcal}"),
                )
            }
            // F02 §4 kcal-only quick-add: the user's number IS the entry.
            kcalOnly != null ->
                ComputedEntry(
                    grams = 0.0,
                    kcal = kcalOnly,
                    proteinG = null,
                    carbG = null,
                    fatG = null,
                    fiberG = null,
                    method = PROVENANCE_METHOD_QUICK_ADD,
                    formulaVersion = FoodMath.PORTION_VERSION,
                    inputs = FoodMath.quickAddInputs(input.textHint, kcalOnly),
                )
            // Portion-scaled catalog item (F02 §3 "macros come from the DB").
            item != null -> {
                val grams =
                    when (input.unit) {
                        UNIT_SERVING ->
                            input.quantity *
                                (input.servingGrams ?: item.servingPresets.firstOrNull()?.grams ?: 0.0)
                        else -> input.quantity // "g" and "ml" share the per-100 scale
                    }
                ComputedEntry(
                    grams = grams,
                    kcal = FoodMath.scale(item.kcalPer100g, grams),
                    proteinG = FoodMath.scale(item.proteinGPer100g, grams),
                    carbG = FoodMath.scale(item.carbGPer100g, grams),
                    fatG = FoodMath.scale(item.fatGPer100g, grams),
                    fiberG = FoodMath.scale(item.fiberGPer100g, grams),
                    method = PROVENANCE_METHOD_FOOD_ITEM,
                    formulaVersion = FoodMath.PORTION_VERSION,
                    inputs = FoodMath.scaleInputs(item.id, grams, FoodMath.scale(item.kcalPer100g, grams) ?: 0.0),
                )
            }
            // Unknown food is incomplete, never a zero-calorie claim.
            else ->
                ComputedEntry(
                    grams = 0.0,
                    kcal = null,
                    proteinG = null,
                    carbG = null,
                    fatG = null,
                    fiberG = null,
                    method = PROVENANCE_METHOD_TEXT_HINT,
                    formulaVersion = FoodMath.PORTION_VERSION,
                    inputs = listOf("via=text-hint", "hint=${input.textHint ?: "none"}"),
                )
        }

    private suspend fun writeProvenance(
        profileId: String,
        day: Long,
        scalar: String,
        computed: ComputedEntry,
        at: Instant,
        estimate: EstimateProvenance? = null,
    ) {
        // AI-assisted saves override the method/version line so the provenance
        // row names the model + consent state (F02 §5's "AI-estimated
        // (on-device, <model>)"). The portion math itself is unchanged.
        val method =
            estimate
                ?.let { PROVENANCE_METHOD_AI_ESTIMATE }
                ?: computed.method
        val formulaVersion = estimate?.modelId ?: computed.formulaVersion
        val inputs =
            estimate
                ?.let {
                    computed.inputs +
                        listOf(
                            "model=${it.modelId}",
                            "confidence=${it.confidence ?: "none"}",
                            "consent=${it.consentGranted}",
                            "held=${it.held}",
                        )
                }
                ?: computed.inputs
        db.provenance().upsert(
            ProvenanceEntity(
                profileId = profileId,
                dayEpochDay = day,
                scalar = scalar,
                method = method,
                formulaVersion = formulaVersion,
                inputsHash = InputsHash.fnv1a64(inputs.joinToString(";")),
                computedAtEpochMs = at.toEpochMilliseconds(),
            ),
        )
    }

    /** D8 plumbing: pass a storage error through, or map a missing row to InvalidInput. */
    private fun notFoundError(
        load: WloResult<DiaryEntryEntity?>,
        entryId: String,
    ): AppError =
        when (load) {
            is WloResult.Err -> load.error
            is WloResult.Ok -> AppError.InvalidInput("no such entry: $entryId")
        }

    private fun nullableSum(values: List<Double?>): Double? = values.takeIf { it.isNotEmpty() && it.all { v -> v != null } }?.sumOf { it!! }

    public companion object {
        /** Provenance method labels (the "how we got here" sheet reads these). */
        public const val PROVENANCE_METHOD_FOOD_ITEM: String = "food-item-portion"
        public const val PROVENANCE_METHOD_AI_ESTIMATE: String = "ai-estimate"
        public const val PROVENANCE_METHOD_QUICK_ADD: String = "quick-add"
        public const val PROVENANCE_METHOD_TEXT_HINT: String = "text-hint"
        public const val PROVENANCE_METHOD_PLAN: String = "planned-recipe"

        public const val UNIT_SERVING: String = "serving"

        /** Formula version shared by every diary-derived number. */
        public const val PORTION_FORMULA_VERSION: String = ConstantsRegistry.DIARY_PORTION_FORMULA_VERSION

        /** R-B1 plan-replay formula version (stamped into the provenance row). */
        public const val PLAN_REPLAY_FORMULA_VERSION: String = "planner/slot-replay-v1"
    }
}
