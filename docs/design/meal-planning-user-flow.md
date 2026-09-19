# Meals — edit directly on the daily agenda

> **Implementation handoff — 19 September 2026:**
> [WLO-0156: complete specification](../tasks/WLO-0156-Implement_The_Daily_Meal_Agenda_And_Multi_Item_Planning/spec.md)
> consolidates the accepted Flow 11 through WLO-0155, including data-model,
> migration, diary integration and acceptance requirements. The notes below are
> chronological design history, not a cumulative implementation checklist.
> Superseded experiments such as inline quantity editing, meal menus, Mark eaten,
> move/repeat and inline bulk suggestions must not be implemented.

WLO-0132 · owner-directed revision, 18 September 2026.
[Try the mockup](flows/11-flexible-meal-planning.html) ·
[Research review](../research/meal-planning-ux-review.md).

Time is the permanent surface. Open on today with the same header, week navigation
and day navigation, regardless of whether the user plans. Always render Breakfast,
Lunch, Dinner and Snacks. Any or all entries may stay empty without a reason,
travel mode, reminder, or setup step. Pantry remains outside this design.

This supersedes the earlier wizard, saved-week gate, flexible-period screen, and
WLO-0131 duplicate food-log review. The day already contains the meal's editable
contents; showing those contents again to log them adds no useful decision.

## Daily interactions

A meal contains any number of foods, drinks and recipe portions. No course labels
are required. The sample lunch includes a starter, dish, yogurt, banana, bread,
wine and milk. Every item has its own quantity, unit and nutrition basis.

- **Quantity:** edit directly in the item row. Blur/change commits the portion,
  updates totals and offers Undo. No item-detail screen is required.
- **Remove:** the item row has a labelled remove button, with Undo.
- **Replace / change planned-eaten state:** secondary item menu.
- **Recipe details:** optional access to ingredients, instructions and household
  servings. Adding a recipe does not require opening these details.
- **Add food or drink:** one bottom sheet over the agenda, described below.
- **Mark eaten:** on today/past meals containing planned items, records the shown
  items immediately, with Undo. There is no duplicate list or confirmation screen.
  Adjust the meal on the day before or afterward. Already eaten items are not
  duplicated. Individual items can also be marked eaten/planned from their menu.

Empty meals have Add and Suggest. Future dates do not offer Mark eaten. Completely
logged meals remain editable on the same day, with no separate logging journey.

## Add several foods in one sheet

The sheet identifies the selected day and meal. Search the sample foods, drinks
and recipes; tap **+** beside each result to add its displayed default portion.
Each tap saves immediately. The sheet stays open and preserves the search so the
user can add multiple items. A live status states how many items were added and
the meal's current total. **Done** or Escape dismisses the sheet; it does not save
again, and dismissal does not discard already-added food.

Today and past dates default to **Eaten**. The sheet has an explicit Eaten/Planned
choice that applies to subsequent additions. Opening Suggest defaults to Planned.
Future dates permit only Planned. Adding a later drink to a logged meal follows
exactly the same flow as adding the first item.

A collapsed **Enter another food or drink** form sits within the same sheet:
name, quantity, unit and optional calories per unit. It can add unknown foods
without inventing nutrition. Recipe import and AI recipe drafting are deferred from the current implementation
(owner scope decision WLO-0151). Their entry points and preview flows are removed
from the mockup. Existing recipes and meal suggestions remain available.
Photo/voice capture and live food search are outside this HTML study.

## Example paths

**Office lunch:** Lunch → Add food or drink → add yogurt, banana and milk → Done.
All three are already recorded as eaten. No review or second Save is required.

**Planned lunch:** adjust bread quantity on the day → remove the uneaten starter →
Mark eaten. Undo restores the prior planned/eaten states without moving dates.

**Tomorrow's dinner:** choose tomorrow → Add or Suggest → add a dish and side →
Done. Both remain planned. Recipe details are available only if needed.

## Suggest several meals

The optional action expands in the agenda. Choose dates and meal types, with an
optional weekday filter. Preview fills only selected empty entries with multiple
items. Keep the same date navigation during preview. Add suggestions commits;
Discard leaves existing entries intact. Users can edit individual items in place.
Bulk generation remains a draft because it introduces multiple proposals at once.
Move/repeat operates on all items together and targets an empty entry in this study.
Full cook-batch/leftover allocation is outside this prototype.

## Numbers and ownership

Sum item portions, distinguish known eaten and planned amounts, and label any
subtotal **total incomplete** when an item has unknown nutrition. An empty entry
is neither zero intake nor a failure to plan. Once an item is eaten, do not count
its planned contribution as additional intake. The existing calorie goal is a
quiet reference; this flow creates no second deficit or automatic compensation.
Household Make servings and personal portions remain distinct for recipes.

This prototype does not amend R-B1 diary ownership, R-B2 target ownership, AI
consent or frozen release sequencing. Production preserves plan/diary provenance;
the HTML uses in-memory fixtures and illustrative nutrition with no real writes.
Recipe import/AI drafting are deferred under amended R-S3; reconsider their
consent and release reconciliation only when that work is explicitly resumed.

## Handoff and verification

Compose maps the agenda to standard ListItem slots, labelled quantity text fields,
IconButtons, TextButtons and Snackbars. Add-many maps to ModalBottomSheet, search,
standard buttons and a segmented state choice. The HTML uses a native modal dialog
for keyboard focus containment and Escape dismissal, visible focus, semantic
labels, text state indicators and targets of at least 48 px. Longer meals and the
sheet scroll. Previous flow screenshots are historical.

Verify batch additions without navigation; Eaten/Planned defaults; future guard;
inline portions; one-tap marking and Undo; adding after marking; unknown nutrition;
and preserving all other meals. No Android implementation change is claimed.

## Meal / item hierarchy (WLO-0133)

Meal names are section headings (22 px, semibold in the HTML study), above a quiet
meal total. Item names are subordinate 14 px regular text. A 16 px inset and subtle
vertical grouping rule visually attach foods to their meal; item rows have no
competing full-width dividers. Only meal boundaries use full-width separators and
generous spacing. Meal actions share the content inset, including on empty meals.
The indentation and weight/size differences establish hierarchy without relying
on color or turning every food into a card. Direct editing behavior is unchanged.

## Owner correction (WLO-0134, supersedes inline-editor and WLO-0133 styling)

Remove visible quantity inputs, vertical grouping rules and content indentation.
The day uses compact two-line items: name, then portion/nutrition/state text.
Meal headings are restrained 18 px semibold; item names 14 px medium. Spacing and
meal-boundary separators distinguish groups without item dividers. Edit portion,
Recipe details and Remove live in the item's menu; portion editing opens a small
explicit dialog with Save/Cancel only when requested. The add-many sheet and
one-tap Mark eaten remain. The earlier inline-edit instructions are superseded.

## Meal-heading actions (WLO-0136)

Remove meal-level status labels. Each heading keeps its icon/name and a right-hand
overflow menu, available for both empty and populated meals. The menu contains
Add food or drink, Suggest/Suggest additions, eligible Mark eaten, Move/repeat,
and Remove meal. No persistent action row appears below the items. Item statuses
remain on their own rows. Selecting an action closes the menu. Production maps
this to an IconButton and standard DropdownMenu; the HTML uses a labelled disclosure.

## Single meal action (WLO-0137, supersedes WLO-0136)

Each meal heading now has a single + icon, labelled “Add food to [meal]” for
accessibility. It opens the existing add-many sheet directly. Remove the meal
overflow, per-meal Suggest/Suggest additions, Move/repeat and Remove meal. Park
Mark eaten pending a clearer interaction; remove it from meal and item actions.
Individual item editing/removal remains available. The separate multi-day preview
is unchanged by this meal-action simplification.

## Item swipe actions (WLO-0138)

Replace item overflow menus with swipe-to-reveal icon actions: Edit portion,
Recipe details (recipes only), Replace, Remove. Drag left at least 40 px then
release to reveal; drag right to close. Only one row opens at a time. No swipe
executes an action, and removal still offers Undo. Vertical gestures remain page
scrolling. The closed row shows only the existing two-line food content.

The prototype also allows tapping the row or Enter/Space to reveal, Left to open,
and Right/Escape to close. Hidden actions are inert; revealed icon buttons have
accessible names and titles, with 48 px targets. Reduced motion disables the
translation animation. Production should use the project's justified
WloSwipeRevealRow behavior composed with standard M3 item/action components.

WLO-0139: remove the redundant Edit portion swipe action. Items expose Replace
and Remove; recipes additionally expose Recipe details. This supersedes the
Edit portion action listed above; the reveal width follows the remaining icons.

## Day nutrition summary (WLO-0140)

Replace the calorie-only reference line with Calories total / target, explicit
remaining (or over target) amount and a horizontal progress bar. Protein, Carbs
and Fat follow in three equal columns, each with total/target, remaining and a
small bar. Fiber uses a quieter row. Count every selected-day item once, including
planned items; disclose planned food and bulk draft suggestions when present.

Missing nutrition is evaluated independently per nutrient: show the known subtotal
labelled incomplete, omit its exact remaining amount and progress bar. Missing
macro targets show totals alone. Empty entries do not represent zero eaten; an
empty summary explicitly says no food added yet. Round displayed totals and their
remaining values consistently. Over-target text is neutral, not a warning color.

This HTML study includes explicitly illustrative per-food macro values and targets
(1,900 kcal, 120 g protein, 220 g carbs, 60 g fat, 25 g fiber) to make the proposed
layout reviewable. They are not calculated recommendations or production user
data. Custom calorie-only entries never infer missing macros. Ingredient edits
invalidate all recipe nutrition fields. Production uses existing goal ownership
and nutrient provenance. Standard M3 progress indicators and text remain the
Android handoff; no ring or new dashboard screen is needed.

## Compact date navigation (WLO-0142)

Combine Plan, previous/next week and the date-range picker trigger in one 56 px
toolbar. The day strip is 52 px high with a compact selection background and
48 px day targets; no separate week-title block or tall day pills. Swipe the strip
to change weeks, or use the arrows. Tap the date range to open a date jump with
Today, Cancel and Go. Month-crossing weeks show both months. Day selection and
existing food remain intact across navigation. Measured toolbar + strip: 108 px,
plus 4 px bottom spacing, excluding the system status bar.

WLO-0143: tapping the toolbar date range opens a calendar picker, replacing the
input-based jump panel. Show month/year, previous/next month, weekday headings,
a day grid, selected/today styling and Today/Cancel actions. Tap a day to navigate
immediately. Arrow keys move day focus; Page Up/Down move months. Production uses
standard M3 DatePicker/DatePickerDialog; HTML is an interactive visual stand-in.

## Day coverage arcs (WLO-0144)

Replace presence dots with neutral arcs around date numbers. Coverage uses all
food on that day, planned and eaten, counted once against the existing calorie
target. Empty days have no arc. At/over target the arc caps at full; the selected
day summary shows the exact overage. Unknown calories show a dashed incomplete
ring rather than a numeric fraction. Without a calorie target, omit the arc.
Selection remains a separate background. Hover/accessibility descriptions explain
coverage or uncertainty. No success/failure colors or change to navigation height.

## Proportional nutrition overage (WLO-0148)

When a nutrient exceeds its target, the full bar represents the planned total.
Green spans target / total, orange spans (total − target) / total, with a boundary
marker at the target. Show +((total − target) / target × 100)% over target below.
Thus 2,410 / 1,900 kcal uses 78.8% green and 21.2% orange and reports +26.8% over
its target. Apply consistently to calorie and macro bars; below target, retain
target-scaled green plus unfilled track. Unknown totals still omit a precise bar.
Use the same displayed rounded totals for percentage arithmetic. Day arcs remain
unchanged by this bar-specific revision.

WLO-0149: remove the visible percentage subtext below nutrition bars. Keep the
proportional green/orange segments and the existing over-target amount above;
the extra percentage line in WLO-0148 is superseded.

## Suggestions bottom sheet (WLO-0150)

Move multi-meal generation controls out of the agenda into a modal bottom sheet.
The toolbar's labelled Suggest meals icon opens it. Choose dates, meal types and
weekday filtering; remember choices when dismissed/reopened during the session.
Preview closes the sheet and shows draft suggestions on the existing agenda,
with Add/Discard. Existing meals remain protected; generation cannot restart
while a preview is pending. Production maps to standard M3 ModalBottomSheet.

## Persistent meal nutrition header (WLO-0152)

Move each meal's calories from the body into its heading, with Protein/Carbs/Fat
on a quieter second line. These are totals only, without targets or bars. The
header toggles item visibility; calories/macros and the separate + remain visible
when collapsed. Remove the old estimated-total body line. Unknown nutrient values
use known-subtotal + or — when absent, with a nutrition-info action explaining
missing fields. Expansion state is retained per day/meal until demo reset.
