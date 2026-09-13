package app.wlo.core.engines

import app.wlo.core.model.ConstantsRegistry
import app.wlo.core.model.PlannedSlot
import app.wlo.core.model.PlannedSlotState
import app.wlo.core.model.RecipeId
import kotlinx.serialization.Serializable

/**
 * Planned-vs-actual adherence metrics (F03 §5; R-B4: F03 computes, F11
 * consumes read-only — one definition per number). PURE functions over the
 * persisted slots + the diary's actual day totals (D7: values in, values
 * out); no clock — the caller owns "today".
 *
 * Definitions (F03 §5, frozen here):
 *  - **Plan coverage** — among slots whose date is strictly before the
 *    as-of day, the share that reached a terminal state (confirmed /
 *    replaced / skipped). A slot still `planned` after its date is simply
 *    open, never penalized — it lowers coverage only by not being terminal
 *    yet, it is never a failure mark.
 *  - **Energy fidelity** — median |actual − planned| kcal across days that
 *    have both a planned claim and a logged actual; shown as a distribution,
 *    not a grade.
 *  - **Swap gravity** — swap counts per recipe, most-swapped first; library
 *    gaps worth filling.
 *  - **Cook realism** — confirmed leftovers ÷ planned leftovers (0 when none
 *    planned; null when no cook events exist).
 *
 * Data gate (F03 §4): every metric is refused — [AdherenceReport.meaningful]
 * = false, reason "not yet meaningful" — until ≥
 * [ConstantsRegistry.ADHERENCE_MIN_LOGGED_DAYS] logged days exist in the
 * window. Refusal is a value, never a guessed number.
 */
public object AdherenceMetrics {
    @Serializable
    public data class Input(
        /** The plan's slots (any states; SWAPPED rows are ignored as retired). */
        public val slots: List<PlannedSlot>,
        /** Logged day totals from the diary (kcal), keyed by epoch day. */
        public val actualKcalByDay: Map<Long, Double>,
        /** The as-of epoch day (inclusive); slots before it count for coverage. */
        public val asOfDayEpochDay: Long,
    )

    @Serializable
    public data class SwapGravity(
        public val recipeId: RecipeId,
        public val swaps: Int,
    )

    @Serializable
    public data class AdherenceReport(
        public val meaningful: Boolean,
        /** Null when refused (the UI renders "not yet meaningful", never a guess). */
        public val planCoveragePct: Double? = null,
        public val energyFidelityKcal: Double? = null,
        public val swapGravity: List<SwapGravity> = emptyList(),
        /** Confirmed ÷ planned leftover servings; null when no leftovers were planned. */
        public val cookRealism: Double? = null,
        public val gateReason: String? = null,
    )

    public fun compute(input: Input): AdherenceReport {
        val activeSlots =
            input.slots.filter { it.recipeId != null && it.state != PlannedSlotState.SWAPPED }
        val pastSlots = activeSlots.filter { it.dayEpochDay < input.asOfDayEpochDay }
        val terminal = pastSlots.filter { it.state != PlannedSlotState.PLANNED }
        val open = pastSlots.count { it.state == PlannedSlotState.PLANNED }

        val daysWithPlan =
            activeSlots
                .groupBy { it.dayEpochDay }
                .filter { (_, slots) -> slots.any { it.state != PlannedSlotState.SKIPPED } }
        val loggedDays =
            daysWithPlan.keys.count { day -> (input.actualKcalByDay[day] ?: 0.0) > 0.0 }

        val gateReason = "not yet meaningful"
        if (loggedDays < ConstantsRegistry.ADHERENCE_MIN_LOGGED_DAYS) {
            return AdherenceReport(meaningful = false, gateReason = gateReason)
        }

        val coverage =
            if (pastSlots.isEmpty()) {
                null
            } else {
                round1(terminal.size.toDouble() / pastSlots.size * 100.0)
            }

        val fidelityValues =
            daysWithPlan.entries.mapNotNull { (day, slots) ->
                val actual = input.actualKcalByDay[day] ?: return@mapNotNull null
                val planned = PlannerEngine.plannedDayTotals(slots).kcal
                if (planned <= 0.0) return@mapNotNull null
                kotlin.math.abs(actual - planned)
            }
        val fidelity = median(fidelityValues)?.let(::round1)

        val swaps =
            input.slots
                .filter { it.state == PlannedSlotState.SWAPPED }
                .groupingBy { it.recipeId ?: "" }
                .eachCount()
                .filter { it.key.isNotEmpty() }
                .map { (recipeId, count) -> SwapGravity(recipeId, count) }
                .sortedWith(compareByDescending<SwapGravity> { it.swaps }.thenBy { it.recipeId })

        val plannedLeftovers = activeSlots.filter { it.parentSlotId != null }
        val cookRealism =
            if (plannedLeftovers.isEmpty()) {
                null
            } else {
                val confirmed = plannedLeftovers.count { it.state == PlannedSlotState.CONFIRMED }
                round1(confirmed.toDouble() / plannedLeftovers.size * 100.0)
            }

        return AdherenceReport(
            meaningful = true,
            planCoveragePct = coverage,
            energyFidelityKcal = fidelity,
            swapGravity = swaps,
            cookRealism = cookRealism,
        )
    }

    private fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0
}
