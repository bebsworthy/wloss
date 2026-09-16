package app.wlo.app.navigation

/**
 * The `wlo://` deep-link registry (IA.md §3), as a pure, unit-pinned function
 * URI → nav route. Every registry row resolves to a real screen (the Hub, the
 * F02 diary/ladder, the F06 weigh-in surfaces) or a documented stub screen —
 * an explicit "lands in a later milestone" placeholder that still navigates.
 * Discretion-gated targets (Archive, gut photo context) land on their stub or
 * tab surface until the owning feature ships its lock gate.
 *
 * Registry rows added beyond IA.md §3's literal list: `wlo://diary` (the F02
 * day view) and `wlo://log/search` (the ladder) — the two surfaces PART B
 * makes real (flagged in the M3 report for the IA doc owner).
 */
public object WloDeepLinks {
    public const val SCHEME: String = "wlo"

    /** The four functioning release roots, in navigation order. */
    public val TAB_ROUTES: List<String> =
        listOf(WloTabs.WEIGHT, WloTabs.HUB, WloTabs.PLAN, WloTabs.MORE)

    /** The IA.md §3 registry: source · deep link · target. */
    public val REGISTRY: List<DeepLinkEntry> =
        listOf(
            // --- tabs (IA §1) ---
            entry("wlo://weight", WloTabs.WEIGHT),
            entry("wlo://hub", WloTabs.HUB),
            entry("wlo://plan", WloTabs.PLAN),
            entry("wlo://insights", INSIGHTS_UNAVAILABLE),
            entry("wlo://more", WloTabs.MORE),
            entry("wlo://archive", WloTabs.ARCHIVE),
            entry("wlo://digestion", WloTabs.DIGESTION),
            // --- real screens (M3) ---
            entry("wlo://weight/log", "f06/log"),
            // M4 PART B: the F02 capture flow (photo/barcode/label + the
            // correction loop) is real; the manual ladder keeps log/search.
            entry("wlo://log/capture", "f02/capture"),
            entry("wlo://log/search", "f02/log"),
            entry("wlo://log/quick-kcal", "f02/quick-kcal"),
            entry("wlo://diary", "f02/diary?entry={entry}"),
            entry("wlo://log/correct?entry={entry}", "f02/diary?entry={entry}"),
            // M5 PART B: the Plan tab is real (F03 week grid + recipes) and the
            // F04 list/pantry pipeline ships beside it — the list/pantry rows
            // follow the wlo://diary precedent (beyond IA.md §3's literal list;
            // flagged for the IA owner in the M5 report).
            entry("wlo://list", "f04/list"),
            entry("wlo://pantry", "f04/pantry"),
            entry("wlo://plan/tomorrow", "f03/plan/tomorrow"),
            entry("wlo://log/planned?slot={slot}", "f03/plan/focus?day={day}&slot={slot}"),
            entry("wlo://studio?proposal={proposal}", "f03/studio?proposal={proposal}"),
            // --- debug diagnostics (M4): the egress monitor renders the
            // persisted receipt ledger — F12 §3.8's "debug build renders a
            // live egress monitor". Debug builds only; release lands on a stub.
            entry("wlo://debug/egress", "debug/egress"),
            // M4 PART B: the F12 model-manager surface (F12 §3.2) — a real
            // screen beyond IA.md §3's literal list (flagged for the IA owner,
            // same precedent as wlo://diary).
            entry("wlo://ai/models", "ai/models"),
            // --- M6 PART B real surfaces -------------------------------------
            // Settings → AI (IA §1): the AI Studio + the receipt log (the
            // receipts row IS in IA.md §3 — "F12 receipts shortcut").
            entry("wlo://ai/studio", "f12/studio"),
            entry("wlo://ai/receipts", "f12/receipts"),
            // Settings (IA §1: gear on the Hub header — beyond IA.md §3's
            // literal registry; flagged for the IA doc owner, wlo://diary
            // precedent) and the Data Vault (IA §3: the backup-health dot's
            // target wlo://vault, now the real F13 dashboard).
            entry("wlo://settings", "app/settings"),
            entry("wlo://vault", "f13/vault"),
            // --- documented stubs (land in a later milestone, still navigating) ---
            stub("wlo://energy", "stub/energy", "Energy"),
            stub("wlo://checkin", "stub/checkin", "Check-in"),
            stub("wlo://algorithms", "stub/algorithms", "Algorithms"),
            stub("wlo://gut/log", "stub/gut", "Digestion log"),
            stub("wlo://exercise/start", "stub/exercise", "Workout"),
            stub("wlo://insights/report", "stub/insights-report", "Report card"),
            stub("wlo://insights/streaks", "stub/insights-streaks", "Streaks"),
            stub("wlo://archive/compare", "stub/archive-compare", "Compare"),
            stub("wlo://archive/capture", "stub/archive-capture", "Capture"),
        )

    /** `wlo://` nav patterns grouped by the route that hosts them. */
    public val patternsByRoute: Map<String, List<String>> =
        REGISTRY.groupBy(DeepLinkEntry::route, DeepLinkEntry::uriPattern)

    /** All registered `wlo://` URI patterns. */
    public val allPatterns: List<String> =
        REGISTRY.map(DeepLinkEntry::uriPattern)

    /**
     * Resolve a concrete `wlo://` URI to its nav route; null when the URI is
     * not a WLO link or names no known target. Query strings and trailing
     * slashes are ignored (`wlo://gut/log?context=meal:123` → `stub/gut`).
     */
    public fun routeFor(uri: String): String? {
        val prefix = "$SCHEME://"
        if (!uri.startsWith(prefix)) return null
        val path = uri.removePrefix(prefix).substringBefore('?').trimEnd('/')
        return REGISTRY.firstOrNull { registryPath(it.uriPattern) == path }?.route
    }

    public const val INSIGHTS_UNAVAILABLE: String = "more/insights-unavailable"

    /** The canonical `wlo://` pattern for a nav route (first registration wins). */
    public fun patternForRoute(route: String): String? = patternsByRoute[route]?.firstOrNull()

    private fun registryPath(uriPattern: String): String = uriPattern.removePrefix("$SCHEME://").substringBefore('?')

    private fun entry(
        uriPattern: String,
        route: String,
    ): DeepLinkEntry = DeepLinkEntry(uriPattern, route, owner = "", stub = false)

    private fun stub(
        uriPattern: String,
        route: String,
        owner: String,
    ): DeepLinkEntry = DeepLinkEntry(uriPattern, route, owner = owner, stub = true)
}

/** One registry row: the URI pattern, the nav route it opens, and its status. */
public data class DeepLinkEntry(
    /** Pattern with `{arg}` placeholders, e.g. `wlo://log/correct?entry={entry}`. */
    public val uriPattern: String,
    /** The nav route (same placeholder names), e.g. `f02/diary?entry={entry}`. */
    public val route: String,
    /** The stub's user-facing title (empty for real screens). */
    public val owner: String,
    /** True = documented stub screen: it navigates, the surface lands later. */
    public val stub: Boolean,
)
