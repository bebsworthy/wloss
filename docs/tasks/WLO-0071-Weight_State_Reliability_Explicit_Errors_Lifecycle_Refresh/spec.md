# Objective
Ensure Weight and Hub never disguise failures or stale time state as valid content.

# Scope
- Model loading, content, empty, and recoverable-error states explicitly.
- Preserve repository errors instead of broadly converting them to null/empty.
- Refresh on app resume, local-date rollover, relevant day-phase boundary, and timezone change.
- Keep Hub and F06 on the same clock and canonical trend answer.
- Add deterministic ViewModel tests for failures, retries, races, midnight, timezone travel, and process recreation.
- Use small pure presentation assemblers/state holders when splitting the broad WeighInViewModel; do not add a use-case framework.

# Acceptance
Storage failure never looks like no history; an app left open crosses boundaries correctly; retry recovers; stale async work cannot overwrite newer state; focused and connected tests are wall-clock independent.


## Implementation evidence (2026-09-16)

- F06 exposes explicit `Loading`, `Empty`, `Content`, and recoverable `Error` load states. Repository/settings failures are preserved as errors instead of becoming empty data, a failed refresh retains the last valid snapshot, and the M3 screen renders progress or a retryable warning banner.
- F06 refreshes on lifecycle resume and on local-day/time-zone boundary changes. Refresh generations cancel/ignore stale reads, and successful mutations reload the canonical snapshot.
- Hub exposes explicit fresh/loading, ready, and recoverable-error states; all repository failures remain visible. Its coherent `HubMoment` drives day phase/date/zone reads, with refreshes on resume, exact phase/midnight boundaries, and system date/time/time-zone broadcasts. Rapid/stale work cannot publish over a newer generation.
- F06 and Hub both consume `SettingsStore.massUnit`; Hub retains the canonical trend/forecast path. WLO-0080 owns the shared `WeightGoalSafety` forecast eligibility gate and was coordinated separately; WLO-0071 does not introduce a safety bypass.
- Deterministic JVM coverage verifies F06 repository and settings failures, retry, retained content after a failed refresh, stale-load suppression, midnight rollover, time-zone travel, and recreation; Hub coverage verifies repository/profile failures, retry, phase/midnight rollover, time-zone travel, rapid generations, slow-old-day suppression, and recreation.
- Focused device acceptance passed on `emulator-5554`: `M3WeighInTest#weightSurface_activityRecreationRefreshesWithoutAnErrorState` and `M3ScreensTest#hubSurface_activityRecreationRefreshesTheLifecycleSnapshot`. There is no injectable repository-failure seam in the production device graph, so failure/retry acceptance is intentionally deterministic ViewModel coverage rather than a synthetic connected test.
- Verification passed: `:feature:f06-weight:testDebugUnitTest`, `ktlintCheck`, `detekt`, `lintDebug`; the same four gates for `:feature:f10-daily-hub`; `:app:ktlintAndroidTestSourceSetCheck`; `:app:compileDebugAndroidTestKotlin`; `checkArchitecture` (D1-D7 + D9 clean); focused connected tests above; and scoped `git diff --check`.
