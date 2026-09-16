# F01 — Onboarding, Goals & Diet Plan Studio — Functional Specification

---

## Identity

| | |
|---|---|
| **Feature ID** | F01 — Onboarding, Goals & Diet Plan Studio |
| **Provides** | A zero-account weight-first entry plus an optional, fully editable Diet Plan Studio. The plan is a versioned artifact defining calorie/macro targets, schedule, food rules, and milestones for users who choose broader planning. |
| **User problems solved** | • Competitors force a signup before showing anything useful (Yazio made "no sign-up" a marketing headline; WLO makes it architecture). • Diet "plans" in the category are marketing funnels that produce a paywall, not an object the user owns, edits, versions or exports. • A goal 12 kg away is demotivating; nobody auto-breaks it into winnable steps except Happy Scale, which tracks no food. • Migrating from MFP/Lose It! means losing history; WLO imports it instead — and Mealime's shutdown proves planners die and take plans with them. |
| **AI consent category** | Consumes the F12 `meal-planning` toggle (optional cloud refinement of a user template); everything in F01 works with zero AI, zero network. |
| **Primary evidence** | `yazio.md` (no-signup onboarding, programs, fasting-plan integration pattern); `loseit.md` (Calorie Schedule / weekday-weekend cycling, plan-as-forecast spine); `mealime.md` (preference quiz: 8 diets, allergy filters, 119 dislikable ingredients; preferences as a first-class object; arbitrary servings demanded); `happy-scale.md` (milestone goal breakdown, Fresh Start hide-not-delete, "how we got here"); `zolt.md` (target cadence daily/weekly, calorie floor, "an estimate, not a promise"); `synthesis.md` §1.1, §2 items 3/15/17, §6 checklist. |

## 1. Purpose & Core Objectives

- F01 owns the small weight-first entry, goal definition, and the optional
  **Diet Plan Studio**. The weight loop works without a plan. When authored,
  `Plan vN` is the structured artifact F02/F03/F07 consume and F13 exports.
- Core objectives (verifiable):
  - First launch → Weight after unit plus an optional goal and optional
    import/manual reading, with **no account, no email, no network** required.
    Creating a Diet Plan is a separate optional success metric.
  - A **3-band forecast preview** (optimistic / expected / pessimistic) is on screen **within 30 s** of entering a goal, rendered via the F07 forecast engine.
  - Every value the wizard produces is editable afterwards in the Studio; nothing is write-once; every edit is a new plan version with a human-readable diff.
  - Any goal requiring calories below the safety floor is **refused with a counter-proposal** (slower pace), never silently accepted — in code, not copy.
  - Fresh Start hides history without deleting a single row; import brings in generic CSV/JSON data fully offline at v1 — MFP/Lose It!/Paprika/Mealime converters follow in v1.x (R-S4) — with a per-category report.
- Benefit to the user: a plan that feels *authored by them*, not issued to them — with honest math labels from day one. Benefit to other features: F01 defines the shared **Targets** and **Plan** objects; without F01, F02 has no budget, F03 has no rules, F07 has nothing to adapt.

## 2. User Moments — when and how it is used

- **First launch** (couch or bathroom scale in hand; curious, skeptical, possibly burned by MFP pricing or Mealime's shutdown): the moment that decides retention. The screen must prove "different" before asking for anything.
- **Goal setting / revision** (episodic, reflective): after a decision ("wedding in June", "enough"), or after an F07 weekly check-in proposes a target change — the proposal opens the Studio as a diff.
- **Template editing** (weekly-monthly, paired with F07's Sunday check-in): tweaking macros, weekend budgets, exclusions, timing windows — the numbers-geek's ritual.
- **Restart after a lapse** (emotional low point): Fresh Start. The tone is a clean page, never a reckoning.
- **Migration** (one-time, 5–10 minutes): importing from a dying or resented app; "your data outlives the app" is the wedge.
- **Preference re-run** (occasional): tastes and households change; the quiz is an instrument, not intake paperwork.
- **Most common flow, end-to-end:** open app → welcome/privacy → choose kg or
  lb → optionally choose lose/maintain/gain → import or enter a reading (or do
  it later) → Weight. Plan Studio is an explicit optional continuation.

## 3. How It Works — functional mechanics

- **Inputs:** body basics (weight, height, age, sex, activity level — all optional, each pre-filled from F06/F13 when available); goal (target weight + either pace in % body-weight/week or a target date); diet template choice; preference quiz answers; imported archives (via F13).
- **Weight-first first run (Release 1):** unit (`kg`/`lb`) → optional
  loss/maintenance/gain goal → Health Connect/file import or manual first
  weigh-in → Weight. This small flow owns onboarding completion; it does not
  require a Diet Plan.
- **Optional Plan Studio setup (after first run):**
  1. *Welcome & privacy statement* — one card, one sentence of value, one sentence of architecture ("no account, no server, no ads").
  2. *Goal* — current weight (prefilled or optional), target weight, pace slider. Date-anchored goals ("by June") are converted to an implied pace and shown as such.
  3. *Forecast preview* — 3 bands only after the shared WLO-0080 eligibility
     result allows math; otherwise an explicit held state. Eligible gain goals
     use F07's separately validated direction-specific model.
  4. *Template gallery* — diet templates as cards; each card names its rules in plain language.
  5. *Preference quiz* — swipe deck (see below).
  6. *Schedule* — weekday/weekend calorie bars (skippable; defaults to flat).
  7. *Milestones* — auto-generated ladder, editable names.
  8. *Start* — writes `Plan v1`; the Hub planning surfaces activate.
- **Processing:**
  - Initial targets from **Mifflin-St Jeor + activity multiplier**, explicitly labeled a *formula estimate*; F07 replaces it with a measured TDEE after ~2–3 weeks of data, and the Studio shows that provenance transition on every affected number.
  - **3-band forecast preview:** F01 passes the shared safety eligibility result
    to F07 before any numerical call. Eligible loss goals with the required
    profile facts may receive wide bands labeled "formula-based, will sharpen
    as you log". Missing facts, unanswered screening, unsupported inputs, and
    gain goals produce a held state rather than a date.
  - **Target cadence:** daily vs weekly calorie budget (Zolt's cadence choice). Weekly cadence unlocks the weekday/weekend **Calorie Schedule** (Lose It!): e.g. 5 × 1,800 + 2 × 2,400 = the same weekly deficit, rendered as a 7-bar chart with the weekly total pinned while bars are dragged.
  - **Macro-split presets:** balanced, high-protein, mediterranean, keto (carb-limit ring + tracker-surface changes, per the Yazio fasting-module integration pattern), low-carb, IF (16:8/5:2/6:1, with timer surfaces in F10), custom sliders. Each preset is a template with different fields — one schema, no special cases.
  - **Shipped template library (v1 seed), all plain JSON in the same schema:**
    - *Balanced Deficit* (default) — 30P/40C/30F, flat schedule, no exclusions.
    - *High-Protein* — 35–40P, protein-floor ring pinned in F02.
    - *Keto* — ≤ 30 g carbs, carb-limit ring, ketosis education card, fat-adaptation note at day 10.
    - *Mediterranean* — 20P/45C/35F, fish-twice-a-week soft hint, olive-oil-friendly budget band.
    - *Low-Carb* — moderate split, carb ceiling without ketosis claims.
    - *Intermittent Fasting 16:8 / 5:2 / 6:1* — eating-window timing rules, F10 timer surface.
    - *Custom* — blank canvas, every field exposed.
    No template is privileged: all ship as inspectable data files, all editable, all excludable from the gallery.
  - **Templates are first-class artifacts.** A `DietPlan` is versioned JSON: `{targets, cadence, weekdayWeekendSchedule, macroSplit, foodRules{allergies, exclusions, dislikes}, timingRules{IF windows, meal slots}, surfaces{which rings/timers/cards pin where}, household{size, defaultServings}}`. Three sources: shipped library, user-authored, imported files. Every edit creates `vN+1` with a diff; any version can be reverted.
  - **Preference quiz content:** allergies/exclusions (gluten, shellfish, fish, dairy, peanut, tree nut, soy, sesame, mustard — Mealime's list, extensible), disliked ingredients via the 119-item swipe gallery plus free text, household size (1–6+), cooking frequency (never → daily), cooking skill, budget band. Results write the `PreferenceProfile`, which immediately re-filters F03's recipe space and F04's defaults.
  - **Milestone auto-breakdown:** goal split into trend-based milestones (Happy Scale) — by default every ~25 % of the journey or every 3–5 kg, whichever yields 4–8 rungs; each rung gets a projected date *range* from the same F07 band math, re-projected as data arrives.
  - **Fresh Start:** sets `hiddenBefore = date`. F02/F06/F07 keep all data queryable for long-range statistics; today surfaces, headline charts, and active streaks restart clean, while badges and earned milestones are retained (R-B7). The marker itself is visible and reversible.
  - **Import (with F13):** generic CSV/JSON parsing fully offline in v1; MFP/Lose It!/Paprika/Mealime converters land in v1.x (R-S4). Maps foods, weigh-ins and (where present) measurements into F02/F06; produces a report — per-category counts, skipped rows with reasons, unmapped foods listed for one-by-one or bulk handling.
  - **AI touchpoint (the only one) [v1.x — v1 ships the deterministic constraint→field applier only, per R-S10]:** optional "refine my template" — the user states constraints in natural language ("no cooking Wednesdays, ~120 g protein, hate cottage cheese") and a cloud LLM proposes structured template edits. Requires the F12 **`meal-planning`** consent toggle (one of F12's six frozen categories; F12 lists F01 as a consumer for exactly this) ON with a BYOK key configured; the consent surface appears at the point of use with a preview of exactly what would be sent. **On-device fallback:** a deterministic constraint→field applier covers common phrasings with no network; if both are unavailable, the Studio remains fully manual. Proposals are drafts — apply/discard, never auto-commit (confirm-before-write grammar).
- **Outputs / artifacts:** `Goal` record; `DietPlan vN` versions; `Milestone[]` with projections; `PreferenceProfile`; `FreshStartMarker`; import report.
- **State owned:** goals, plan versions and diffs, preference profile, milestone definitions, fresh-start markers, onboarding completion state.
- **Weight-only goal creation:** a profile may have no Targets document. The
  Goals editor can explicitly create v1 through the existing F01 Studio writer
  without running the diet wizard: DAILY cadence, null calorie budget unless
  the user enters one, the registry floor with no override acknowledgment and
  the balanced macro-schema default. Saved weight-first intent is only an
  editable prefill. Drafts retain raw text and profile/base-version identity;
  conflicts never overwrite, and a small operation journal reconciles the
  immutable Targets version with its safety metadata after interruption.

## 4. User Interaction Model

- **Entry points:** optional continuation after weight-first setup; Settings → Plan Studio; F10 nudge "your targets haven't been reviewed in 30 days"; F07 check-in deep link "adjust plan" (opens the Studio pre-filled with the proposal as a pending diff); post-import CTA.
- **Happy path:** Weight first run completes → optionally open Plan Studio → goal details → forecast bands → template gallery → swipe quiz → schedule bars → milestones → Start.
- **Primary flows, step by step:**
  - *Goal revision:* Studio → Goal card → dials → bands re-bloom → "Save as vN+1" → diff summary card → F07/F10 informed.
  - *Template editing:* Studio → any card (macros, rules, schedule, surfaces) → edit inline → version badge ticks up → diff ribbon at top shows the delta since last commit.
  - *Fresh Start:* Settings → Fresh Start → date wheel → page-turn confirmation → history shaded (not gone) → streak-freeze note ("your F11 badges and milestones are kept; your streak starts clean today" — R-B7).
  - *Import:* first-launch branch "I'm coming from another app" → pick source → pick file (system picker) → parse → report → "Bring it in" → goal suggestions derived from imported history.
  - *Template authoring (Studio):* "New template" (blank or duplicate-an-existing) → edit inline (macro sliders, rules chips, schedule bars, surface toggles, timing windows) → name it (chips or text) → saved as v1 → optional "share as file" (signed JSON). Authoring is a first-class flow, not a settings detour — the same editor the user met in onboarding, no separate power-user mode.
  - *Cloud refinement [v1.x — R-S10]:* Studio → "Refine with AI" → typed or spoken constraints → F12 point-of-use consent sheet (`meal-planning`) with payload preview → proposal rendered as a pending diff → Apply (new version) or Discard.
- **Fallback paths:** (a) *Skip Plan Studio* — exit without creating a plan;
  the weight loop remains complete and no values are invented. (b) *Unsafe goal*
  — the shared [weight-goal safety contract](../research/weight-goal-safety-contract.md)
  holds unsupported targets/dates and a pace demanding sub-floor calories is
  blocked with a counter-proposal; raw tracking remains available. This is
  product policy, not medical advice. (c)
  *Cloud refinement declined/offline* — the deterministic applier runs or the
  offer simply does not appear; nothing stalls.
- **Input minimization — never typed:** weight/height via steppers and sliders with round-number snap; dislikes via card swipes from the gallery (free text only as last resort); household size as chips; cooking frequency as 4 chips; activity as one picture-card; dates via wheels. Zero fields are mandatory; every field shows its provenance chip (`measured` / `estimated`). Explicitly typed-once-and-reused items: none in the happy path — the only keyboard moments in all of F01 are optional free-text dislikes and the optional AI-refinement prompt.
- **Micro-interactions:**
  - Goal dials: rotary haptic ticks per kg; magnetic snap + soft "thock" at 5 kg multiples; the delta ("−12 kg") counts up beside the dial.
  - Forecast: three bands **bloom outward** over 600 ms (optimistic first, pessimistic last) so the eye reads range, not point; the middle band carries a slow shimmer that says "living estimate"; an `ESTIMATED` chip types itself in with a caret blink.
  - Template cards: spring scale on press (0.94×), selection ripple in the template's accent color; the card that changes tracker surfaces (e.g. keto) shows its surface icons re-arranging on the card face.
  - Preference quiz: swipe-left flings the card with a 10 ms double-tick; swipe-right lands with a single snap; the pile counter is a tiny rolling odometer; a "why we ask" long-press explains the rule it drives ("no olives → never suggested in plans or lists").
  - Schedule bars: dragging a weekend bar up animates the weekday siblings' compensation in real time — the pinned weekly total is the visible invariant.
  - Milestones: each rung drops into place with 80 ms stagger and a rising three-note pentatonic motif (< 0.5 s total); completed rungs get a stitched underline.
  - Fresh Start: a page-turn shade dims history in place — the opacity metaphor communicates "hidden", never "gone"; a small "12 months of data preserved" footnote reinforces it.
  - Import: a count-up receipt-tally animation per category (foods, weigh-ins, measurements) as rows map; unmapped rows slide into a side tray rather than blocking.
- **Data-quality gating:** the forecast preview carries a visible `ESTIMATED` chip until F07 has ≥ 14 days of real data; milestone projections are hidden entirely (not grayed) until a weight trend exists; the Studio shows "computed from formula, not yet from your data" on every derived number; import never guesses nutrition for unmapped foods — it imports them as placeholders with `unknown` provenance.

## 5. What the User Gets Out

- The **Targets card** (daily/weekly budget, macro splits, per-day schedule bars) — the object F02 renders as the daily ring and F10 quotes in nudges.
- **Milestone ladder:** named, dated as a range, each rung with "you are here" progress and its own mini-forecast; rungs renameable ("Size 34 jeans").
- **Forecast chart:** raw goal curve + optimistic/expected/pessimistic bands (F07 engine), re-rendered in the Studio whenever plan or data changes; tap any band → its assumptions.
- **Plan version history:** list of `vN` with human-readable diffs ("keto v3 → v4: carbs 30 g → 50 g, weekend +400 kcal") and one-tap revert.
- **Import report:** what came in, what didn't, and why — the migration receipt.
- **Provenance rule:** every target and projection carries `measured` / `estimated` provenance and a "How we got here" panel (formula used, inputs, why it will change) — Happy Scale's explainer applied to the plan itself.
- **Visualizations owned by F01:** the 7-bar weekday/weekend schedule chart; the milestone ladder; the plan-version timeline. The long-horizon forecast chart is F07's; F01 embeds read-only renders.

## 6. Motivation & Psychology

- **First-win design:** the forecast is the first dopamine hit — within one minute the user sees a plausible future self, honestly hedged. This replaces the industry's "enter your email to see your plan" bait with the real thing, free, before asking anything. Verification intent: a first-session success is a user who *shows the forecast to someone else* — it should survive being screenshotted (synthesis §5, card-as-artifact).
- **Agency over automation:** the engine proposes, the user disposes — from the first wizard to F07's weekly check-in, no number changes without a visible human commit. This is the psychological contract that makes later adaptation trustworthy rather than creepy.
- Fresh Start's psychology: a relapse is a data point, not a verdict. Copy is strictly forward-looking ("New page, same book"); nothing in the restart flow summarizes "what went wrong".
- Milestones convert a distant goal into a ladder of winnable games — the proven antidote to demotivation (Happy Scale reviewers cite goal breakdowns as their favorite feature). Each completion emits a **local hook event** F11 may turn into a badge; F01 owns milestone *definitions*, F11 owns the *system*.
- Tone rules: no guilt, no shame, no "you failed your target" language anywhere; goal-setting copy never moralizes food choices; the calorie-floor refusal frames safety as respect ("we don't do crash math"), not scolding; skipped onboarding steps are framed as "later", never "incomplete".

## 7. Relations to Other Features

- **Consumes from:** **F06** (current weight for the goal dials; trend once available); **F07** (forecast engine for the 3-band preview; measured TDEE to replace formula estimates; check-in proposals rendered as pending Studio diffs); **F13** (MFP/Lose It!/CSV import parsers, Health Connect prefill); **F12** (`meal-planning` consent gate for optional cloud refinement); **F02** (logged-history stats to power Diet DNA comparisons).
- **Feeds into:** **F02** (daily calorie/macro targets, meal-timing windows — the budget it renders); **F03** (diet template + preference profile = generation rules; household size → default servings); **F07** (the targets it adapts; Apply-commit writes a new plan version here); **F04** (household defaults, budget band); **F10** (IF timer surfaces, plan-freshness nudges); **F11** (milestone-completion hook events, streak-reset exemptions after Fresh Start); **F13** (plan/template JSON in export bundles).
- **Shared concepts:** Targets, Provenance, Consent, Fresh Start marker, versioned artifacts.
- **Conflict/boundary notes:** F01 owns *what the targets are* (definitions, versions, studio UI); F07 owns *how they evolve* (proposals, cadence math, adaptation). The ratified Targets schema — fields, write API, day projection — lives in FEATURES.md Appendix A; F01's Studio is one of its exactly two writers. F01 never computes a forecast itself — it always calls the F07 engine. F11 owns all gamification mechanics; F01 only emits events and displays what F11 grants.

## 8. Blue Sky Ideas

- **[v1] Plan-as-document with diffs.** Every target change — user edit or F07 check-in apply — is a version with a readable diff. "Show me what changed since I started" becomes a trust-building timeline. No competitor versions their plan at all.
- **[v1] Honest 3-band forecast at goal-set time.** Optimistic/expected/pessimistic from minute one, explicitly wide when data is absent ("this band is wide because we know nothing about you yet — good"). Directly attacks the category-wide linear-extrapolation lie (synthesis §1.5).
- **[v1] Calorie-floor wall with counter-proposal.** The unsafe-pace refusal is itself a designed moment: the slider hits a physical haptic wall and the app *negotiates* instead of obeying.
- **[v1.x] Template sharing without a server.** Signed `DietPlan` JSON files, shareable via QR code / file / any link; the importer verifies the signature and shows a plain-language diff against the current plan before applying. Community templates circulate as *files* — the open-source answer to Yazio's content team.
- **[v1.x] Diet DNA.** With imported history, the Studio overlays the proposed template on the user's real past eating ("your median day was 2,150 kcal / 74 g protein — this plan asks −12 % / +38 %"), making the change concrete instead of aspirational.
- **[v1.x] Preference quiz as a re-runnable instrument.** Re-run the swipe deck anytime to re-filter F03's recipe space, with a "what changed" summary and an immediate plan re-fit preview. Mealime's preferences were a config file; WLO's are a living instrument.
- **[v1.x] Onboarding replay.** A "start over, keep data" flow that rebuilds the plan with the knowledge the app has now (measured TDEE, real eating patterns) — onboarding as a periodic re-calibration, not a one-time gauntlet.
- **[future] Multi-phase plans.** Cut → maintenance → recomposition sequences defined up front, with F07-signaled automatic phase-transition proposals. The plan becomes a season arc, not a single sprint.
- **[future] Household plan harmonization.** Two profiles, one dinner: the Studio solves for a shared evening meal that fits both members' budgets (couples are Happy Scale's multi-profile audience — WLO makes profiles cooperate).
- **[moonshot] Plan simulator sandbox.** A what-if Monte Carlo over the user's own logged variance ("0.75 %/week pace, 3 restaurant meals/week, your actual missed-log rate → band"), rendered as draggable slider stacks with the forecast updating live. Pure on-device math over local data — the ultimate numbers-geek toy.

## 9. Guardrails, Privacy & Sensitivity

- **No account is ever created, requested, or useful.** Onboarding completes fully offline; if the device has never had network, nothing degrades except the optional cloud refinement (F12 `meal-planning` toggle + BYOK key, with a pre-send payload preview).
- Goal weight, body stats and the plan are sensitive health data: protected by the app lock (biometric/PIN, F13), stored in encrypted local storage, exported only through F13's explicit export action.
- The **calorie floor is non-negotiable in code**: no plan version, import, AI proposal, or blue-sky feature may produce targets below it; imported plans with unsafe historical targets are imported as *history*, never as the active plan.
- Goal UI consumes the shared WLO-0080 eligibility result. It never assumes an
  unanswered safety question means “no,” and it never produces a confident
  target, milestone, or date for a held/unsupported result.
- Fresh Start hides; it never deletes. Deletion of history exists only as a separate, explicit, per-category destructive action with confirmation.
- AI proposals (template refinement) are drafts: apply/discard, never auto-commit; nothing is logged about the exchange — only the consented request itself leaves the device.
- Tone audit rule: any string scoring the user against the plan must pass the "no verdict" test — deltas are described, never judged.
- Hard rendering rule: any derived number rendered without its provenance chip is a spec bug; the Studio's preview surfaces and F10's target cards inherit this requirement from F01.

## 10. Open Questions

- **Quiz length vs. depth:** the full Mealime-style pass (diet + allergies + 119 dislikes + household + cooking) is ~40 swipes; ship an 8-card core in v1 with the long tail behind "more options", or place the long tail inside F03 where its effect is visible? *(Resolved: R-S6 — 8-card core in v1; the long tail lives in the Studio and F03 filters.)*
- **Forecast preview ownership:** F01 renders, F07 computes — confirm F07 ships a callable "zero-data forecast" mode in v1; if not, F01 needs a stub (wide bands from population variance) clearly labeled as such. *(Resolved: R-A5 — F07's cold-start forecast mode is mandatory in v1.)*
- **Import scope for v1:** MFP + Lose It! CSV are documented formats; do we also accept Paprika/Mealime recipe exports at launch (higher effort, bigger "your data outlives the app" wedge) or defer to v1.x? *(Resolved: R-S4 — generic CSV/JSON at v1; MFP/Lose It!/Paprika/Mealime converters at v1.x.)*
- **IF plans in v1:** the Yazio pattern suggests fasting windows bundle naturally with templates; confirm F10's timer surface ships early enough to make the IF template honest.
- **Weekly vs daily cadence default:** weekly pairs with F07's check-in ritual (recommended); confirm with the F07 spec that daily cadence remains supported rather than dropped. *(Resolved: R-S6 — weekly default, daily supported.)*
- **Plan Studio activation metric:** plan written + first applicable food log
  within 24 h. This is not the first-run completion metric; first run completes
  on arrival at Weight.
