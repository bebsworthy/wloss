# Proposal: goal → daily food intake target

Status: design proposal for owner review, not an approved spec amendment. Research-only; existing implementation was not inspected. Governing decisions remain FEATURES.md §2.0 and §3, and the weight-goal safety contract. This proposes a smaller entry flow than F01's historical full Plan Studio wizard.

## Intent and research basis

Answer: “Given my weight goal, what daily intake should I start with, and why?” Deliver a saved, editable intake target with visible origin and an effective date. Remain optional to weight tracking. No account, network or AI required.

Repository evidence:
- docs/research/macrofactor.md, Onboarding and weekly check-in: suggested versus manual control, formula starting point followed by intake/trend-derived estimates, adherence-neutral review.
- docs/research/zolt.md, energy engine and takeaways: developing/updating/held states, auditable calculation, Apply/Keep, no exercise-calorie credit.
- docs/research/loseit.md, Onboarding and takeaways: concise goal-to-budget journey, optional weekday/weekend schedule preserving weekly total.
- docs/research/cronometer.md, nutrition targets: nutrient customization as depth, not a prerequisite to an energy target.
- docs/research/synthesis.md §1.1, §1.5, §4–5: editable artifacts, explainability, no eat-back exercise credit.
These are the project's collected research findings, not a fresh verification of competitor products.

## Proposed journey

Weight goal → Set food intake → Starting estimate → Review target → optional customization → Save → target summary. Manual entry branches at the start and rejoins review.

### Entry: contextual continuation

After saving a goal show a quiet “Set food intake” action with “Choose a daily target to support this goal.” Keep a persistent entry beside the goal. Dismissal leaves weight tracking complete and no intake target. Never launch the wizard automatically after a weigh-in.

### 1. Starting point

Title: “Set your daily intake.” Show saved goal and pace with Edit. Two radio options: “Estimate from my goal” (default) and “Enter my own target.” Continue.

Estimated path: reuse known profile facts and answered eligibility questions. Collect only missing age, height, formula sex input, usual activity and required eligibility answers. Explain the sex input as an equation parameter and activity with everyday movement examples. Do not silently select unknown answers. Allow leaving with draft preserved and no active target. Weight alone cannot identify energy expenditure.

Use a qualifying intake/trend-derived expenditure estimate when available; otherwise use the documented initial formula and activity assumption. Label both as estimates, with distinct sources; never call derived expenditure directly measured. Valid formula estimation is possible before food logs exist, but never fabricate missing formula inputs.

Manual path: enter kcal/day, identify source as “Set by you,” reuse applicable eligibility and floor checks. Do not require irrelevant formula inputs or fabricate a pace/date when expenditure is unknown. Manual entry is not a bypass of WLO safety policy.

### 2. Review: the main decision screen

Title: “Your daily intake.” Hero: proposed kcal/day plus source label. Below: existing goal and pace; estimated maintenance energy; deficit for loss / no planned offset for maintenance / surplus for gain; resulting intake. Labels distinguish chosen pace from predicted outcome. Use the direction-specific governed engine.

“How we got here” opens a sheet with actual inputs, formula/method, activity assumption, date/window and reason for uncertainty. Show the actual weight input and its source/date. If relevant assumptions change, recalculate the draft visibly.

Actions: Edit pace (updates calorie proposal) or Enter my own target (explicit manual mode). Do not let independently editable pace and calories contradict one another. Review any goal changes alongside intake before committing; cancelling preserves active goal and targets.

Forecast is supporting information, never the gate to an otherwise valid intake target. Render only the state's allowed output: provisional outer range when permitted, no invented point date, or a reason for held forecast. An absent forecast must not imply that target validation succeeded or failed; evaluate each correctly.

Illustrative layout values only: maintenance estimate 2,400 kcal/day; planned deficit 400; daily intake 2,000. These are display examples, not individual nutrition recommendations or default prescriptions.

### 3. Optional customization, within review

Collapsed rows, no required extra screens:
- Nutrient targets: show the named editable balanced preset, grams and percentages for protein/carbohydrate/fat, and the single shared fiber target. Offer other presets/custom editing here. Make their origin explicit; template values are not personalized findings. Validate energy reconciliation, including rounding, before save. Any wider optional-nutrient schema change needs separate approval.
- Daily schedule: default same daily target; optional weekday/weekend distribution. Show seven-day amounts and weekly total. Preserve the total while reallocating and show changes before commit. Validate every day's floor, not only the average. Use ordinary numeric inputs/controls; a chart is a preview, not the only editing mechanism. No automatic compensation for yesterday's food logs.

Allergies, ingredient dislikes, cooking skills, household size, fasting and recipe selection belong to later meal-planning tasks. A calories-only interaction may accept a disclosed template default without forcing macro editing. It must not silently claim a custom macro prescription.

### 4. Save and return

Primary action “Save intake target.” Review names start date (default today, editable), kcal/day or schedule, nutrient preset/changes, goal/pace changes, and origin. Save a versioned target; historical intake and historical target comparisons stay unchanged. Return to invoking context showing today's intake target, source and Edit. Offer food logging only when the capability ships; do not advertise a working adaptive loop before the required intake capture exists.

Cancel retains a draft without activating it; back navigates within setup. Validation and save errors keep inputs with a concrete retry/correction action. Reopening after save shows the active values. Stale version conflicts require review, never overwrite newer targets.

## Later weekly loop

Once sufficient food intake and weight coverage exists, review current versus proposed intake, supporting evidence and its uncertainty. Apply / Keep / Why this change. Weak data holds a new proposal with a specific reason; incomplete food days never count as zero intake. Do not promise convergence on a fixed date. New estimates do not silently change saved targets. Exercise does not earn extra intake. Every applied change is versioned and reversible.

## Required exceptions

Missing inputs: ask for the specific missing fact or retain unset target. Unanswered or unsupported eligibility: use the shared policy's held state, keep raw tracking available. Unsupported pace or below-floor target: explain and offer a supported counterproposal requiring selection, never silently clamp/save. Manual input retains the same constraints. Gain/maintenance use their own goal semantics. No target is rendered as zero.

## UI handoff

Use standard Compose Material 3 Scaffold/TopAppBar, ListItem, RadioButton, numeric OutlinedTextField, Button/TextButton, and ModalBottomSheet for explanations. Reuse DESIGN-SYSTEM.md semantic colors/typography and visible source/quality text. One primary action per page; scrollable content with keyboard-safe actions. Compact layout is sequential; wider windows may show the target preview beside inputs while preserving reading order.

All numeric editing works without dragging; explicit units and locale-aware input; labeled errors next to fields; logical TalkBack/focus order; large text reflows; state never relies on color; standard accessible touch areas; reduced motion respected. Motion may explain recalculation but never be the only evidence of change.

## Review boundary

This ticket completes a proposal, not feature implementation or production UI verification. Acceptance: research provenance, entry and happy path, manual path, optional customization, save semantics, held/unsupported behavior, and future adaptation clearly described. If accepted, reconcile F01's older wizard and any stale F07 wording with this flow and the authoritative safety contract before implementation.


## Owner correction — 2026-09-18
The weight goal no longer asks for pace. This supersedes the proposal's references to reusing an existing chosen pace and editing that pace. Intake setup must start from current weight, target weight, profile and goal direction; it must not invent a user-selected rate. Present estimated maintenance and an editable starting calorie target. The updated mockup uses an explicitly illustrative 2,000 kcal fixture, not a newly approved starting-deficit algorithm. Production policy for choosing that initial suggestion remains to be defined.
