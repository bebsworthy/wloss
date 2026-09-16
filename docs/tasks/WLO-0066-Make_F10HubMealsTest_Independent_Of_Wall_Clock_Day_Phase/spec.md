# Problem

F10HubMealsTest.logAsPlanned_writesOneDiaryEntry_andRetiresTheSlot assumes the daytime "Log as planned" action exists. The full API 29 suite fails when run during the night phase, although the product correctly omits that action then.

# Acceptance

- The test binds a deterministic daytime clock or otherwise establishes a day model that includes the planned-meal action.
- The focused F10HubMealsTest and the full connectedDebugAndroidTest suite pass independently of local wall-clock time.

# Evidence

WLO-0065 final API 29 run: 64/65 passed; this was the sole failure.


# Implementation evidence (2026-09-16)

- `F10HubMealsTest` now overrides `ClockPort` before seeding/Hub composition with a `FixedClock` pinned to 12:00 on the device's current local date. This keeps repository day boundaries coherent while making the daytime-only planned-meal action deterministic.
- `./gradlew :app:compileDebugAndroidTestKotlin`: pass.
- `./gradlew :app:ktlintCheck`: pass.
- Focused API 29 run at approximately 00:14 local time: `F10HubMealsTest` passed 2/2, directly reproducing the former night-phase condition.
- Full API 29 suite: WLO-0066/F10 tests passed, but the suite finished 64/66 because two `M3WeighInTest` cases failed under concurrent WLO-0052 unit/date-time changes. No F10HubMealsTest failure remained. Ticket stays doing until the full-suite acceptance gate is green.
