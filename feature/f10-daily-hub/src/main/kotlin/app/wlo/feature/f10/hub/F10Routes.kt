package app.wlo.feature.f10.hub

/**
 * F10 route contracts (ARCHITECTURE §2.2 `routes.kt` slot). The Hub IS the
 * app's home tab (IA §1); cross-feature links leave through callbacks the
 * composition root wires onto the owning features' route contracts — the Hub
 * holds no feature→feature dependency (D2).
 */
public object F10Routes {
    /** The home tab surface. */
    public const val HUB: String = "hub"
}
