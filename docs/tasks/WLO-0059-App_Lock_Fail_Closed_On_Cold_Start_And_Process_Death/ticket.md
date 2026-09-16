---
id: WLO-0059
title: 'App lock: fail closed on cold start and process death'
status: done
theme:
release:
created: 2026-09-15T18:20:51Z
modified: 2026-09-15T18:37:43Z
closed: 2026-09-15T18:37:43Z
revision: 16ad090bdabce79b
blocks: []
related: [WLO-0057]
---

# Description

Implemented a fail-closed app-lock startup contract.

- `AppLockController` now starts in explicit `UNRESOLVED` posture; unresolved and locked postures require the gate.
- A new process resolves the persisted setting before `setContent`; enabled always starts locked for every timeout because the pre-death background duration is unknowable.
- Activity recreation preserves an unlock within the same process.
- `FLAG_SECURE` is applied before startup preference resolution or UI composition.
- The DataStore Compose fallback uses the synchronously read persisted value rather than a fail-open `false` default.
- Added JVM coverage for unresolved, enabled/disabled startup, recreation, and immediate/one-minute/five-minute thresholds.
- Added an API 29 device regression asserting enabled cold start composes no application destination and has a secure window.

Verification:

- `./gradlew :core:vault:jvmTest :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin --console=plain` — passed.
- `./gradlew :app:connectedDebugAndroidTest --console=plain -Pandroid.testInstrumentationRunnerArguments.class=app.wlo.app.AppLockColdStartTest` — passed on API 29.
- Scoped `git diff --check` — passed.

An additional rerun of the pre-existing `M6AppLockGateTest` was interrupted by concurrent shared-worktree Gradle/source changes and is not claimed as evidence.
