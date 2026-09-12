# ADR-002: minSdk = API 29 (Android 10)

**Status:** Accepted (owner decision q-000023, 2026-09-11; validated by research 2026-09-11)
**Decides:** T-A3

## Context

minSdk was unstated anywhere in the docs. Owner picked API 29; research validated it
against 2025–2026 distribution data and the app's actual feature dependencies.

## Decision

`minSdk = 29`. `targetSdk`/`compileSdk` track the latest stable (compileSdk 36/37 era in
2026; Compose 1.12 requires compileSdk 37 + AGP 9.1.1).

## Evidence (researched 2026-09-11)

- **Coverage cost:** API <29 ≈ **8.9%** (StatCounter-derived, Apr 2026) to **9.3%**
  (Google distribution data, Dec 2025) of active devices globally — less in Western
  markets (e.g. US Android 16 share 39%, Aug 2026). composables.com chart (Dec 2025),
  apilevels.com (May 2026), gs.statcounter.com (Aug 2026).
- **Nothing the app needs sits above 29:** Compose (minSdk 21; `Modifier.blur` no-op
  below 31 — cosmetic, suppressed anyway under reduced motion), CameraX (21),
  WorkManager 2.11.2 (23), androidx.biometric (23; framework BiometricPrompt from 28),
  Health Connect (SDK 26+; app requires 28+ — an API 26 floor would buy *unusable*
  reach), ARCore (device-list-gated, Depth API on >88% of active devices, May 2026).
- **Known graceful degradations at 29:** haptic `Composition` primitives (API 30) and
  `VibratorManager` (31) fall back to predefined effects — exactly the fallback path
  DESIGN-SYSTEM §5 already specifies; exact-alarm permission only exists at 31+ (below
  that `setExact` needs no permission); `POST_NOTIFICATIONS` (33) version-guarded with
  the F10 in-context ask.
- **Gemini Nano/AICore is hardware-gated (2024+ flagships, Android 14+), not
  API-floor-gated** — no floor choice changes its reach.
- **vs alternatives:** floor 26 buys ~3.1% (API 26–27) of devices that cannot run the
  Health Connect app at all; floor 31 sacrifices 10.5–21.5% (sources differ) for only
  blur + haptic primitives.

## Play-release flags discovered (release checklist, not blockers)

- **Health apps declaration is mandatory** in Play Console; weight management is
  explicitly a covered category.
- Reported Jan 2026 tightening: health-category apps may require an **organization
  developer account** (D-U-N-S) — *third-party-sourced; verify against official Play
  policy before release.* A "not a medical device" disclaimer is needed.
- **AI-Generated Content policy** applies to the F12 features: in-app AI-content
  flagging, output moderation, AI labeling.
- Runtime model downloads (R-S14) are policy-safe (assets, not executable code);
  disclose network use in the Data safety form.

## Consequences

- R-U16/FLAG_SECURE, consent gate, etc. unaffected. Haptics fallback matrix is a
  test-matrix item on API 29/30 devices.
