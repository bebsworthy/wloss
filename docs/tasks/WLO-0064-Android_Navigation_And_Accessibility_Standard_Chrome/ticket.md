---
id: WLO-0064
title: 'Android navigation and accessibility: standard chrome, adaptation, semantics'
status: done
theme:
release:
created: 2026-09-15T18:20:51Z
modified: 2026-09-15T19:17:01Z
closed: 2026-09-15T19:16:46Z
revision: a0c106637332c1b8
blocks: []
related: [WLO-0057, WLO-0082]
---

# Description

## Implemented

- Reworked the app shell around standard Material 3 chrome: compact top-level destinations use `NavigationBar`; windows at 600 dp and above use `NavigationRail`; nested destinations hide top-level chrome and show `TopAppBar` with a semantic Up action.
- Adopted Navigation Compose's standard save/restore recipe (`findStartDestination`, `saveState`, `launchSingleTop`, `restoreState`) so top-level UI state survives destination switching.
- Added a small, pure route/window policy rather than a new navigation abstraction, with focused JVM tests for top-level classification, titles, and the 600 dp boundary.
- Corrected dark `text-tertiary` to `#808C9A` (4.68:1 minimum against raised surfaces) while retaining a separate light-scheme token.
- Added missing heading, live-region, pane-title, and sheet semantics; made icon action descriptions non-null; prevented navigation labels from wrapping into unusable multi-line controls.
- Updated IA and design-token documentation.

## Verification

- `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, focused navigation policy JVM tests, and design-system compilation passed.
- Full `WloShellTest` passed on the API 29 emulator after the nested-Up and top-level-state tests were added.
- Compact navigation/tab switching passed at font scales 1.0, 1.5, and 2.0.
- Expanded navigation rail passed on a 1600x1200 emulator window (>600 dp).
- `checkArchitecture` passed: 28 projects, D1-D7 + D9 clean, 166 dependency edges, 704 external artifacts, zero UI-atom violations.
- App ktlint reports no WLO-0064-file violation; the aggregate task remains red only for concurrent WLO-0059 files (`WeighInReminder.kt`, `WloApplication.kt`).
