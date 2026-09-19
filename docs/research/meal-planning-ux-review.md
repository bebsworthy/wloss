# Meal-planning UX review — WLO-0127

18 September 2026. Research supplement and design recommendations, not a release
commitment. Companion: [proposed user flow](../design/meal-planning-user-flow.md).
Owner scope: pantry management is entirely excluded from this review's proposed
experience. Shopping is only an optional downstream handoff.

## Conclusion

Make it easy to decide what to eat, get the recipe without transcribing it, and
reuse the decision. A recipe library should accumulate through use; creating one
must not be homework before planning starts. Import and AI assistance belong in
the first useful journey, alongside familiar meals and a small starter collection.

The old direction over-invests in a seven-day optimization ritual and underspecifies
recipe acquisition, partial planning, everyday meals, and changes of mind. Keep
its editable suggestions, provenance, explicit logging, and reusable menus.

**Evidence limit:** this review uses repository research, official product pages,
help documentation, and a source-code walkthrough. No new competitor app sessions
or WLO usability sessions were run. Marketing establishes advertised capability,
not accuracy, usability, or prevalence. “Users will rarely author recipes” is a
reasonable owner hypothesis to design for and test, not a measured population fact.

## Existing research audit

All 19 original competitor studies were screened for planning, acquisition,
repetition, correction, and trust patterns. The two syntheses and subsequent
weight/intake research were also reviewed for applicable constraints. This is a
meal-planning review, not a revalidation of every claim in unrelated app studies.

| Existing evidence | Lesson retained | Change in application |
|---|---|---|
| [Mealime](mealime.md) | Preferences, low decision burden, practical recipes; historical complaints about inflexible edits | Start from the meal decision. Do not derive a mandatory full-week planner from a cook-queue product. |
| [Paprika](paprika.md) | Capture existing recipes, local ownership, scaling, reusable menus | Make import reachable from the empty planner and a selected meal; avoid requiring library organization first. |
| [MyFitnessPal](myfitnesspal.md), [Yazio](yazio.md), [Foodvisor](foodvisor.md) | Goal context and recipe-to-diary continuity | Connect planning to the saved calorie goal, with editable personal portions and direct logging. |
| [MacroFactor](macrofactor.md), [Lose It!](loseit.md) | Reuse, future meals, calorie schedules, low logging burden | Read the target for each date; repeat familiar meals; distinguish intention from consumption. |
| [Cronometer](cronometer.md) | Nutrient context and review of recognized ingredients | Nutrition calculations need known ingredients and quantities; recognition alone does not verify an amount. |
| [Cal AI](cal-ai.md), [SnapCalorie](snapcalorie.md) | Correct in place, save familiar meals, avoid repeat input | Use the correction pattern for recipe drafts. A meal photo cannot recover an exact recipe or hidden ingredients. |
| [Zolt](zolt.md) | Draft → confirm, missing-data states, conversational shortcuts | Short prompts can create editable proposals; chat should not replace the plan itself. |
| [Waistline](waistline.md) | Meals can be food groups rather than cooking recipes; local portability | Let “yogurt + banana” be a saved meal without requiring steps or recipe metadata. |
| [Gyroscope](gyroscope.md) | Clear numbers with drill-down | Show decision-relevant totals, not an adherence dashboard before there is a plan. |
| [Hevy](hevy.md), [Fitbod](fitbod.md) | Prefill from last time; predictable routines plus suggestions | Transfer these as design analogies, not evidence of meal-planner outcomes. |
| [Happy Scale](happy-scale.md), [openScale](openscale.md), [MeThreeSixty](methreesixty.md) | Optional setup, source transparency, recoverable capture failures | No new body-data questionnaire to plan dinner; preserve import progress when extraction fails. |
| [Gut category](poop-trackers.md) | Later relationship between food and symptoms | Retain fiber provenance, but do not add digestion setup or correlations to the planning journey. Planned food is not evidence of consumption. |

The [original synthesis](synthesis.md) and
[design synthesis](../design/research/synthesis.md) are historical inputs, not
proof that all their recommendations remain appropriate. The weight/intake
studies inform the boundary between recording a user's target and making an
automated recommendation; they do not establish meal-generation efficacy.

### Corrections to carry forward

- “Planning and nutrition never coexist” and “no competitor combines these” are
  unsupported category-wide claims. The existing MyFitnessPal study already
  describes a target-aware planner; Eat This Much is a major omitted comparator.
- Recipe import should no longer be treated primarily as migration polish. It
  is an everyday acquisition path, including sharing from another app.
- The old studies contain superseded WLO pricing, body-photo, and release advice.
  The current objective and master rulings govern, not those historical takeaways.
- Mealime is **scheduled to close on 21 October 2026**, not already closed as
  some F03 wording implies. Its official site still shows that announcement on
  this review date. Do not infer that all users' data is necessarily lost.
  [Official site](https://www.mealime.com/).
- Historical swap/list-reset complaints remain useful failure scenarios, but
  were not reproduced in this review and must not be presented as freshly tested.
- An ingredient match in a food database does not guarantee recipe accuracy.
  Portions, preparation, yield, and missing ingredients still matter.

## Additional primary-source research

Sources below were accessed on 18 September 2026. Recommendations are WLO design
inferences, separate from the documented competitor behavior.

### YouMealPlan — the owner's reference

The Android listing describes weekly planning, reusable menus, AI-assisted imports
from supported website/social links, editable nutrition, and grocery lists. Its
update notes also mention extracting recipes from photos, PDFs, and pasted text.
It presents acquisition as part of planning rather than a lengthy authoring job.
This is advertised functionality; extraction success and nutrition accuracy were
not tested. [Google Play listing](https://play.google.com/store/apps/details?id=meal.planner&hl=en).

**WLO inference:** put “Import recipe” beside meal suggestions, retain the source,
and offer “Save and add to Tuesday dinner.” Do not promise every website or video
works. AI-assisted extraction and inventing a new recipe are different actions.

### Eat This Much — the missing goal-aware reference

Its documentation describes using favorites, collections, and individual foods
as recurring meal inputs, including simpler meals without full recipes. Separate
guidance recommends leftovers and recurring foods to reduce preparation burden.
[Preferred foods](https://help.eatthismuch.com/help/can-the-generator-build-my-meal-plans-using-only-my-favorites-custom-recipes-or-other-preferred-foods)
and [simpler plans](https://help.eatthismuch.com/help/how-do-i-set-up-the-planner-to-provide-simpler-meal-plans).
Its public product offering combines automatic daily/weekly plans with tracking
and reusable weeks. [Product capabilities](https://www.eatthismuch.com/pricing).

**WLO inference:** the meaningful automation is often “keep breakfast, repeat two
lunches, suggest dinners,” not twenty-one novel recipes. Familiarity and realistic
cooking effort deserve priority alongside calorie fit. Do not copy its interface
or nutritional presets without separate evaluation.

### Plan to Eat — capture and practical scheduling

Official release notes describe social imports, photo scanning, planner search,
bulk moving/copying, serving changes, and reusable plans.
[November 2025 product update](https://www.plantoeat.com/blog/2025/11/the-meal-planning-updates-youve-been-asking-for/).
Its current Instagram instructions give a share-to-app flow and acknowledge that
some imports take 15–20 seconds.
[Instagram import help](https://learn.plantoeat.com/help/import-recipes-from-instagram).
Its planner supports leftover copies and notes for irregular meals.
[Leftover planning help](https://learn.plantoeat.com/help/adding-leftovers-to-the-meal-planner).

**WLO inference:** preserve the destination meal through a longer import, allow
users to return later, and make “eating out” and leftovers ordinary calendar
choices. Do not force every meal into a complete recipe record.

### Paprika and structured recipes — dependable basics

The Android guide documents browser capture, editing, source URLs, recipe scaling,
calendar placement, and reusable menus.
[Official Android guide](https://www.paprikaapp.com/help/android/).
Recipe markup can represent ingredients, instructions, nutrition, and yield;
yield may be text such as “1 loaf,” not a numeric serving count.
[Schema.org Recipe](https://schema.org/Recipe).

**WLO inference:** attempt structured extraction before AI assistance. Ask a
focused yield question only when needed for personal-portion math. A readable
recipe may be saved even when its nutrition remains unknown. A website retrieval
is network access even when subsequent parsing runs locally.

## Review of WLO's current journey

Reviewed [F03](../features/F03-meal-planning.md), the relevant F01/F02/F04/F12 and
master boundaries, IA, the plan/shop prototype, check-in handoffs, and the current
F03 screen, sheets, editor, and view-model code. Code observations are static,
not an emulator validation.

| Finding | UX consequence | Proposed response |
|---|---|---|
| `PlanScreen` starts with “Deal a week of meals”; Plan/Recipes/List/Pantry segments | The first action assumes a library and full-week commitment | Ask which days/meals need help; remove pantry from the proposed surface. |
| Current recipe editor asks for name, base servings, macros and tags; it is not a complete ingredients/instructions capture flow | The manual-first library does not solve the cooking job | Prioritize capture, readable recipe review, personal portions, and reuse. |
| Empty slot sheet instructs users to add a recipe and re-deal, or skip | One missing meal sends the user away and risks unnecessary replanning | Add directly to the selected slot through import, suggestions, saved meals, or a note. |
| Replacement sheet sends users to the diary before linking a replacement | The common “ate something else” path breaks context | Open logging with the planned slot attached; return after save. |
| Flow 05 emphasizes list/pantry and assumes a populated plan | First-use and recipe acquisition are not demonstrated | Replace it as the planning design reference with the proposed flow document. |
| Flow 03 says targets automatically regenerate the week | Accepted meal choices may be unexpectedly replaced | Refresh comparisons immediately; make meal changes an explicit draft. |
| F03 promises one-tap confirmation but also describes a two-tap arming interaction | Conflicting mealtime behavior | One explicit “Log this meal” action with Undo; portion adjustment remains available. |
| F03 describes ±5% full-day fit and macro minimums | A partial dinner plan or unknown nutrition can imply false adequacy | Separate coverage, known totals, and optional goal comparison. |
| R-S3 mentions AI/import, while R-S10 and §5 defer generation/import capabilities | Research/design can be mistaken for shipped functionality | Keep the proposed experience separate from release commitments; list required amendments. |

## What to prioritize and validate

For a coherent planning release: partial planning, familiar meals, website/text
import with correction, accessible recipe reading, editable suggestions, explicit
logging, and reuse. Design AI drafting as a first-class acquisition branch, but
prove on-device capability and the consented BYOK path before promising availability.
Defer month heatmaps, adherence scoring, elaborate solver controls, and social/video
coverage promises. Pantry has no role in this proposal.

Test the flow with people who plan only dinner, repeat meals, and cook for others.
Observe whether they can import and schedule a recipe, identify their own portion,
recognize an incomplete calorie total, replace one meal, and reuse a previous plan
without explanation. Compare imported, generated, and familiar-meal starting points;
do not assume AI is the preferred acquisition method for everyone.

Remaining evidence gaps are importer reliability across ordinary blogs/languages,
on-device recipe-generation quality and latency, practical review burden, and
whether people prefer tonight, several days, or a week as their default horizon.
These need prototype and capability tests, not more competitor feature counting.

## Owner clarification — flexible meals and travel periods (WLO-0128)

The owner describes eating lunch at the office without knowing the available food
in advance and without wanting an entry obligation, and extends this to any lunch,
dinner, whole day or travel week. This is direct product input,
not a new competitor finding. The proposed flow now distinguishes intentionally
flexible periods from slots awaiting suggestions, supports recurring weekday scope
and one-off date ranges,
and makes later logging optional. Planning completion is independent of full-day
nutrition coverage. An optional calorie allowance is a planning assumption, never
an invented food record. Validate that users can finish and reuse a dinner plan
without being prompted to fill in their office lunches or catch up after travel.

## Owner correction — time is the permanent surface (WLO-0130)

The owner rejected turning flexibility into a travel screen or explicit empty-period
configuration. The current [user flow](../design/meal-planning-user-flow.md) replaces
that proposal: the day agenda is always the first screen, all meal entries remain
visible, and an empty entry can be suggested or logged directly. Users may never
plan at all. This supersedes the previous section's proposed flexible-period state
and the original full-week-first design. Office lunches/travel remain use cases,
not UI modes. Planning several meals is an optional scoped action on the agenda.
