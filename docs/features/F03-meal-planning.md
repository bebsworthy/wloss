# F03 — Meal Planning & Recipes — Functional Specification

---

## Identity

| | |
|---|---|
| **Feature ID** | F03 — Meal Planning & Recipes |
| **Provides** | A week/month meal planner that turns the user's diet template (F01) into concrete meals, feeds them forward as one-tap pre-logs (F02) and backward into the shopping list (F04), with per-day nutrition rollups against targets and planned-vs-actual adherence stats. |
| **User problems solved** | • "What's for dinner" decision fatigue at 6 p.m., solved once a week instead of daily. • MacroFactor has no planner and Mealime died taking users' plans with it; planning and nutrition math never coexist in one app — WLO fuses them. • Planned meals and logged meals are separate universes everywhere; here the plan *is* tomorrow's diary. • Nobody can answer "do I actually eat what I plan?" — adherence is invisible in every competitor. |
| **AI consent category** | Consumes the F12 `meal-planning` toggle (cloud plan/recipe generation); deterministic on-device generation is the default and full fallback. Recipe *import* uses on-device structured parsing (no AI). Voice planning would additionally consume `voice-input`. |
| **Primary evidence** | `mealime.md` (preferences→plan→list pipeline, waste-aware ingredient reuse, no-swap-after-the-fact anti-pattern, 2/4/6 servings rigidity, cook mode, list-reset-on-edit fatal bug); `paprika.md` (day/week/month calendar, reusable menus, arbitrary date ranges, recipe linking, scaling limits); `yazio.md` (recipe→diary integration, cook-mode pipeline); `macrofactor.md` (planned/future timeline entries, "log tomorrow's food today"); `synthesis.md` §1.2, §2 item 8, §3 (cook mode, recipe URL import), §6 checklist. |

## 1. Purpose & Core Objectives

- F03 is where the plan becomes food. It owns the calendar (week and month views), the recipe library, the generation engine, planned meals as first-class pre-log objects, and the planned-vs-actual analysis that only a planner-plus-tracker can compute. It is the structural moat of the product: F09's fiber/poop correlation and F04's auto-list are both downstream of meals being *planned* here.
- Core objectives (verifiable):
  - **Generate a week plan in < 30 s** that hits each day's calorie target within ±5 % and all macro minimums, violating zero preference rules (allergies hard-blocked, dislikes excluded), fully on-device with no network.
  - A planned meal becomes a logged meal in **≤ 1 tap** ("ate this") at mealtime.
  - **Swap any meal after list generation** with nutrition rebalancing shown instantly, and no check-state damage in F04 (the Mealime fatal bug, designed out).
  - **Arbitrary serving counts** — 1.5×, 2.5×, 7× — propagate into ingredient scaling, nutrition math and the shopping list; never a 2/4/6 straitjacket.
  - Every planned day shows a **nutrition rollup vs targets** with provenance per number; adherence stats refuse to claim anything from thin data.
- Benefit to the user: fewer daily decisions, fewer wasted groceries, and the geek joy of seeing plan-vs-reality divergence quantified. Benefit to other features: F03 is the sole *producer* of "planned intake" — the input F02 pre-logs, F04 shops for, F07 uses for adherence, and F09 correlates fiber against.

## 2. User Moments — when and how it is used

- **Sunday planning ritual** (couch, ~10–15 min, relaxed): review the coming week, generate or hand-place meals, save the week as a menu, tap "Build shopping list" (F04). The weekly dopamine moment, paired with F07's check-in.
- **Mid-week swap** (kitchen, 5 p.m., mildly stressed): "not in the mood for curry" → swap → dinner stays on target.
- **Mealtime confirm** (kitchen table, seconds): today's plan card → "ate this" double-tick → done; or "ate something else" → F02 capture flow.
- **Month view review** (monthly, reflective): spot monotony via the variety heatmap and browse saved menus; the month grid is a read-only overview in v1 — editing stays week-level (R-U11) — with month-grid drag-copy arriving [v1.x].
- **Company tonight** (occasional, happy stress): scale dinner from 2 to 6 servings with the dial; the rollup, leftovers and the F04 list all re-derive — a moment that sells the whole plan↔list architecture in one gesture.
- **Import day** (one-time; URL/photo converters are [v1.x] — in v1 the library grows by manual entry, the seed library (R-S3), and AI drafting): recipes arrive from URLs, Paprika/Mealime exports, or photos of cookbook pages — the library grows while the user watches. This is also the Mealime-refugee moment: plans can't be imported from a dead cloud service, but recipes and menus can be rebuilt fast, and the copy says so without naming corpses.
- **Most common flow, end-to-end:** open Planner → week view → "Generate week" → review 7 × 3–4 meal cards → swipe one swap → "Looks good" → "Build list" → F04 opens with the week pre-selected → (days later) tap "ate this" on each planned meal → adherence strip fills.

## 3. How It Works — functional mechanics

- **Inputs:** `DietPlan vN` + `PreferenceProfile` (F01); recipe library (seeded + imported + AI-drafted); weekly targets from F01/F07 (including mid-week check-in adjustments); pantry "expiring soon" hints (F04, opt-in influence); past adherence data; household size for default servings.
- **The recipe object:** versioned, editable, exportable (F13):
  - `{name, servingsBase, cuisine, tags[]}` — identity and facets for filtering.
  - `ingredients[{item, qty, unit, optional?}]` — normalized against the same canonical item space F04 uses, so consolidation and pantry deduction work with zero mapping.
  - `steps[]` — plain text in v1; cook-mode formatting and auto-detected timers are a [future] concern.
  - `nutrition{perServing, basis: label | db | estimated}` — with per-ingredient breakdown available for the "how we got here" drill-down.
  - `provenance{source: url | import | manual | aiDraft, author, importedAt}` — AI drafts are visually distinct until reviewed.
  - `linkedRecipes[]` — Paprika's sub-recipe linking (a vinaigrette referenced from a salad).
  - `lastPlanned, rating, menus[]` — usage memory for the variety heatmap and suggestions.
- **Processing:**
  - **On-device generation engine (default; no consent needed):** deterministic pipeline —
    1. *Filter* recipes by hard rules (allergies, exclusions, IF timing windows, budget band hints).
    2. *Score* remaining recipes for macro fit against each day's targets (calorie distance + macro gap).
    3. *Optimize ingredient overlap* across the week (Mealime's waste-aware reuse: one red onion serves three dinners) with a greedy + pairwise-refinement solver.
    4. *Slot leftovers*: cook-events emit N servings consumed across days (see below).
    5. *Report*: per-day fit deltas and an overlap score accompany the plan.
    Runs in well under a second for a few hundred recipes on mid-range Android. Variety is enforced as a soft constraint (recently-planned recipes down-weighted) so the anti-waste overlap objective never collapses into eating chili four nights running — the solver reports when the two objectives pull against each other.
  - **Cloud generation (optional) [v1.x — deterministic-only in v1 per R-S10]:** with the F12 **`meal-planning`** consent toggle ON + BYOK key, the user can request "plan a lighter week, cooking only 3 times" or novel recipes with constraints. Payload preview shown before send; results are **drafts** — every AI-generated recipe carries an `AI-drafted · nutrition estimated` provenance badge and must be reviewed and saved before entering the library or a committed plan. **Fallback when off/offline:** the deterministic engine plans from the existing library and says so ("planning from your 214 saved recipes").
  - **Recipe import [v1.x — URL/photo importers sit outside the v1 scope per the master doc]:** on-device structured parsing of recipe-schema markup from URLs and share-sheet text (Paprika's pattern, no AI); when the source provides no nutrition (Paprika's gaping hole), per-ingredient nutrition is estimated on-device via F02's food data, provenance-tagged `estimated`; manual entry always available; batch import from Paprika/Mealime exports (with F13).
  - **Planned meals as pre-logs:** a planned meal writes a *planned* entry into F02's timeline for its date/slot (MacroFactor's "log tomorrow today"). State machine, owned by F03: `planned → confirmed` (one tap, adopts recipe nutrition) | `swapped` (old plan retired, swap recorded) | `skipped` (no guilt; feeds adherence honestly) | `replaced` (links to the actual F02 entry, which supplies nutrition).
  - **Leftovers / cook-once-eat-twice:** a scheduled cook-event can emit N cooked servings; subsequent days reference the *same cooked batch* as leftover instances carrying parent provenance; ingredient weight is charged to the cook day only, so F04's list pays for cook events, not eating events. Semantics: a cook-event has a date, a slot, a batch size, and a shelf-life hint (default 3 days); leftover instances beyond shelf life are flagged, not blocked; deleting the cook-event offers to convert its leftovers to `skipped` or `replaced`.
  - **Swap with rebalancing:** swapping a meal recomputes the day rollup instantly and shows delta chips (−230 kcal, +14 g P); the engine proposes top-3 swaps that best restore target fit; day- and week-level targets are *never* silently re-based — the user sees what the swap did.
  - **Menus (plan templates):** any week or date range saves as a named reusable menu (Paprika); menus re-apply with current serving counts and current targets, re-rolling only slots the updated rules now violate, with a summary of what moved.
  - **Generation settings (user-tunable, persisted per plan):** rule strictness (hard-block vs "prefer"), ingredient-overlap weight, cooking-frequency cap ("max 3 cook events/week" — leftovers fill the rest), budget-band influence, pantry-expiry influence toggle (off by default until the pantry has data), variety bias (how strongly recent meals are down-weighted). Defaults are sensible for one; the geek tunes them like a mixing desk.
- **Outputs / artifacts:** `PlannedMeal[]` (calendar + state machine), `Recipe[]` (library), `Menu[]`, per-day/per-week nutrition rollups, planned-vs-actual dataset, ingredient-overlap and variety scores, cook-event records, list-generation requests to F04.
- **State owned:** calendar placements and meal states, recipes, menus, generation settings (how strongly pantry expiries and budget influence), adherence computations, cook-event definitions. All of it is versioned or timestamped enough to survive export/import round-trips (F13) without losing planned-vs-actual history.

## 4. User Interaction Model

- **Entry points:** Planner tab; F10 Daily Hub "today's plan" card (with "ate this" inline); F01's post-onboarding CTA "generate your first week"; F04's reconciliation banner deep-links back when the plan changed mid-shop; F11 stats tapping a week bar opens that week's plan; share-sheet target for recipe URLs anywhere on the device.
- **Happy path (generate):** week view → Generate → engine fills slots with fit badges per day ("kcal ✓ · P −6 g") → user swipes one card left to see swap suggestions → accepts → "Build list" → F04 opens with the week pre-selected.
- **Primary flows:** *Generate* (above); *Swap* (card left-swipe → 3 suggestions or library browse → pick → deltas fly to the day ring → list reconciles silently); *Confirm* (F10 card → "ate this" → double-tick); *Save menu* (week view → "Save as menu" → name via chips or text → done); *Import recipe* (share-sheet tap or paste URL → on-device parse preview assembles → nutrition estimated if absent, badge stamped → "Save to library" or adjust first); *Leftover planning* (long-press a cook meal → "repeat as leftovers" → pick target days → connector lines drawn).
- **Fallback paths:** (a) *Library too small to satisfy rules* — generation succeeds partially; unfillable slots show a neutral "add anything" card with the reason ("nothing vegetarian, <20 g carbs, no mushrooms in your library — add/import, or relax one rule with a tap"). Never a blank failure. (b) *Cloud generation dies mid-request* — the on-device result is offered automatically; no error dead-end. (c) *Plan edit after shopping started* — F04 reconciles by delta; F03 surfaces what changed ("onion qty +2 — your checked items are untouched").
- **Input minimization — never typed:** generation needs zero input beyond the template that already exists; swaps are pick-from-3; serving changes via a ± dial with 0.5 steps; recipe import is a share-sheet tap; "ate this" is one tap from F10; menu names offer chips ("Work week", "High-protein"). Free text appears only in recipe editing and optional cloud prompts.
- **Micro-interactions:**
  - Generate: meal cards deal into the 7-day grid with 40 ms stagger and a soft tick per card; day-fit badges then flip like split-flap displays to their values — plan quality read at a glance.
  - Swap: old card slides out left while the new one slides in; **delta chips** (−230 kcal) fly from the card to the day-rollup ring, which re-animates its fill — cause and effect visible in one gesture.
  - "Ate this": two-beat confirm — first tap arms (a ring traces the checkmark), second tap commits with a double-tick haptic and a springy green tick; the day's adherence strip extends with a zipper motion.
  - Drag-to-replan: cards lift with shadow + 8 haptic notch points crossing slot boundaries; landing snaps with a settle bounce.
  - Leftover linking: a leftover instance shows a stitched "tupperware" connector line to its parent; tapping it pans the calendar to the cook day.
  - Month variety heatmap: cells tint by recipe repetition; long-press a tinted cell lifts the whole repeated group with a rubber-band stretch and offers "diversify this week".
  - Import: parsed recipe assembles itself — ingredients slide into place, the nutrition panel counts up, and the provenance badge stamps down with a soft thud.
  - Menu apply: a saved menu cascades into the week with the same deal-in animation as Generate, but faster (25 ms stagger) — the user has seen this show before; leftover instances arrive pre-connected, their tupperware lines drawing in one stroke.
  - Generation settings: each dial is a mixing-desk fader with fine haptic detents; moving a fader re-runs generation live at ≤ 100 ms for previews (debounced), the grid re-dealing only where slots actually changed.
  - Serving dial for company: turning it to 6 multiplies the plate icons on the meal card with 60 ms stagger and shows a one-line list impact preview ("+4 items to shop") — the plan↔list architecture, felt in one gesture.
- **Data-quality gating:** day rollups show `estimated` provenance when any recipe's nutrition is self-entered or AI-estimated; **adherence % is refused** (shown as "not yet meaningful") until ≥ 3 logged days exist in the window; overlap/waste scores require a full 7-day plan, never a partial one; nutrition "per serving" is suppressed entirely for recipes with unresolved ingredients (listed as `unknown`, never guessed silently).

## 5. What the User Gets Out

- **Week/month calendar** with per-meal cards, fit badges, leftover links, and day nutrition rings vs targets.
- **Planned-vs-actual analysis** (the geek gold): per-day energy and macro deltas (planned vs confirmed-actual), weekly adherence strip, trended over time, with the honest headline ("your plans run 8 % under what you actually eat — biggest gap: Friday dinner").
- **Adherence metrics defined (so they can be built and trusted):**
  - *Plan coverage* — % of planned slots that reached a terminal state (confirmed/replaced/skipped); a slot still `planned` after its date is simply open, never penalized.
  - *Energy fidelity* — median |actual − planned| kcal across confirmed days; shown as a distribution, not a grade.
  - *Swap gravity* — which meals get swapped most, suggesting library gaps worth filling.
  - *Cook realism* — planned cook-events vs actual cooking rhythm (inferred from leftover confirmations), feeding generation settings.
- **Ingredient-overlap score and waste-avoided estimate** per plan ("this plan reuses 9 ingredients across recipes; ≈ €4.10 less than an unshared plan") — Mealime's invisible optimization, made visible.
- **Recipe library** with per-recipe nutrition and provenance (measured-from-label / DB / estimated / AI-drafted), ratings, last-planned dates, linked sub-recipes, and the menus that contain it. Library health is surfaced honestly: a "library fit" gauge shows how much of the rule space the current library can satisfy, so a weak generation result reads as a library gap with a fix, not a mysterious failure.
- **Menus:** the household's greatest hits, re-applyable in two taps with current targets.
- **Provenance rule:** every rollup number states its basis and opens a "how we got here" breakdown of per-ingredient contributions; swapping a meal visibly re-stamps the basis so mixed-provenance days are never mistaken for verified ones.
- **Visualizations owned by F03:**
  - *Calendar variety heatmap* — month grid, tint by recipe repetition; monotony is visible before it is felt.
  - *Adherence strip* — 7-segment weekly strip that fills on confirmations; renders as "not yet meaningful" below the data gate.
  - *Planned-vs-actual deltas* — per-day paired bars or a scatter over time; trended monthly.
  - *Per-day fit badges* — generated inline, always showing the gap to target, never a pass/fail. (The daily budget ring is F02's; weekly target trends are F07's.)

## 6. Motivation & Psychology

- The Sunday ritual is a **weekly re-commitment moment** — paired with F07's check-in it feels like coaching yourself, not homework; the goal is that planning becomes identity ("I'm a Sunday planner").
- Adherence stats are deliberately **information, not judgment**: the number answers "how good is my planning?", not "how good are you?" — misses update the model (MacroFactor's adherence-neutral stance) and the copy says "plans that survive contact with Tuesdays".
- Gamification: F03 owns **local hooks** — planning streak ("planned 4 weeks in a row"), first-menu-saved, 100 %-week-confirmed — all emitted to F11, which owns badges/streak mechanics. No red "over target" anywhere; deltas are neutral-hued by design.
- The planning streak is deliberately calendar-based, not logging-based: a week with a plan but a chaotic execution still counts, because the *planning habit* is what F03 builds; execution honesty is F02/F07's domain.
- Tone rules: swaps and skips carry zero moral weight ("Friday's plan met real life — replanned", never "you went off plan"); waste stats never shame ("€4.10 avoided this week", never "you threw away food"); recipe ratings describe the food, never the eater. House copy style for the Planner: short, dry, numbers-forward ("3 cook events · 4 leftovers · €31 est."), with warmth reserved for moments of change (swap, replan, skip).

## 7. Relations to Other Features

- **Consumes from:** **F01** (diet template, preference profile, targets, household size for default servings); **F07** (current weekly targets incl. check-in adjustments — plans are generated against *today's* budget, not onboarding's); **F02** (actual logged meals for planned-vs-actual; F02's food/nutrition engine for recipe nutrition estimation); **F04** (pantry expiring-soon hints; list-reconciliation status); **F13** (recipe export/import, Paprika/Mealime migration); **F12** (`meal-planning` consent; `voice-input` if voice planning ships).
- **Feeds into:** **F04** (date-range + scaled ingredient requirements + cook-vs-leftover semantics → auto list; edit deltas for reconciliation); **F02** (planned entries as pre-logs; confirmed plans become diary rows); **F09** (per-meal fiber/FODMAP-category content of planned *and* eaten meals — the plan↔poop correlation depends on this); **F10** (today's planned meals card, "plan your week" nudge); **F11** (planning-streak and confirmation hook events); **F13** (recipes/menus/plans in export bundles).
- **Shared concepts:** Targets, Provenance, Consent, Logs (planned vs actual states), Trend.
- **Conflict/boundary notes:** F03 owns *what will be eaten*; F02 owns *what was eaten* — the planned-meal state machine is the treaty (planned entries live in F02's timeline but are owned and mutated by F03 until confirmed or replaced). F04 never mutates the plan; it only derives and reconciles. Cook mode is explicitly **out of scope here beyond a note** — see Blue Sky. Until a dedicated cook-mode spec exists, the in-app step view stays read-only: no partial cook-mode shims, no half-persisted session state.

## 8. Blue Sky Ideas

- **[v1] Generation with visible reasoning.** After generating, an explainer panel — "why this plan": overlap edges between recipes, the two rules that constrained hardest, per-day fit math. The plan stops being a black box and becomes an artifact the geek can interrogate (MacroFactor's published-math ethos applied to planning).
- **[v1] One-tap pre-log loop.** Planned→confirmed in one tap from F10, with the adherence zipper. This is what makes the planner and the tracker one product (MacroFactor's timeline pattern, but planned entries come from a real calendar).
- **[v1] Swap-with-rebalance + top-3 suggestions.** Kills Mealime's #1 complaint (no post-hoc swaps) and its fatal bug (list resets) in one interaction.
- **[v1.x] Rebalance dial.** Drag today's remaining budget ±150 kcal and the rest of the week re-solves visually — meals re-slot or portion dials nudge — as a live before/after overlay. Weekly-cadence users (Lose It! cyclers) get the same at week level. The dial is the designed answer to "plans are too rigid", the category's second-most-heard planner complaint after Mealime's swap bug.
- **[v1.x] Pantry-aware generation.** A toggle makes the engine weight F04's expiring-soon items when scoring recipes ("this week's plan consumes your wilting spinach, the open feta, and 4 eggs approaching date"). The planner becomes the anti-waste engine Mealime only advertised.
- **[v1.x] Voice planning.** "Plan a light week, eating out Thursday and Friday, fish twice" → on-device STT + parser drafts constraint chips for confirmation, then generates. Needs the F12 **`voice-input`** toggle; full on-device fallback is the same chips set by hand.
- **[v1.x] Adherence forensics.** Drill from any adherence week into the gap decomposition: which meals, which weekdays, planned-vs-replaced nutrition distributions — turning "I'm always 8 % over" into "I'm 22 % over on Fridays, all from replaced dinners".
- **[future] Cook mode (hands-free).** Mealime's hover-to-advance + Paprika's keep-awake/timers, with WLO fixes: cook-session state persisted in the local DB (never lost on navigation — Paprika's bug), per-step macro callouts, pinned-multi-recipe bottom bar for coordinated cooking. Deliberately not spec'd here; deserves its own mini-spec when scheduled.
- **[future] Photo-to-recipe.** Photograph a cookbook page or handwritten card → on-device OCR/vision drafts a structured recipe (F12 **`food-photo`** toggle for any cloud assist; pure on-device OCR fallback). Paprika's open flank (URL-only capture), attacked photo-first.
- **[moonshot] The leftover graph solver.** Full constraint optimization across recipes, portions, cook-events, leftovers, pantry expiries and prices (F04 [v1.x] cost data): "minimize cost + waste subject to hitting every day's macros" — rendered as an editable solution with slack dials. The waste/cost/adherence triangle becomes the geek's weekly chess puzzle, all on-device.

## 9. Guardrails, Privacy & Sensitivity

- Recipes, plans, menus and adherence stats are local data, included in F13's encrypted store and exported only explicitly. Nothing about eating intentions leaves the device without the F12 `meal-planning` toggle ON at that moment, with a pre-send payload preview.
- AI-drafted recipes and plans are **always drafts**: provenance-badged, visually distinct until saved, never auto-committed to the calendar or library (confirm-before-write).
- Allergen handling is a hard rule, not a preference: allergens in the profile block generation and swap suggestions outright; imported/AI recipes containing a listed allergen are flagged at the top of the recipe, not buried in ingredients. The block applies even when the allergen entered the library before the profile did — profile changes re-scan the entire library and calendar.
- No bundled-recipe marketing funnel: the seed library ships as data files, fully inspectable, user-editable and excludable; no recipe is ever paywalled or ad-supported (free forever).
- Cloud-generated content must never phone home implicitly: one consent toggle covers plan+recipe generation, revocable mid-session, with the last-used provider and timestamp visible in F12's consent matrix. Revocation mid-generation aborts the request; partial drafts already received are discarded, not saved.
- Tone audit: adherence, waste and fit language is verdict-free; no food is labeled "cheat", "bad" or "guilty".

## 10. Open Questions

- **Seed library size and license for v1:** Mealime had "a few hundred"; Yazio 3,000+ with a content team. Options: small curated open-licensed set (~50) + strong import + AI drafting, vs. generating a larger set offline at build time. Needs a decision before v1 content work. *(Resolved: R-S3 — ~50 open-licensed starter recipes + import + AI drafting; a larger curated library is v1.x content work.)*
- **Month-view depth in v1:** full month editing vs month-as-read-only-overview with week-level editing only (recommended for v1). *(Resolved: R-U11 — read-only month view; week-level editing.)*
- **Interaction contract with F02 for `replaced` meals:** does the replacement adopt the planned slot's meal-type labeling, and who owns the joining record? Needs a joint decision with the F02 spec. *(Resolved: R-B1 — the F02 entry owns nutrition; F03 keeps slot labeling and the link.)*
- **Generation optimization scope in v1:** greedy scoring + pairwise overlap reuse is safely v1; the full constraint solver (leftover graph, cost-aware) is likely v1.x — confirm the performance budget on mid-range Android.
- **Fit-badge thresholds:** ±5 % kcal and macro-gap bands are proposals; align exact thresholds with F07's tolerance semantics so a "fit" plan never argues with a "held" TDEE week. *(Resolved: R-S7 — ±5 % kcal, aligned with F07's proposal semantics.)*
- **Recipe nutrition estimation boundary:** F02 owns the per-ingredient nutrition engine; confirm F03 may call it synchronously during import/generation, and define offline behavior when an exotic ingredient has no cached data (`unknown` marker — never a silent guess).
- **Voice planning sequencing:** if `voice-input` consent lands in F12 v1, does voice planning ship with F03 v1.x or wait for the F02 voice-logging grammar to stabilize?
- **Adherence metric ownership:** the metrics defined in §5 are F03-computed; confirm F11 consumes them as read-only inputs (display/badge material) rather than defining its own competing adherence numbers — one definition per number, or the geek trust model breaks. *(Resolved: R-B4 — F03 computes, F11 consumes read-only; one definition per number.)*
