---
id: WLO-0049
title: 'Real system clock: replace frozen FixedClock(DEMO_NOW) binding — app and writes live on 2026-09-08'
status: todo
theme:
release:
created: 2026-09-14T14:03:45Z
modified: 2026-09-14T14:04:54Z
closed:
revision: a55611fca70c4f4f
blocks: []
related: []
---

# Description

## The defect

The app does not use the device clock. `app/src/main/kotlin/app/wlo/app/di/
WloModule.kt` line ~96 binds the production graph to a frozen instant:

    single<ClockPort> { FixedClock(FixedClock.DEMO_NOW) }
    // FixedClock.DEMO_NOW = 2026-09-08T07:12:00Z ("a Tuesday morning, 07:12")

The binding is unconditional — not gated to debug builds or tests. Every
"today" in the app derives from it: the Hub header date, day projections,
the trend window, the forecast start day, the streak anchor, check-in and
reminder math. Worse, WRITES are stamped with it (they flow through the same
ClockPort): a weigh-in or "Log as planned" tapped today is filed under
2026-09-08 in the database. A real install lives in the past — currently six
days, forever.

Owner noticed 2026-09-14: emulator clock says Sep 14, app renders
"Tuesday 8 sep". This blocks dogfooding (WLO-0029 alpha): any data collected
by an alpha user is date-corrupted.

## Why it exists

M1 scaffold — FixedClock's KDoc: "M1 has no scheduler yet; time moves in a
later milestone." The swap never happened; the entire demo/test pipeline was
subsequently built around the pinned Tuesday.

## Fix

1. Bind the real system clock in the app graph
   (`ClockPort` backed by `kotlinx.datetime.Clock.System`), keep FixedClock
   as a test utility only (tests that want determinism construct it or
   override the binding explicitly — engines never see it either way, D7:
   they take `Instant` parameters).
2. The demo dataset (WLO-0036 / `just demo`) is authored relative to
   "today" already (`today - daysAgo`), so seeding works unchanged on a real
   clock — the demo simply renders real dates instead of the canonical
   Tuesday. Verify, don't assume.
3. Sweep for hardcoded-date assertions in androidTest (grep for "8 sep",
   "Sep", "10 Aug", weekday names, rendered-date strings) and convert them
   to relative-today assertions; where a test genuinely needs a frozen
   clock, override the ClockPort binding in that test's graph instead of
   reinstating a global pin.
4. Keep `just demo` / `just fresh` behavior intact (live-proof both after
   the change; screenshot the Hub showing the REAL date).

## Gates

- `./gradlew :app:assembleDebug` green; checkArchitecture enforce 0;
  ktlint + detekt green.
- `connectedDebugAndroidTest` 60/60/0 with no global date pin remaining
  (`grep -rn "DEMO_NOW" app/src/main` returns only the FixedClock
  definition, not a binding).
- `just demo` live proof: Hub header shows the emulator's REAL date;
  a weigh-in logged in the demo appears under today's day, not Sep 8.
- JVM suites untouched-green.
