package app.wlo.core.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lock timeout (F13 §3 configurable): IMMEDIATE locks in a background blip;
 * ONE_MINUTE is the v1 default (a weigh-in glance-away must not relock);
 * FIVE_MINUTES for gym-tracked sessions.
 */
public enum class LockTimeout(
    public val wireName: String,
    public val millis: Long,
) {
    IMMEDIATE("immediate", 0L),
    ONE_MINUTE("1min", 60_000L),
    FIVE_MINUTES("5min", 300_000L),
    ;

    public companion object {
        public fun fromWire(wire: String): LockTimeout = entries.firstOrNull { it.wireName == wire } ?: ONE_MINUTE
    }
}

/**
 * The app-lock state holder (data API; PART B renders the lock surface).
 * `onBackground` on ON_STOP, then `shouldLock(now)` decides — the timeout is
 * measured from the last background transition, not from unlock.
 */
public class AppLockController {
    private val lockedState = MutableStateFlow(false)

    /** True = the lock surface should be showing (gate passed since last lock). */
    public val locked: StateFlow<Boolean> = lockedState.asStateFlow()

    private var backgroundSinceEpochMs: Long? = null

    public fun onUnlock() {
        lockedState.value = false
        backgroundSinceEpochMs = null
    }

    public fun onBackground(atEpochMs: Long) {
        if (!lockedState.value) backgroundSinceEpochMs = atEpochMs
    }

    public fun onForeground(
        atEpochMs: Long,
        timeout: LockTimeout,
    ) {
        val since = backgroundSinceEpochMs
        if (lockedState.value) return
        if (since != null && atEpochMs - since >= timeout.millis) lockedState.value = true
        backgroundSinceEpochMs = null
    }

    /** Does this controller need the gate for the CURRENT resume? */
    public fun shouldLock(
        nowEpochMs: Long,
        timeout: LockTimeout,
    ): Boolean {
        val since = backgroundSinceEpochMs ?: return lockedState.value
        return nowEpochMs - since >= timeout.millis
    }
}

/**
 * FLAG_SECURE route SET (IA §6 discreet mode: "FLAG_SECURE on all F08
 * screens and any F09 screen showing a photo"; F13 §3 adds the vault
 * surfaces and archive capture). F13 owns discretion mechanics, so the set
 * lives here; :app's MainActivity applies/clears the activity-window flag
 * from the current nav route (the Android application helper lives in
 * androidMain: [androidMain SecureSurfaces.applyFlagSecure]).
 */
public object SecureSurfaces {
    /** Routes that must never appear in screenshots or the recents thumbnail. */
    public val SECURE_ROUTES: Set<String> =
        setOf(
            "archive", // the F08 tab (R-U7 naming; IA §1)
            "stub/archive-capture", // wlo://archive/capture
            "stub/archive-compare", // wlo://archive/compare
            "stub/vault", // wlo://vault — the PART A stub (pre-PART-B builds)
            "f13/vault", // the Data Vault dashboard (M6 PART B)
            "f13/vault/restore",
            "f13/vault/export",
            "f13/vault/backup-setup",
            "f13/vault/import", // the CSV/bundle import wizard
            "applock/gate", // the app-lock gate (never in recents thumbnails)
        )

    /** Route prefixes treated as secure (a subtree, e.g. F09 photo contexts). */
    public val SECURE_ROUTE_PREFIXES: Set<String> =
        setOf(
            "f09/photo", // gut photo contexts (only photo-bearing F09 screens)
        )

    public fun isSecure(route: String?): Boolean {
        if (route == null) return false
        val path = route.substringBefore('?')
        if (path in SECURE_ROUTES) return true
        return SECURE_ROUTE_PREFIXES.any(path::startsWith)
    }
}
