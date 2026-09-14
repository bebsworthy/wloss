---
id: WLO-0035
title: 'F06 measurement + targets flow rethink: logbook with edit/void, back-dating, full-depth chart windows, body-fat as a series, goals/profile editor after onboarding'
status: doing
theme:
release:
created: 2026-09-14T12:57:04Z
modified: 2026-09-14T13:18:05Z
closed:
revision: 5a762d95c1461d85
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
