## Owner ruling (frozen)
- Mass unit = **global** user preference: **kg | lb** (stone stays out).
- Asked **exactly once**, during onboarding. Never re-asked.
- Changeable **only in Settings**. No other surface offers a switch —
  no unit chips-as-choice, no per-entry unit pick. People use the unit they
  know; a switcher is pointless chrome.
- Every weight render and every typed weight input reads the active unit.
  Storage stays canonical kg (`MassUnit.toKilograms` on save,
  `fromKilograms`/`format` on render). A unitless body number is a defect
  (R-D10); a hardcoded unit in copy likewise.

## Current state (audit, 2026-09-14)
- `core/common/Units.kt`: `MassUnit` (kg/lb) + `LengthUnit` (cm/in) exist;
  KDoc already promises "rendering is always settings-driven".
- `profiles.unitPreference` (`UnitSystem` METRIC/IMPERIAL) is per-profile in
  Room, carried in the F13 backup schema; `ProfileRepository.setUnitPreference`
  exists — with **zero UI callers** (one jvm test only). Writing a profile
  mirrors it into `SettingsStore.massUnit` (DataStore `unit_system`),
  which **nothing reads** — a write-only mirror today.
- Onboarding (F01) never asks and renders ~9 sites via `MassUnit.DEFAULT` (kg).
- F06 weight surface hardcodes `MassUnit.KILOGRAM`
  (`WeighInViewModel.reload`), the sheet placeholder "kg", and parses the
  typed number unit-blind (an lb user typing "170" would store 170 kg).
- F10 Hub is the only honoring consumer (`profile.unitPreference` → formats).

## Work
1. **Preference surface:** onboarding step asks kg|lb exactly once (default
   from locale is fine as preselection; the choice is one tap, skippable
   back-tap keeps default); Settings card shows the current unit and allows
   changing it — the only switch in the app. Changing it re-renders every
   surface; no data migration (storage is canonical kg).
2. **Global vs per-profile:** move the preference out of the per-profile row
   into the global settings scope (v1 is single-profile per R-B9, so this is
   a storage-location fix, not behavior). Keep `profiles.unitPreference` as
   the backup-compat field or migrate the schema — decide during
   implementation; F13 round-trip must keep working either way.
3. **Honor it everywhere weight renders or is typed:** F01 onboarding
   (replace `MassUnit.DEFAULT` sites), F06 weight surface + weigh-in sheet
   (parse in active unit → `toKilograms`; placeholder shows the active
   symbol), F07/F05/F11 weight-bearing copy (audit). Body-fat %, cm tapes,
   ratios stay metric-only per spec (tapes are cm; no lb-tape concept).
4. **Delete the switch from nowhere-else:** ensure no surface grows a unit
   picker; the Settings card is the single choke point (ruling above).
5. **Docs:** F06 §3 says "kg/lb/st, profile-sticky" and §4 "unit
   (profile-level, sticky)" — amend to "kg|lb, global preference, ask-once,
   Settings-only switch" so code and spec don't diverge (governing-doc rule:
   amend, don't contradict). Note st (stone) is ruled out.

## Tests
- Onboarding: unit step asked once; choice lands in settings scope.
- Settings: change re-renders Hub + F06 immediately.
- F06 sheet: typing in lb stores correct canonical kg (round-trip vs
  `MassUnit`); kg path unchanged.
- Vault: backup/restore keeps the unit preference.

## Acceptance criteria
- A brand-new user picks kg or lb once at onboarding and never sees the
  question again; only Settings can change it.
- With lb active, every weight number in the app (onboarding recap, Hub hero,
  F06 hero/trend/logbook/sheet, charts) renders lb and typed input is
  interpreted as lb.
- No surface except Settings offers a unit choice.
- F13 backup/restore round-trips the preference.


## Precision rules (canonical kg vs lb display) — owner question, 2026-09-14

Round-trip concern is settled by magnitudes: lb is defined as exactly
0.45359237 kg; double carries ~15–16 significant digits vs 0.1 lb ≈ 45 g
scale resolution → conversion error ~1e-13 kg, invisible. lb input is FINER
than kg, so canonical kg preserves it (canonical lb would quantize kg tenths
— kg is the right canonical direction; matches openScale/Happy Scale).

Implementer rules (the actual drift risks are software, not math):
1. Never round-trip a value through a formatted display string. Store full
   doubles; convert only at the input/display edge; seed edit fields via
   stored-kg → active-unit → format at display resolution.
2. Fix `formatWeightInput` (WeighInViewModel) to ROUND half-up at tenths,
   not truncate (`(kg*10).toLong()` drops 77.09 → 77.0).
3. The ±0.1 stepper steps in the ACTIVE unit (0.1 lb = 45 g), then converts;
   `STEP_KG = 0.1` must not be applied against stored kg in lb mode
   (0.1 kg = 0.22 lb jumps — visibly broken).
4. All delta/trend math stays in canonical kg; conversion is label-only.
5. Nothing writes back converted values — events append verbatim (R-B8);
   scalars/trend are derived views, so error cannot accumulate.


## Weight-first reorganization addendum (WLO-0067)

- The Hub does **not** currently honor lb correctly: it changes the suffix while leaving canonical kg numerals/deltas/chart values unconverted. Fix and test this explicitly.
- Choose one authoritative unit-preference API. Keep profile/DataStore compatibility mirroring internal; do not force a schema migration solely for single-profile v1.
- Resolve length-unit behavior against the governing settings-driven mass/length ruling before implementation; do not leave imperial users with an accidental lb-plus-cm contradiction.
- WLO-0051 is blocked by this ticket because input parsing/stepping must know the active unit.


## Implementation note — onboarding + Settings slice (2026-09-16)

- Added a resumable `UNIT` onboarding step with kg/lb selection; the choice is persisted in the draft and written to the global `SettingsStore`.
- F01 now renders weights, deltas, forecast bands, milestone ladder, and rates in the active mass unit while retaining canonical kg domain values. Imperial selection also renders/steps height in inches to keep the unit system coherent.
- Onboarding completion writes the backup-compatible profile `UnitSystem` mirror.
- Added the sole post-onboarding unit switch to Settings. DataStore remains authoritative and the active profile row is updated as a compatibility mirror; measurements are not migrated.
- Updated onboarding resume/robot tests and added kg/lb draft plus end-to-end UI coverage.
- Evidence: `:feature:f01-onboarding:testDebugUnitTest`, `:app:testDebugUnitTest`, onboarding/app `ktlintCheck`, `:app:compileDebugAndroidTestKotlin`, and `git diff --check` pass. The focused connected test was compiled but its run was deferred because the shared emulator was occupied by the WLO-0066 full suite.

Ticket intentionally remains **DOING** for root integration of the other WLO-0052 surfaces.

## Integrated implementation evidence (2026-09-16)

- SettingsStore is the render/input authority across F01 onboarding, Goals, F06 Weight, Logbook, and F10 Hub; the profile row remains only a backup-compatible mirror.
- Hub now converts numerals, deltas, provenance values, and chart labels instead of changing only the suffix.
- F06 input and edit paths convert the active display unit to canonical kg exactly once; display prefill and the ±0.1 step operate in the active unit; tenths formatting rounds rather than truncates.
- Goals input/display and imperial height follow the same settings boundary. F13 already exports/imports `unit_system` through its known-settings section.
- Added an emulator assertion that 170.0 lb is rendered as pounds and stored as 77.1107029 kg, plus onboarding/Settings kg↔lb coverage.
- Evidence passed: core/common, F01, F06, and app unit tests; Android-test compilation; `git diff --check`; API 29 focused connected tests for onboarding selection/Settings and canonical lb input (2/2).
