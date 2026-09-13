package app.wlo.feature.f03.planning

/**
 * F03 route contracts (ARCHITECTURE §2.2 `routes.kt` slot): the public route
 * targets so the deep-link registry, the Plan tab and the Hub reach the
 * planner without a feature→feature dependency (D2; :app's nav graph wires
 * them). The Plan tab root itself stays `plan` (WloTabs.PLAN).
 */
public object F03Routes {
    /**
     * The planner focused on a given day (epoch-day) — `wlo://log/planned`
     * lands here with the touched slot highlighted; day defaults to today.
     */
    public const val FOCUS: String = "f03/plan/focus?day={day}&slot={slot}"

    /** The planner focused on tomorrow (the evening "plan tomorrow" card). */
    public const val TOMORROW: String = "f03/plan/tomorrow"

    /**
     * The plan-studio landing: the week plan with the pending-proposal arg
     * passed through (F01's Studio deep link; the diff UI itself is F01's).
     */
    public const val STUDIO: String = "f03/studio?proposal={proposal}"

    /** The recipe editor; empty id = create (R-U15's manual path). */
    public const val RECIPE_EDIT: String = "f03/recipe/edit?recipeId={recipeId}"

    /** Route argument names. */
    public const val ARG_DAY: String = "day"
    public const val ARG_SLOT: String = "slot"
    public const val ARG_PROPOSAL: String = "proposal"
    public const val ARG_RECIPE_ID: String = "recipeId"
}
