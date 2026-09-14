## Context

UX review of the weigh-in sheet (F06's typed path, R-U15 first-class; F06 §1
bars: ≤5 s capture, zero typing in the common case, trend-first framing,
"remove the verdict feeling"). Implementation:
`feature/f06-weight/.../ui/WeightScreen.kt` (`WeighInSheetContent`) +
`state/WeighInViewModel.kt` (`save()`, `freshSheet()`, `SheetWhenParser`).
Design intent: `docs/design/flows/04-weigh-in-trend-f06-f08.html`.
Owner ruling from review: items 5 (time-default chips), 7 (stepper
long-press/coarse step), 8 (custom numpad) are **out of scope**; the live
parse-preview idea is dropped in favor of native pickers; the sheet subtitle
is removed outright.

## Scope

### A. Silent-failure fixes (correctness first)
1. **Save must never no-op silently.** `save()` returns on
   `toDoubleOrNull() == null` — empty/malformed weight + Save = nothing
   happens, no feedback. Disable Save until the field parses, and/or show an
   inline error in the sheet.
2. **Locale decimal-comma bug.** `"77,0"` (comma-locale keyboards) fails
   `toDoubleOrNull` → every save silently fails. Normalize `,`→`.`, trim,
   before parsing.
3. **Validation errors must render inside the sheet.** Bad date/time sets
   `notice`, which renders on the WeightScreen *behind* the still-open sheet
   — invisible to the user. Move sheet-relevant validation next to the
   offending field (supportingText/error state).

### B. Zero-typing speed (F06 §1)
4. **Prefill from last known weight / trend, not just today's log.**
   `lastWeightInput()` reads only *today's* events, so the day's first
   weigh-in opens empty and the ±0.1 stepper is dead until typing. Prefill
   yesterday's last reading or the current trend value (prototype: "prefilled
   with your trend").
6. **Autofocus the weight field on sheet open** (FocusRequester; keyboard up).

### C. Date/time entry
12. **Native date + time pickers** replacing free-text ISO/HH:MM fields
    (typo-bait today; errors only surface on save). Text override may remain
    for geeks if cheap, but picker is the path.
14. **Remove the sheet subtitle** ("Same conditions help the trend read
    true. Missed a day? … blank time reads as noon.") — owner call: cut it.
    With pickers the time is always explicit; the R-B5 noon rule stays a
    parser-level semantic (blank text still normalizes to noon for
    non-picker paths), just no longer explained in-sheet.

### D. Trend-first save moment (F06 §2/§4)
9. **Post-save confirmation + haptic.** Spec flow: confirmation with trend
   value + delta, raw small beneath, single soft tick. Today the sheet just
   closes; no haptic exists. Add: `WloHaptic.Tick` on save + a confirmation
   line/card on the surface ("Saved · trend 77.05, ↓ 0.1 / 7 d"). Gain days:
   identical effort, neutral color (tone rules §6).
10. **Live trend preview while typing:** caption under the field —
    "after save: trend ≈ 77.0 (−0.1)" — reassures before commit.
11. **Today-context line in the sheet:** when the day already has readings,
    one neutral line ("today's log: 77.4 · lowest stands") — data, not
    commentary (R-B8 no-duplicate-guilt).

### E. Robustness / reach
15. **Unit chip beside the numeral.** kg is hardcoded (`MassUnit.KILOGRAM`,
    "kg" placeholder that vanishes on input). Minimum: persistent unit chip
    beside the display per prototype. Full kg/lb/st profile-sticky entry is
    likely its own ticket — see question.
16. **Pre-save typo nudge:** typo-shaped values ("770" or "7.7" for a ~77 kg
    user) get an inline pre-save hint. Post-save ±3σ guard stays as is.
17. **Accessibility:** labels/supportingText for all fields (placeholders
    vanish + are invisible to TalkBack), state semantics on stepper buttons,
    error/live-region announcements.
18. **IME/inset QA:** `WloSheet` column has no imePadding/scroll — verify
    Save stays reachable with keyboard up on small screens; investigate stray
    edge artifact seen in screenshot.

## Out of scope (owner, 2026-09-14)
- Time-default chips / smart "this morning" default (review item 5)
- Stepper long-press repeat + coarse ±1 step (item 7)
- Custom in-sheet number pad (item 8)

## Tests
Update `M3WeighInTest` + `SheetWhenParserTest` for: comma parse, disabled
Save, picker-provided when, prefill source, post-save confirmation state.

## Acceptance criteria
- Empty/malformed weight + Save: visible inline feedback, never a silent no-op.
- `77,0` saves as 77.0 in comma locales.
- Date/time errors visible inside the open sheet and announced.
- Day's first weigh-in opens prefilled; stepper works without typing first.
- Weight field focused on open.
- Pickers set date/time; subtitle gone.
- Save produces soft tick + trend confirmation incl. delta; gain = neutral.
- Live "trend after save" preview under the field.
- Today-context line when day already has entries.
- Unit chip visible beside the weight display at all times.
- Typo-shaped values get a pre-save inline nudge.
- TalkBack labels everywhere; Save reachable with keyboard open.


## Addendum — owner ruling on units (2026-09-14, resolves q-000034)

Item 15 tightens to: the sheet renders a **persistent unit chip beside the
numeral** showing the active global unit, and parses the typed value **in the
active unit** (converting to canonical kg on save via `MassUnit.toKilograms`).
The sheet never offers a unit choice. The global unit system itself (kg|lb
preference: onboarding ask-once, Settings-only change path, F01/F06 consumers
honoring it) is WLO-0052. Items 5/7/8 remain out of scope.
