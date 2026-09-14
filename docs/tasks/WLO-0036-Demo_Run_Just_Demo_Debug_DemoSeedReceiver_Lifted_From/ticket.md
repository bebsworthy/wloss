---
id: WLO-0036
title: 'Demo run: just demo — debug DemoSeedReceiver (lifted from SeedingRobot) + justfile boot/demo recipes'
status: done
theme:
release:
created: 2026-09-14T13:18:03Z
modified: 2026-09-14T14:00:27Z
closed: 2026-09-14T14:00:27Z
revision: 7e047526c7e59f8c
blocks: []
related: []
---

# Description

## Goal

`just demo` — one command that runs the app on the emulator WITH the demo
dataset. Today the dataset exists only inside the androidTest SeedingRobot;
a fresh install starts empty at onboarding (the justfile's `fresh` comment
claiming "resets the seeded demo data" is stale — nothing seeds).

## Design (agreed)

- **Debug-only seeder in the app process**: move the reusable seeding core
  (onboardAndSeedWeek / seedWeek / the weigh-in series) from
  `app/src/androidTest/.../SeedingRobot.kt` into
  `app/src/debug/kotlin/app/wlo/app/demo/` (single implementation — androidTest
  sees debug sources, so SeedingRobot becomes a thin delegate or its call
  sites update; zero duplication). The robot's deps come from the app's own
  Koin graph (GlobalContext) — same access works from a receiver.
- **DemoSeedReceiver** registered in `app/src/debug/AndroidManifest.xml`
  (exported, action `app.wlo.demo.SEED`, explicit component broadcast). It
  must: skip politely when data already exists (never clobber — check the
  onboarding flag / active profile; log + toast instead), seed when fresh
  (onboard + diary week + weigh-in series + `dealWeekPlan` so the meals card
  and log-as-planned work), then log a `wlo-demo` line ("seeded" / "skipped")
  the recipe can wait on. All writes anchor to the app's frozen demo clock
  (DEMO_NOW) — coherent with every screen.
- **Check the dependency**: DiarySeeder lives in `:core:testing`; if :app only
  has it as androidTestImplementation, add `debugImplementation`.
- **Justfile**: factor the emulator-boot block out of `run` into a `boot`
  recipe (keep `run` behavior identical), then add:
  `demo: build` → boot → install -r → pm clear → broadcast SEED → wait for
  the wlo-demo logcat line (timeout) → resolve launcher → launch.
  Fix the `fresh` comment (clear only; pair with `just demo` to reseed).
  Update the header comment.

## References (spec + mockup contracts the dataset implements)

- **Mockup (visual contract):** `docs/design/flows/01-day-loop-f10.html` —
  the seeded Hub reproduces the day-loop frames: weigh-in/trend cards with
  the 45-day series, calories ring, meals-today row with "Log as planned",
  diary card, streak chip in the header (pins 1–7).
- **F01 onboarding** (`docs/features/F01-onboarding-diet-plans.md`) — the
  seeder replays the wizard's Start write programmatically (profile + Targets
  v1 via DietTemplateApplier, completion flag) instead of driving the UI.
- **F02 food logging** (`docs/features/F02-food-logging.md`) — the seeded
  week comes from `core/testing` DiarySeeder through the real diary door
  (EntryVia.MANUAL_SEARCH / QUICK_ADD, computed macros).
- **F03 meal planning** (`docs/features/F03-meal-planning.md`) —
  `dealWeekPlan` deals a deterministic 7-day plan through `generateWeek`
  (seed 42) so today has open slots for the meals card + log-as-planned.
- **F06 weight** (`docs/features/F06-weight-body-metrics.md`) — the 45-day
  weigh-in series encodes the spec's semantics: daily morning readings,
  one double-weigh-in day (evening re-weigh HIGHER so lowest-of-day keeps
  the morning value, ruling R-B8 in `docs/features/FEATURES.md` §3), and a
  trend within ~0.5 kg of the latest raw reading.
- **Demo clock** — `app/src/main/.../di/FixedClock.kt` (`DEMO_NOW`,
  2026-09-08T07:12Z): every seeded write anchors to it, which is why the
  demo renders the canonical "Tuesday 8 sep" state consistently.

## Gates

- `./gradlew :app:assembleDebug` (+ test compile of androidTest) green;
  architecture enforce 0; ktlint + detekt green.
- `connectedDebugAndroidTest` stays 60/60/0 (robot delegation must not change
  seeding behavior).
- **Live proof**: run `just demo` (or the equivalent adb sequence) on the
  emulator, screenshot the seeded Hub into /tmp/wlo-uifix/p7-demo-hub.png —
  expect: trend card with the 45-day series, streak chip, ring, meals card
  with an open slot ("Log as planned" usable), no onboarding wizard.
  Also `just fresh`-equivalent then relaunch shows onboarding (empty state).
