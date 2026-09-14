package app.wlo.feature.f06.weight

/**
 * F06 route contracts (ARCHITECTURE §2.2 `routes.kt` slot): the public route
 * targets, so the Hub and the deep-link registry reach weight surfaces without
 * a feature→feature dependency (:app's nav graph wires them).
 */
public object F06Routes {
    /** Trend/history: the chart, the smoother tuner, the day log. */
    public const val WEIGHT: String = "f06/weight"

    /** The same surface with the weigh-in sheet open (wlo://weight/log). */
    public const val LOG: String = "f06/log"

    /** The in-app math documentation screen (each smoother's formula + terms). */
    public const val MATH: String = "f06/math"

    /** The body-fat method registry (Navy / RFM) with provenance. */
    public const val BODY_FAT: String = "f06/bodyfat"

    /** The full-page logbook (WLO-0055): every verbatim entry, month sections. */
    public const val LOGBOOK: String = "f06/logbook"
}
