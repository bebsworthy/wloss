package app.wlo.feature.f12.consent

/**
 * F12 route contracts (ARCHITECTURE §2.2 `routes.kt` slot). The AI Studio is
 * Settings → AI (IA.md §1 — consent is visited deliberately, never browsed);
 * the receipt log keeps the registry link `wlo://ai/receipts` (IA §3, "F12
 * receipts shortcut"); the point-of-use consent sheet ships with a demo
 * surface so the component is reachable, inspectable, and screenshot-able
 * before any cloud call exists to raise it in context (BYOK is v1.x).
 */
public object F12Routes {
    /** The AI Studio: kill switch + six consent rows + BYOK shell + diagnostics. */
    public const val STUDIO: String = "f12/studio"

    /** The hash-chained AI receipt log (`wlo://ai/receipts` in IA §3). */
    public const val RECEIPTS: String = "f12/receipts"

    /** The point-of-use consent sheet component, demo-hosted (v1.x callers). */
    public const val CONSENT_SHEET_DEMO: String = "f12/consent-demo"
}
