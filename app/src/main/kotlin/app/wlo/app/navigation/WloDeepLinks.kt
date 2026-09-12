package app.wlo.app.navigation

/**
 * The `wlo://` deep-link registry (IA.md §3), as a pure function URI -> tab
 * route. Tab roots are `wlo://<tab>`; every card/notification/widget target in
 * the IA registry maps onto its owning tab for M1 (the finer surfaces do not
 * exist yet). Discretion-gated targets (Archive, gut photo context) still land
 * on their tab surface — the lock gate ships with F08 itself.
 */
public object WloDeepLinks {
    public const val SCHEME: String = "wlo"

    /** The five tabs, in navigation order (R-D2). */
    public val TAB_ROUTES: List<String> =
        listOf(WloTabs.HUB, WloTabs.PLAN, WloTabs.INSIGHTS, WloTabs.ARCHIVE, WloTabs.DIGESTION)

    /** IA.md §3 registry targets and the tab that owns them in M1. */
    private val ALIASES: Map<String, String> =
        mapOf(
            // Hub-owned surfaces (F10 hub cards, F02 capture, F06 numbers, F07
            // check-in, F01 studio, F13 vault, algorithms).
            "weight" to WloTabs.HUB,
            "weight/log" to WloTabs.HUB,
            "energy" to WloTabs.HUB,
            "log/capture" to WloTabs.HUB,
            "log/quick-kcal" to WloTabs.HUB,
            "log/planned" to WloTabs.HUB,
            "log/correct" to WloTabs.HUB,
            "checkin" to WloTabs.HUB,
            "studio" to WloTabs.HUB,
            "vault" to WloTabs.HUB,
            "algorithms" to WloTabs.HUB,
            "ai/receipts" to WloTabs.HUB,
            "exercise/start" to WloTabs.HUB,
            // Plan-owned (F03/F04 pipeline).
            "plan/tomorrow" to WloTabs.PLAN,
            // Insights-owned (F11).
            "insights/report" to WloTabs.INSIGHTS,
            "insights/streaks" to WloTabs.INSIGHTS,
            // Archive-owned (F08) — lands behind the lock gate when it ships.
            "archive/compare" to WloTabs.ARCHIVE,
            "archive/capture" to WloTabs.ARCHIVE,
            // Digestion-owned (F09).
            "gut/log" to WloTabs.DIGESTION,
        )

    /** `wlo://` patterns that should open the given tab (tab root first). */
    public val patternsByRoute: Map<String, List<String>> =
        buildMap {
            for (route in TAB_ROUTES) {
                put(
                    route,
                    listOf(patternForRoute(route)) +
                        ALIASES.filterValues { it == route }.keys.map { target -> "$SCHEME://$target" },
                )
            }
        }

    /** All registered `wlo://` URI patterns (tabs + registry aliases). */
    public val allPatterns: List<String> =
        patternsByRoute.values.flatten()

    /**
     * Resolve a `wlo://` URI to a tab route; null when the URI is not a WLO
     * link or names no known target. Query strings and trailing slashes are
     * ignored (`wlo://gut/log?context=meal:123` -> `digestion`).
     */
    public fun routeFor(uri: String): String? {
        val prefix = "$SCHEME://"
        if (!uri.startsWith(prefix)) return null
        val target = uri.removePrefix(prefix).substringBefore('?').trimEnd('/')
        return when {
            target in TAB_ROUTES -> target
            else -> ALIASES[target]
        }
    }

    /** The canonical `wlo://` pattern for a tab route. */
    public fun patternForRoute(route: String): String = "$SCHEME://$route"
}
