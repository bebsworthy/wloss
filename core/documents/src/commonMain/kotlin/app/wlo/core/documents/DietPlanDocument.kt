package app.wlo.core.documents

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/**
 * F01's Diet Plan — the user-authored artifact wrapping the spine's Targets
 * document (Appendix A) with F01/F03/F04-local rules. Reconciliation note:
 * F01 §3's pre-ratification flat shape (`{targets, cadence, schedule, …}`)
 * folded into Appendix A — cadence/schedule/macroSplit/surfaces live INSIDE
 * [TargetsDocument]; only F03/F04-consumed rules sit beside it here.
 * Every edit creates `vN+1`; any version can be reverted (F01 §1).
 */
@Serializable
public data class DietPlanRecord(
    public val version: Int,
    public val parentVersion: Int? = null,
    public val createdAtEpochMs: Long,
    public val document: DietPlanDocument,
)

@Serializable
public data class DietPlanDocument(
    public val name: String = "My plan",
    public val targets: TargetsDocument,
    public val foodRules: FoodRules = FoodRules(),
    public val timingRules: TimingRules = TimingRules(),
    public val household: Household = Household(),
)

/** F01 §3 foodRules: allergies, exclusions, dislikes (open, extensible sets). */
@Serializable
public data class FoodRules(
    /** True allergies — hard filters in F03/F04 generation. */
    public val allergies: List<String> = emptyList(),
    /** Exclusions (dietary, ethical) — hard filters. */
    public val exclusions: List<String> = emptyList(),
    /** Dislikes — soft filters, never suggested but never forbidden. */
    public val dislikes: List<String> = emptyList(),
    /** Soft authored hints rendered by F03 (e.g. "fish twice a week"). */
    public val hints: List<String> = emptyList(),
)

/** F01 §3 timingRules: IF windows + meal slots (F10 renders timers). */
@Serializable
public data class TimingRules(
    public val eatingWindows: List<EatingWindow> = emptyList(),
    public val mealSlots: List<String> = emptyList(),
)

/** F01 §3 household: sizes → F04 default servings. */
@Serializable
public data class Household(
    public val size: Int = 1,
    public val defaultServings: Int = 1,
)

/** Storage funnel for Diet Plan versions (same house rules as Targets). */
public object DietPlanDocumentIO {
    public const val SCHEMA_VERSION: Int = 1

    public fun encode(record: DietPlanRecord): String =
        DocumentCodec.json.encodeToString(
            serializer = serializer<DocumentEnvelope<DietPlanRecord>>(),
            value = DocumentEnvelope(SCHEMA_VERSION, record),
        )

    public fun decode(text: String): DietPlanRecord {
        val envelope =
            DocumentCodec.json.decodeFromString(
                deserializer = serializer<DocumentEnvelope<DietPlanRecord>>(),
                string = text,
            )
        when (envelope.schemaVersion) {
            SCHEMA_VERSION -> Unit
            else -> error("unknown diet-plan schemaVersion: ${envelope.schemaVersion}")
        }
        return envelope.payload
    }
}

/**
 * The F01 preference quiz result (F01 §3; R-S6 8-card core in v1). Deterministic
 * input to the template applier — writes FoodRules + household defaults.
 */
@Serializable
public data class PreferenceProfile(
    public val allergies: List<String> = emptyList(),
    public val exclusions: List<String> = emptyList(),
    public val dislikes: List<String> = emptyList(),
    /** 1–6+. */
    public val householdSize: Int = 1,
    /** never | few-week | most-days | daily. */
    public val cookingFrequency: String = "most-days",
    /** beginner | home-cook | confident | advanced. */
    public val cookingSkill: String = "home-cook",
    /** tight | moderate | relaxed. */
    public val budgetBand: String = "moderate",
)
