# WLO-0097 — Weight-first setup, useful settings and accurate reminders

## Read this first

This is an implementation handoff, not a record of completed code. WLO is a free, local-first Android application; weight loss, maintenance and gain are supported without shame-based copy. Work from the repository root. Read `AGENTS.md`, `docs/tech/ARCHITECTURE.md`, `docs/features/FEATURES.md` (§2.0 and §3), `docs/design/DESIGN-SYSTEM.md`, and `docs/design/IA.md`. The evidence and rationale are in `docs/tech/WLO-0087-WEIGHT-UX-UI-REVIEW.md`; the surface inventory is `docs/tech/WLO-0086-WEIGHT-UX-UI-INVENTORY.md`. The dispatch/ownership index is `docs/tech/WLO-0100-WEIGHT-UX-IMPLEMENTATION-HANDOFF.md`.

Use the repository's installed Compose Material 3 APIs and existing dependency versions. Standard components and documented slots are mandatory; a genuinely necessary custom component needs a local KDoc explaining the platform limitation and a reference to this ticket. Keep Android UI out of core commonMain, persistence behind repositories/ports, cross-feature navigation in `:app`, and dependencies compliant with the architecture checker. Preserve existing user changes. Update this ticket through `ticket`, never edit its mirror files directly.

Requirements below are proposed implementation decisions. Where a named frozen ruling conflicts, update that governing contract explicitly in the same change before implementing it; do not portray this handoff as an already-ratified change. Unrelated frozen rulings remain in force. Do not invent clinical thresholds or relax safety holds. Baseline inspected: commit `66fd2c5`; locate symbols again if intervening work moves them.

## Outcome and source map

A10/A16: setup ends at the task the user selected; reminders describe their actual schedule/permission state; settings prioritize weight tracking over optional AI configuration.

Read `feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/{domain/WeightFirstOnboarding.kt,state/WeightFirstOnboardingViewModel.kt,ui/WeightFirstOnboardingScreen.kt}`, `app/src/main/kotlin/app/wlo/app/navigation/WloApp.kt` (`OnboardingGatedSurface`), `app/src/main/kotlin/app/wlo/app/ui/settings/SettingsScreen.kt`, and `app/src/main/kotlin/app/wlo/app/notification/WeighInReminder.kt`. Unit settings use SettingsStore plus profile mirroring; current notification text says morning even for evening schedule. File choice already navigates to import; preserve that behavior while completing Health Connect handoff.

## Dependencies and scope

Integrate after WLO-0093 goal creation contract, WLO-0095 import recovery and WLO-0096 route metadata. WLO-0091 supplies M3 controls. Own weight-first onboarding completion, Settings grouping/reminder configuration and permission repair. Do not redesign the full legacy diet wizard or AI consent system. AI remains per-capability opt-in/BYOK where applicable; no toggle consolidation that grants unrelated consent.

## Setup contract

1. Each setup step has a clear primary verb and a distinct optional Skip where supported. Remove ambiguous “Continue or skip”. Final CTA reflects selected source: “Open Weight”, “Import a file” or “Connect Health Connect”. Review summarizes units, entered starting measurement, optional goal intent and source choice before completion.
2. Persist a typed post-completion destination/intent, not a transient navigation event. Complete the profile/initial-reading operation idempotently, then navigate to the selected task. File choice opens existing import; Health Connect opens its provider/permission/import surface directly; manual/skip opens Weight. Mark intent consumed after destination acknowledgement so rotation/process death cannot lose it or repeatedly reopen it after successful completion.
3. `FinishWeightFirstOnboarding` currently writes settings/profile/measurement/completion separately. Add a durable operation ID/journal or atomic core-data operation for the Room portions, followed by recoverable preferences/completion updates. Retry must reuse the same profile and initial event, never create duplicates. Do not use kg/date equality as a uniqueness key. Completion flag is set only when required writes have succeeded; failures retain draft and surface Retry.
4. Goal intent remains an editable intent until explicitly saved through F01's authorized writer (WLO-0093). Onboarding must not display it as an already active versioned target if none exists. Skipping a goal never blocks logging. Back within setup preserves entered values; a newly selected source replaces the old pending handoff.

## Settings and reminder contract

Order Settings groups: Weight tracking (units, profile facts, goals, reminder), Appearance/interaction controls that actually exist (including haptics), Data/connections (import/export/backup/Health Connect), then optional AI/capability controls and app information. Use standard ListItem/Switch/navigation rows, sentence-case headings and supporting summaries. Preserve existing settings and routes; this is grouping, not removal of functionality.

Replace six preset time chips with a labeled row showing the selected local time and a standard M3 TimePicker dialog. Store hour/minute; respect 12/24-hour display preference. Cancel does not change saved time or worker schedule. Confirm updates the preference and uniquely replaces the scheduled work once. Label reminders approximate: WorkManager cannot promise an exact alarm. Do not add exact-alarm permission.

Model user preference separately from effective availability: enabled+permitted, disabled, permission denied, app notifications disabled, channel disabled and scheduling error. Recheck platform notification availability on resume. The switch must not claim an effective reminder when OS/channel blocks it; show supporting explanation and an explicit permission/settings repair action. Android 13+ requests notification permission only after enabling; denial keeps a recoverable state without repeated automatic prompts. On older Android inspect app/channel availability where applicable.

Notification copy must work at any time: “Time for a weigh-in” with a neutral optional action, not “morning”, “missed”, streak loss or guilt. Tap opens capture through the existing secure navigation/app-lock path. Timezone/clock changes recompute the next approximate local occurrence; daylight-saving gaps move to the next valid local time, overlaps schedule one occurrence. Disable cancels pending work. Use injected clock/scheduler in tests.

## Acceptance scenarios

- A97-01: manual, file, Health Connect and skipped-source completions land in their correct surfaces. Rotate/kill between completion and navigation: pending handoff resumes once, without duplicate profile/starting weight.
- A97-02: fail each completion write, retry, and confirm one profile/initial measurement/completion record. Draft remains until success. Goal intent is not mislabeled as active Targets.
- A97-03: settings initially exposes units/profile/goals/reminder before AI. Changing mass unit updates Weight, Goals and body length convention consistently; no stale mixed-unit summary after return.
- A97-04: choose 20:30; cancel leaves old schedule, confirm enqueues one replacement. Notification copy contains no morning-only assertion and the summary says approximate/local time.
- A97-05: deny permission, revoke it in system settings, disable the channel, then return. The effective state and repair action match the platform; no false “on” state. Regrant and explicitly enable recovers without duplicate workers.
- A97-06: synthetic DST gap/overlap and timezone-change tests schedule one next valid occurrence; disabling cancels it. Notification tap obeys app lock and opens capture after unlock.
- A97-07: at 200% font with TalkBack, time row is named, switches have correct state, optional setup paths are distinct and every button is reachable above IME/insets.

## Validation

Extend WeightFirstOnboardingTest and OnboardingResumeTest for durable completion/handoff. Add scheduler tests with fake time and platform-availability facade; manually validate notification permission/channel repair on API29 and a current emulator supporting runtime notification permission. No background AI, health permission or cloud consent is requested as a side effect.

## Completion and evidence

The ticket stays in todo until implementation begins, then moves to doing. It is done only when the scenarios above pass, relevant existing tests remain green, documentation describes shipped behavior, and the implementation report lists changed files, commands/results, screenshots or recordings for affected UI states, and any unverified checks. An unrun check is not a pass. Attach evidence under `docs/tech/<ticket-id>-evidence/` using synthetic data; never include real health records or credentials.

Run the ticket-specific commands below from the repository root, plus `./gradlew :app:assembleDebug checkArchitecture` and the affected modules' `ktlintCheck`/`detekt` tasks. Instrumentation uses Android Test Orchestrator with `clearPackageData=true`: run only on a disposable emulator. Do not use `just fresh`, `just demo`, or instrumentation on a user's data-bearing device. Manual accessibility or physical haptic checks must be explicitly marked unverified if unavailable. No dependency upgrade, cloud service, unrelated feature rewrite, or visual redesign outside the stated scope is authorized by this handoff.

```sh
./gradlew :feature:f01-onboarding:testDebugUnitTest :app:testDebugUnitTest :core:data:jvmTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.wlo.app.OnboardingResumeTest
```
