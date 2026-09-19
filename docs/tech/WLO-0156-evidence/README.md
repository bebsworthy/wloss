# WLO-0156 — daily meal agenda implementation and verification

## Delivered behavior

Plan opens a persistent day with Breakfast, Lunch, Dinner and Snacks, even
without a generated plan. Calendar/week navigation, multi-item meals, collapsed
headers with nutrition, immediate add-many acquisition, same-food portion
replacement and swipe-revealed actions use the production repositories.
Today/past acquisitions can be Eaten or Planned; future dates are Planned.
Meal headings have only +. Mark eaten, quantity actions, move/repeat, pantry,
shopping, recipe import and AI recipe drafting are absent from this surface.

Coverage includes actual entries plus unresolved planned items, deduplicated by
explicit links. It is separate from actual intake used by the energy engine.
Each nutrient preserves unknown versus measured zero. Above-target bars fill
with green target/total and orange excess/total, without extra percentage copy.
Meal icon/name/+ center against the complete right-aligned nutrition block.
Only the selected date has a filled background; neutral rings express coverage.
Tap the day nutrition summary for saved-source and effective-target explanation.

Suggestions use local recipes and the deterministic engine, for selected empty
meals only. Their sheet has calendar range, meal types and weekdays filtering.
Generation is cancellable and bounded to 31 days. Draft preview survives
recreation; commit checks target version and meal-content fingerprints, writes
atomically and is idempotent. Existing meals and pantry/list data are untouched.

## Storage and compatibility decisions

The final implementation evolves `plan_slots` in place rather than introducing
the spec's recommended parallel `plan_items` migration. Each row is now one
item: `itemJson` freezes food/recipe/custom source, quantity basis, display unit
and nullable nutrition; `sortOrder` preserves sibling ordering on replacement.
Manual rows use an empty generation ID. Empty meals remain virtual containers.
The single store keeps existing F02/F10/F04 adapters and legacy history linked;
retired generations, replacements and unfillable placeholders are filtered.

Room is schema **11**, with non-destructive 9→10→11 migrations. Schema 10 adds
item snapshots and nullable diary/revision calories; 11 adds stable item order.
Version 10 is retained because a development build was installed during this
work; it also has a tested forward migration. No destructive fallback is used.

Diary additions/revisions remain F02-owned. Plan-to-diary logging is transactional
and uses a stable operation ID. Linked plans do not reappear after diary removal.
Legacy confirmed claims without diary links remain plans, are identified in the
explanation, and can be replaced/removed/undone without inventing actual intake.
Unknown calories hold the actual-intake projection rather than publishing zero.
Backup schema **2** carries nullable nutrition, snapshots and order, while older
backups are migrated and existing recipe/plan/diary history is preserved.

## Automated verification

`verification/gradle.log` records a successful build and **393 tests**, all with
zero failures/errors/skips (`verification/test-counts.json`): app 24; data 94;
database 15; vault 88; engines 145; food 11; planning 3; hub 13.

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest \
  :core:data:jvmTest :core:database:jvmTest :core:vault:jvmTest :core:engines:jvmTest \
  :feature:f02-food:testDebugUnitTest :feature:f03-planning:testDebugUnitTest \
  :feature:f10-daily-hub:testDebugUnitTest :feature:f03-planning:detekt \
  :core:data:detekt :core:designsystem:detekt checkArchitecture --console=plain
```

Ten new agenda repository tests cover independent seven-item persistence,
idempotency, replacement order/conflicts, removal/undo, legacy claim handling,
profile-isolated deep-link lookup, unknown intake, explicit-link deduplication,
empty-date targets, non-writing previews, commit conflicts/idempotency and overage
ratios. Existing migration, diary/planner, engine and vault regressions also ran;
an encrypted backup/restore test verifies unknown nutrition and item metadata.
Architecture checks D1–D7 and D9 and UI-atom checks pass.

Android `MealAgendaUiTest` runs production UI and repositories on
`emulator-5554`: date picker → 22 September → 2,410 kcal; acquisition sheet;
meal collapse; real swipe reveal (does not delete); replace 1→2; activity
recreation preserves the replacement and quantity; save yields 2,500 kcal;
replace back yields 2,410. It uses a separate fixture profile and does not delete
pre-existing profiles. The test leaves the fixture active for visual review.
`verification/android.log` records the device result.

## Screenshot verification

Final comparison: [overlay review](overlay-verified/index.html),
[side by side](overlay-verified/side-by-side.png),
[tinted overlay](overlay-verified/overlay.png),
[alignment metadata](overlay-verified/report.json).

```sh
python3 tools/screenshot-compare/agent.py \
  --mockup docs/design/flows/11-flexible-meal-planning.html \
  --selector .device --serial emulator-5554 \
  --anchor-selector '.nutrient-kcal .nutrient-value strong' --anchor-text '2,410' \
  --prepare-script docs/tech/WLO-0156-evidence/prepare-overplanning.js \
  --out docs/tech/WLO-0156-evidence/overlay-verified --json
```

The agent captured both surfaces and aligned the first visible numeral of 2,410,
not text-box leading. Both show 22 September, 2,410/1,900 kcal, P97/120,
C323/220, F70/60, fiber44/25 and the seven-item 1,300-kcal lunch. Green/orange
proportions are 78.84/21.16% for calories; carbs and fat use their own totals.
The review caught and corrected the overage color, filled unselected dates,
meal row backgrounds and meal-header spacing. Header alignment and right edges
were inspected in the resulting PNGs.

This is **not a pixel-identical claim**: Android uses the existing Inter/M3 type
and component metrics; the HTML uses different weights and spacing. Native
sections are denser. The mock capture is 390×845, the emulator 1080×2400;
width normalization is uniform (0.36111), and system/status/navigation chrome
differs. The sample recipe names are frozen custom snapshots in the device
fixture; production recipe-backed rows additionally offer recipe details.

[150% text screenshot](large-text.png) and its UI hierarchy (`large-text.xml`)
verify stacked calorie layout, wrapped remainder text, centered meal controls,
and enlarged date rings. The calendar remains usable. Font scale was restored
to its prior value, 1.0. Below-fold meals remain reachable by scrolling.

## Limits and follow-up context

This evidence is one emulator size, dark theme, normal and 150% text. It is not
an exhaustive device, locale, keyboard or TalkBack certification. Accessibility
labels, expanded states, custom row actions and standard 48dp icon targets are
implemented; actual TalkBack audio was not exercised. No release-device
performance claim is made.

Current F01 produces dietary-rule objects during onboarding but does not persist
a published DietPlan/PreferenceProfile for the planner to read. Suggestions use
the existing local library and saved generation settings; they do **not** claim
personal allergy filtering. Persisting and consuming those rules remains an
upstream integration gap tracked as **WLO-0164**, not guessed preferences or a new setup screen.
