# WLO-0096 implementation report

## Shipped behavior

- Reduced primary navigation to the four functioning release destinations: Weight, Hub, Plan, and More. Weight remains the start destination and Plan remains fully reachable.
- Added a single route-metadata contract for destination identity, label, top-level owner, app-bar/pane title, and Up behavior.
- Shell-owned Material 3 top app bars now cover top-level and nested routes; redundant Weight, Plan, More, logbook, math, and body-fat content titles were removed.
- Preserved single-top/save-state/restore-state top-level switching and made nested Up fall back to the owning top-level destination.
- Retained the 600dp navigation-bar/rail boundary and pinned the 840dp supporting-pane eligibility boundary in policy tests.
- Legacy `wlo://insights` now opens More with a neutral availability explanation rather than a blank placeholder.
- Ranged logbook routes retain inclusive-start/exclusive-end arguments; reversed ranges fall back to the full logbook with an explanation.
- Amended R-D2 and IA to distinguish the four-destination release shell from the future five-destination target.

## Verification

- `./gradlew :app:testDebugUnitTest :feature:f06-weight:testDebugUnitTest` — passed.
- `./gradlew :app:ktlintCheck :app:detekt :feature:f06-weight:ktlintCheck :feature:f06-weight:detekt` — passed.
- `./gradlew :app:assembleDebug checkArchitecture` — passed; D1–D7 and D9 clean.
- Unit tests pin the four destinations, legacy Insights fallback, route ownership/title/Up metadata, 599/600dp navigation boundary, and 839/840dp pane eligibility boundary.

## Not verified in this environment

- `WloShellTest`/`WloDeepLinkTest` device instrumentation, resize recordings, 200% text/IME traversal, fold/hinge occlusion, and TalkBack focus order were not run because no confirmed disposable emulator or foldable test surface was available. These remain unverified, not passes.
- Expanded Weight supporting-pane and logbook list-detail behavior require device-layout validation; compact behavior and state restoration remain the verified release fallback when that layout information is unavailable.
