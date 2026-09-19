# Daily meal agenda and multi-item planning

## Outcome and authority

Implement the accepted Flow 11: time is the permanent surface; planning is an optional action on that surface. Opening Plan always shows a usable day, including when nothing has ever been planned. A meal is a collection of independent foods, drinks and recipe portions, never a single recipe slot.

This is the implementation successor to completed WLO-0027, not a reopening of its original acceptance criteria. Source of accepted interaction/visual behavior: `docs/design/flows/11-flexible-meal-planning.html`, `11-meal-planning.js`, `11-meal-planning.css`, through WLO-0155. Research: `docs/research/meal-planning-ux-review.md`. Earlier experiments in WLO-0127–0155 and chronological flow notes are superseded wherever they conflict with this specification. The HTML is an interaction reference, not a persistence or nutrition-engine implementation. Proposed technical decisions below are implementation recommendations derived from code inspection, not additional owner-requested features.

Keep F02 diary ownership, F01/F07 target ownership, provenance, local-first operation and standard Material 3 components. No second calorie deficit, automatic target adjustment, server, or new AI consent workflow.

## Scope boundaries

Included: permanent daily agenda; date navigation; calorie/macro coverage; multiple items per meal; add/replace/remove; optional recipe details; scoped deterministic suggestions with preview; persistence, migration, projections, accessibility and backup compatibility.

Excluded: pantry management or pantry-dependent suggestions; shopping-list work; recipe import/scraping and recipe drafting with AI (WLO-0151); travel/open-day modes; required planning setup; move/repeat; remove-meal action; meal-level menus; per-meal Suggest/Suggest additions; inline quantity editor; dedicated Edit quantity action; Mark eaten at meal or item level; duplicate Log meal review screen. Do not delete existing pantry, shopping, recipe or diary data. Existing independent features remain outside this redesign.

Parking Mark eaten does not remove direct food logging: the mockup's Add sheet explicitly chooses Eaten or Planned on today/past dates. Eaten additions use F02. Do not invent an implicit conversion of existing plans to actual intake.

## Screen and interaction contract

### Permanent day surface

- Open Plan on today unless a date is supplied by navigation. Always show Breakfast, Lunch, Dinner and Snacks, including empty meals. Empty means no entries; no reason, travel declaration, completion requirement, guilt message or setup gate.
- Read a date range independently of an active generated plan. Preserve selected date, scroll and expansion state across sheets and back navigation; restore selected date across recreation. Refresh today when local date/timezone changes, without unexpectedly moving the user's selection.
- Compact top bar: Plan, previous week, date-range/calendar trigger, next week, Suggest meals icon. Seven-day strip below; arrows and horizontal week navigation preserve the chosen weekday. At normal text size use the prototype's approximate 56 px toolbar plus 52 px strip as reference, not extra vertical whitespace. Native touch targets at least 48 dp; support large text without clipping.
- Date trigger opens a proper calendar date picker with month navigation, selected date, Today and Cancel. Date selection returns to that date in the agenda; cancel changes nothing. Use standard M3 DatePicker in DatePickerDialog, calendar mode, with documented components/slots. No text-input-only date panel. No duplicate Today/date heading below navigation.
- Each date's ring represents calorie coverage for that date, with neutral stroke, centered numeral and separate selected-day background. No entries or no valid target: no progress arc. Unknown kcal among entries: incomplete/dashed treatment, no precise percentage. Complete total over target: cap arc at one revolution and expose amount over in accessibility description. No extra overflow symbol or percentage caption.

### Day nutrition

Show calories prominently as total / target and calculated remaining or over-target amount. Below: Protein, Carbs, Fat, each with total / target, remaining/over and a bar; quieter Fiber total/target/remainder line. Do not add “Includes planned food · estimates”, redundant percentage subtext, or repeated explanation captions.

Coverage counts actual diary entries plus still-planned items, deduplicated by explicit links. It is not an actual-intake measure. Keep energy-engine intake separate.

For each nutrient independently, let T be a positive target and V the complete covered total:

- V <= T: green width V/T; remainder is neutral track. Text T−V remaining (at equality, target reached).
- V > T: whole bar represents V; green width T/V, orange width (V−T)/V, target boundary between them. Text V−T over target. Example 2,410 / 1,900 kcal: 510 over, green 78.84%, orange 21.16%. Do not label orange 26.84% of the bar: that is overage relative to the target, a different denominator.
- Invalid/absent target: known total only, no invented target, remaining value or ratio.
- Missing nutrient on any counted item: show known subtotal as incomplete; no exact remaining or precise progress for that nutrient. Zero is valid known data, not missing. Empty day has zero covered/planned items, not a claim that the person ate nothing.
- Use full precision for accumulation and ratios; round presentation consistently so displayed totals/remainders reconcile. Accessible progress descriptions include total, target and overage/completeness. Do not rely on color alone: totals and over-target text remain visible.

Targets come through the existing versioned day-target projection. Resolve schedule and effective versions centrally: use the effective target for past days and latest applicable published target for present/future days; expose the version in provenance. Audit existing effective-date behavior before changing shared consumers. No hardcoded prototype targets, no deriving missing macros from calorie percentages.

### Meal headers and items

Meal header: outline meal icon and name at left; calories at right above right-aligned P/C/F quantities; + at far right. Icon, name and + vertically center against the entire two-line nutrition block. Totals remain visible when collapsed. Header toggles expansion; expose expanded state to accessibility, but no visual up/down chevron. + adds without also toggling. Empty meals retain header and +; omit nonexistent item totals. No Planned label, estimated-total body line, or action row.

List each item's name then quantity/unit and kcal/status in compact standard M3 ListItem slots. Support seven or more items and drinks in any meal. Recipe portion, starter, yogurt, banana, bread, wine and milk are independent items with their own quantities and nutrition.

Swipe left reveals Replace and Remove icons; recipes additionally reveal Recipe details. Reuse `WloSwipeRevealRow`: reveal only, never delete on swipe; release-gated and velocity-blind; one open row; swipe right closes. Match existing component thresholds, checking against the prototype's 40 px reference in native density units. Provide TalkBack custom actions and keyboard-accessible equivalent actions without a persistent overflow menu. Hidden buttons must not intercept touch or focus. Remove one item, offer Undo. Preserve siblings and order.

Replace opens the same acquisition sheet in replacement mode, prefilled with current source and portion. Allow choosing the same food with a different quantity/unit as well as another food. A single explicit replacement commit updates only that item; cancellation leaves it unchanged. No intermediate save on selecting a candidate, no delete-before-insert, no separate quantity editor. Preserve planned/eaten ownership, date and meal. Existing logged items are corrected through F02 revisions. Recipe details is optional and returns to the same agenda state.

### Add sheet

+ opens one ModalBottomSheet scoped visibly to selected date/meal. Search existing local food and recipe catalogs. Seed recipes must be available without first generating a week. A result's + adds its displayed default portion immediately; sheet and query stay open for repeated additions. Done/Back dismiss, without a second save or discarding successful additions. Show pending/error state on the affected operation and prevent duplicate writes from a double tap/retry; two deliberate adds remain possible.

Today/past default to Eaten with explicit Eaten/Planned choice for subsequent additions; future dates permit Planned only. Eaten creates an F02 diary entry, Planned an F03 item. No synthetic planned record is needed for a direct Eaten addition. Optional portion adjustment happens inside acquisition/replacement, not inline on the agenda.

Provide the prototype's collapsed custom-food entry within this sheet: name, positive quantity, unit and optional calories per unit; unknown macros remain unknown. Clearly distinguish per-unit and total nutrition. Do not infer nutrition for arbitrary text. Unknown-kcal direct logging requires the data-model change below; silently saving zero is prohibited. Dismissing custom entry does not undo earlier adds. Live remote search, photo/voice acquisition, recipe import and AI drafting are not dependencies for this ticket.

### Suggest meals

Toolbar icon opens its own bottom sheet: From/Through dates, meal-type selection and optional weekdays-only filter. Remember selections during the session. Validate range and at least one meal type; date selection uses calendar controls. Bound generation work, make it cancellable and report empty results honestly.

Preview is read-only: generate for selected empty meal containers, close the sheet and display proposals on the same agenda with draft identification and Add suggestions/Discard. Keep the selected date and date navigation; count draft proposals once in preview nutrition. Existing populated meals, including actual diary entries, are never overwritten or topped up. Discard removes drafts only. Add commits the complete draft atomically; success removes draft styling. Prevent another generation while a draft is pending. Persist an uncommitted draft across recreation via saved state/local draft storage; it is never counted as committed data or intake. Back from a sheet cancels the sheet; leaving preview must retain it or explicitly discard, not silently save.

Use existing deterministic engine and local recipes, adapting output into lists of items. Respect existing dietary exclusions, recipe availability and actual date targets; do not use pantry stock or trigger shopping reconciliation. Account for already-covered day nutrition when suggesting empty meals, without forcing every day to match a target. No valid targets: manual planning stays available, target-fit suggestion reports that prerequisite inline. No suitable content: leave meals empty with an honest result, no fabricated food or nutrition. Preview is advisory, never an automatic re-deal when targets change.

Commit checks profile, target version and per-meal content revisions captured at preview. On conflict preserve user edits and draft, explain that preview needs refreshing; no partial silent commit. Retrying the same commit must not duplicate items.

## Code audit and required architecture changes

| Current implementation | Required change |
| --- | --- |
| `feature/f03-planning/.../state/PlanUiState.kt`: PlannedSlotUi is recipe-based; hasPlan requires planId; days come from slots; kcal/protein and fit badges | Day agenda state independent of a plan version; four virtual meal containers; item models, per-nutrient completeness, date range and explicit sheet/draft state |
| `PlanViewModel.kt` observes currentPlan, opens slot/swap sheets; breakfast/lunch/dinner default | Observe plan items + diary + targets for selected range, including Snacks and empty dates; bounded/cancellable queries; stable selection; item commands |
| `ui/PlanScreen.kt`, `PlanSheets.kt`: Plan/Recipes/List/Pantry segments, empty-plan generation gate, week slot cards and confirm/skip/swap | Persistent agenda, compact calendar, add/replace and suggestions sheets; remove obsolete planner entry points while preserving independent feature routes/data |
| `core/model/.../Planning.kt`: PlannedSlot requires planId, recipe-specific snapshot; null recipe means unfillable | Explicit item kind, food/custom support, independent dates, portions, ordering, completeness and links; never overload null recipe to mean both unknown food and empty slot |
| `core/data/.../PlannerRepository.kt`: generateWeek supersedes active plan; logAsPlanned recipe-only | Separate preview/commit APIs and granular item writes. Existing range observer is a useful starting point; no currentPlan gate or generateWeek call for agenda edits |
| `core/engines/.../PlannerEngine.kt`: plannedDayTotals includes CONFIRMED and substitutes 0 for missing fields | New deduplicated coverage projection; preserve legacy plan-claim/adherence meaning rather than summing it with intake |
| `core/data/.../RoomRepositories.kt`: planned scalars read plan slots; current target document projection | Shared range nutrition/target projection with completeness and explicit source references; keep actual intake scalars isolated |
| `core/model/.../Diary.kt`: kcal required in entry/revision; nullable macros; FoodItem has nullable nutrition | Support unknown kcal end-to-end for custom entries, including revision, write validation, Room projection and backup; no null-to-zero fallback |
| Room entities/DAOs/migrations and `core/vault/.../BackupSchema.kt` | Durable model migration and versioned portable backup/restore for new fields/records and links |

### Recommended durable model

Introduce item-granular `PlanItem` and `plan_items` rather than making every arbitrary food masquerade as a recipe slot. A meal container is a projection key `(profileId, localDate, mealSlot)`; no persisted empty meal/day is required. Multiple ordered items share that key. Suggested fields:

- Stable item ID, profile ID, dayEpochDay, canonical MealSlot, sortOrder, created/updated timestamps, revision and soft-archive marker.
- Explicit source kind RECIPE / FOOD / CUSTOM; nullable recipe ID+version or food ID+version, frozen display name and provenance. Enforce source-kind invariants. Source deletion must not erase historical nutrition/name.
- Display quantity and unit; normalized base amount/unit where conversion is known; snapshot basis amount and nullable kcal/P/C/F/fiber, plus source/provenance references. Compute extended nutrition from quantity and basis exactly once. Recipe household batch servings remain distinct from personal consumed portion.
- Optional generation batch/version ID and legacy slot ID for provenance; neither is required for manual planning. Retain legacy cook/batch/parent references where present without introducing batch UX.
- Explicit resolution/link to F02 diary entry, with stable idempotency/revision checks. Keep legacy state transitions in an adapter/history layer; there is no meal-wide eaten boolean.

A food's preset grams and recipe servings must use existing conversion rules. Do not equate grams and ml without density or invent a weight for “glass”/“slice”. Custom units can have per-unit nutrition without a guessed mass. Reject negative/nonfinite amounts; missing nutrition and measured zero are different.

Diary entries remain the actual-food record. Extend nullable kcal support to NewDiaryEntry, PlanNutrition where needed, DiaryEntry/DiaryRevision, DB fields, projections, validation, exports and consumers. For an incomplete day, F07 must hold any inference requiring complete intake rather than interpreting known subtotal as full intake. Preserve existing day-status semantics. This is a necessary cross-feature prerequisite, not just a UI nullable field.

### Coverage and lifecycle invariants

Build `DayMealAgenda`/`MealAgenda` as a read projection, not another stored diary. Each nutrient returns known total, unknown-item count and provenance; carry actual versus planned contributions separately. Stable row keys distinguish plan and diary IDs.

An unresolved active planned item contributes its snapshot. A linked resolved item displays/counts the owning active diary record once; it does not also contribute planned nutrition. Unlinked actual records also count once. Retired replacements, archived items and old superseded generations do not contribute. Never match by food name/time/calories. Legacy confirmed items without a valid actual link must remain identifiable as legacy plan claims, not invented intake; expose incomplete linkage in diagnostics/provenance and preserve their data.

Replacement and removal are transactional and revision-aware. Undo restores the exact item/link/position only when compatible with subsequent edits. Removing a logged row archives through F02; do not automatically resurrect a resolved plan. Corrections to a linked diary item update the agenda immediately while keeping original plan intent for historical comparisons. Existing F02/F10 plan-log entry points must still work through an adapter and must not duplicate writes. The current logAsPlanned sequence writes diary then retires slot: audit crash/race behavior and ensure atomic linking or durable idempotent recovery.

Keep separate definitions: coverage for this screen, original planned claim for adherence, and actual intake for F07. Update counts formerly defined as number of slots to distinguish meal-container count from item count. Empty optional meals must not become failures or automatic skipped states in adherence.

### Migration, integration and portability

- Add a forward Room migration from the current checked-in schema (9 at inspection; recheck when implementing). Backfill each effective recipe slot into one PlanItem, preserving IDs or an explicit mapping, dates, quantities, frozen snapshots, state, diary links, swap chains and batch references. Keep old version/history records for audit and compatibility. Null-recipe unfillable placeholders become no visible food, not custom food and not zero-nutrition rows. Migration is idempotent and non-destructive; no fallback-to-destructive-migration.
- Do not backfill every historical generation as simultaneously active. Respect superseded plans and retired swap predecessors; test overlapping date ranges.
- Existing `snack` wire value maps to Snacks. Legacy standalone `drink` diary records must remain visible: project them under Snacks with their original meal-slot metadata retained; new drinks use whichever meal the user selected. This is a compatibility presentation choice, not a stored-data reassignment.
- New reads/writes use PlanItem as authority. Update legacy F02/F10 and F04 adapters that currently read plan_slots so there are not two divergent writable sources. F04 compatibility can project recipe-backed items and ignore unsupported custom ingredients honestly; no automatic list generation, pantry mutation or shopping UI added by this feature.
- Extend backup rows/section versions, restore validation, manifests and export schema; old backups migrate through the same mapping, new backups round-trip unknowns, order, links, revisions and history. Retain legacy sections until migration compatibility permits retirement. Reject dangling cross-profile links.
- Route existing Plan entry/deep links to selected day/item in agenda; initial F01 planner CTA opens usable agenda without requiring generation. Removed slot sheets must not strand deep links. Back from details/sheets returns to the same selected day. Keep feature boundaries: F03 composes, F02 writes actual entries, F01/F07 own targets, F13 serializes.
- Use standard M3 Scaffold/TopAppBar, ListItem, IconButton, ModalBottomSheet, search fields, date picker and Snackbar through documented slots. Custom ring/segmented-overage visualization is drawing within standard layout; reuse justified swipe component, do not hand-roll another interaction container.

## Implementation sequence

1. Introduce domain invariants, nullable-nutrition support and coverage tests; settle schema/compatibility adapters in code before UI work.
2. Migrate persistence and backups; implement atomic item operations and range projection. Verify legacy diary/plan integration and actual-intake isolation.
3. Build agenda/navigation/header/summary and add/replace/remove flows against repositories; remove obsolete planner routes/actions.
4. Adapt deterministic generation to non-writing preview plus atomic scoped commit; implement draft restoration/conflict handling.
5. Run focused unit/integration/migration/device checks below; update F03, IA, data-spine and design documentation to final implementation. Do not mark this ticket done on specification or mockup completion.

## Acceptance and verification

- Fresh profile with no generated plan opens today and four empty meal headings; all dates remain accessible, no required setup or travel state. Reopening, rotation and calendar cancellation preserve appropriate state.
- Add seven distinct lunch items including drinks, navigate away/back and restart: preserve quantities/order and exact summed nutrition. Removing/replacing one leaves six siblings unchanged; replace same food with a different portion works. Undo restores the correct row. No visible quantity editor, overflow menu, Mark eaten or removed actions.
- Meal icon/name/+ align to the full nutrition-block center; calories/macros share right edge; collapse retains totals and has no visual chevron. Verify at normal and enlarged font sizes using emulator screenshots.
- Add sheet supports multiple immediate saves; Back does not lose them; retry/double-tap does not duplicate one operation. Eaten and Planned writes go to correct stores; future has no Eaten choice. Recipe lookup works before any generation.
- Partial nutrients, genuine zero, absent target, over-target, empty days, archived foods and invalid units are tested. Unknown kcal survives save, projection, restart and backup. F07 does not receive incomplete coverage as intake.
- Reproduce fixtures using 21 Sept ordinary coverage and 22 Sept overplanning: 22nd 2,410/1,900 kcal, P97/120, C323/220, F70/60, fiber44/25. Calorie bar green78.84%/orange21.16%; carbs/fat use their own denominators. No duplicate overage percentage text. These are test values, not production defaults.
- Linked planned+actual food counts once; diary correction refreshes totals; old CONFIRMED/SWAPPED/SKIPPED/REPLACED cases, missing links and overlapping plan versions tested. Removing actual food does not silently reinstate plan intake. Actual-only energy projections remain unchanged for complete legacy data.
- Suggestions touch selected empty containers only, including weekday/range boundaries. Preview/discard writes no committed items; draft counts once; commit is atomic/idempotent; target/content change is detected. Cancel/error preserves existing food. No pantry/list writes and no network/AI invocation.
- Migration fixtures cover legacy recipes, missing snapshots, leftovers, swapped chains, confirmed links and unfillable cards; current and old backup round-trips preserve data and cross-profile isolation.
- Test swipe with release before/after threshold, rapid motion, vertical scroll, open-one-row, TalkBack and keyboard actions. No action executes just by revealing. Test day/week navigation near month/year boundaries and timezone/date rollover.
- Extend existing `PlanStateTest`, `PlanningRepositoriesTest`, `LogAsPlannedTest`, planner engine tests, Room `MigrationTest`, vault round-trip tests and relevant F02/F10 projection tests; replace obsolete M5 planner UI assertions while retaining independent shopping regression coverage. Run affected module suites plus Android end-to-end agenda paths. Record actual commands/results and limitations in the ticket at implementation completion.

Specification prepared from repository inspection; application implementation and acceptance checks remain outstanding.


## Implementation and verification — 19 September 2026

Implemented in the Android Plan tab. Final architecture decision: evolve the existing `plan_slots` table into item-granular storage instead of introducing the recommended parallel `plan_items` table. `itemJson` freezes food/recipe/custom source, display unit and nullable nutrients; `sortOrder` preserves sibling position. Manual items are independent of generation (`planId=""`). Legacy history and existing F02/F10/F04 adapters retain one source of truth. This supersedes the recommended new-table/backfill portion above.

Room schema 11 has forward 9→10→11 migrations, including nullable diary calories/revisions and stable item order. Backup schema 2 preserves those fields and migrates old backups. Actual entries remain F02-owned, legacy log-as-planned is transactional/idempotent, and unknown intake holds F07 inference. Legacy confirmed rows without diary links stay identifiable as claims; remove/undo and replacement preserve that distinction.

The agenda, compact navigation/calendar, day coverage/overage, centered meal headings, swipe actions, add-many/replace acquisition, recipe details and optional suggestion preview/atomic commit are implemented. Date/draft/replacement state survives recreation. Suggestions can be cancelled; scope is bounded to 31 days. Day targets resolve their effective version. Nutrition explanation is available by tapping the summary without adding permanent captions.

Verification: 393 affected JVM/unit tests pass (including 10 new agenda repository tests); Room migration and encrypted backup tests pass. Production Android UI test passes through date selection, overplanning, acquisition, collapse, real swipe, same-food quantity replacement, activity recreation and correction back to the 2,410 kcal fixture. Targeted Detekt and architecture checks pass. Captured/inspected mockup/app overlays using the required screenshot-compare agent and the first 2,410 glyph; verified 150% font size, then restored 1.0.

Evidence, commands, test counts and specific visual differences: `docs/tech/WLO-0156-evidence/README.md`. Final normal-size overlay: `docs/tech/WLO-0156-evidence/overlay-verified/index.html`. Existing native M3/Inter metrics produce denser spacing than HTML; this is not a pixel-identical claim. Dark theme and one emulator size were checked; exhaustive device/locale/TalkBack testing was not performed.

Upstream integration gap tracked separately in WLO-0164: F01 computes dietary rules but does not persist a published preference document. No existing persisted exclusion state was found to consume; suggestions must not be advertised as personalized allergy filtering. This work does not invent user preferences. WLO-0164 covers their durable publication, planner constraint integration, conflict detection and portability.
