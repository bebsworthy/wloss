package app.wlo.feature.f04.shopping

/**
 * F04 route contracts (ARCHITECTURE §2.2 `routes.kt` slot): the list and
 * pantry destinations, so the Plan tab's segmented bar and the deep-link
 * registry reach them without a feature→feature dependency (D2; :app's nav
 * graph wires them). IA.md §3 has no literal `wlo://` rows for these — the
 * registry additions follow the wlo://diary precedent (flagged for the IA
 * owner in the M5 report).
 */
public object F04Routes {
    /** The aisle-grouped shopping list (the in-store surface). */
    public const val LIST: String = "f04/list"

    /** The pantry: stock levels, expiry timeline, check-in. */
    public const val PANTRY: String = "f04/pantry"
}
