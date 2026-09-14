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
