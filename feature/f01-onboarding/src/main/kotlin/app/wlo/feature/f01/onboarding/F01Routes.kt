package app.wlo.feature.f01.onboarding

/**
 * The module's route contracts (the `routes.kt` slot of ARCHITECTURE.md §2.2).
 *
 * F01 route contracts (ARCHITECTURE §2.2 feature shape: `routes.kt` carries the
 * public route targets + arg schemas, so cross-feature navigation never needs a
 * feature→feature dependency). M2 hosts the wizard inside the Hub route while
 * the gate is fresh (`:app` owns that decision, F10 owns the Hub surface);
 * later milestones reuse these constants for the Studio entry points
 * (IA §3: `wlo://studio?proposal=<id>`).
 */
public object F01Routes {
    /** The first-run wizard (this module's only screen in M2). */
    public const val WIZARD: String = "f01/wizard"

    /** Optional arg: the step to open on restore (defaults to the persisted draft step). */
    public const val ARG_STEP: String = "step"

    /** Studio entry — F01 §4 (plan revision / template editing); M3+ implements the surface. */
    public const val STUDIO: String = "f01/studio"

    /** The former first-run diet wizard, now an optional post-onboarding studio. */
    public const val PLAN_STUDIO: String = "f01/plan-studio"
}
