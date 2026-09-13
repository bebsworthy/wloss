package app.wlo.feature.f02.food

/**
 * F02 route contracts (ARCHITECTURE §2.2 `routes.kt` slot): the public route
 * targets + arg schemas, so cross-feature navigation never needs a
 * feature→feature dependency (:app's nav graph wires F10's diary link here).
 */
public object F02Routes {
    /** The diary day view (default day = today). */
    public const val DIARY: String = "f02/diary"

    /** The manual input ladder surface: text hint → search → portions. */
    public const val LOG: String = "f02/log"

    /** The kcal-only quick-add sheet, opened over the ladder. */
    public const val QUICK_KCAL: String = "f02/quick-kcal"

    /**
     * The full-screen capture flow (F02 §3 rungs 1-3): photo -> scan ->
     * correct -> save, with the barcode/label assists and their R-U15 manual
     * twins in the same surface. IA.md §3 rows this as `wlo://log/capture`.
     */
    public const val CAPTURE: String = "f02/capture"

    /** Optional arg of [DIARY]: the epoch day to render (defaults to today). */
    public const val ARG_DAY: String = "day"

    /** Optional arg of [DIARY]: an entry id to open the provenance sheet for. */
    public const val ARG_ENTRY: String = "entry"
}
