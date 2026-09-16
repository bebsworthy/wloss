---
id: WLO-0115
title: Review goal drawer and editor UX against weight overview
status: done
theme:
release:
created: 2026-09-16T21:02:47Z
modified: 2026-09-16T21:04:46Z
closed: 2026-09-16T21:04:46Z
revision: f23ce2748dcd9d43
blocks: []
related: []
---

# Description

# WLO-0115 — Goal progress and editor review

16 September 2026. Scope: supplied screenshots plus repository implementation. Review and proposed contract only; no production changes. Forecast destination is deferred. This review describes application logic, not a medical assessment of its models.

## Finding

These surfaces still use the previous card-first design. More fundamentally, they mix the user's target, planning preferences, eligibility screening and a model prediction without explaining their dependencies. Typography alone cannot fix that.

## What the implementation actually does

- `WeightGoalPreview.evaluate` converts a requested date into an average percentage pace: absolute target/current difference ÷ current weight × 100 ÷ weeks remaining. When a valid future date exists, this implied pace takes precedence over the editable pace field. Save persists that effective pace. The visible pace can therefore differ from the value used and saved.
- Requested pace is an input to eligibility checks. It does not directly set the forecast trajectory or calculate the daily calorie budget. At 77.1 kg, 0.5% means approximately 0.386 kg/week at the current weight. This is an interpretation of the field, not a recommended rate or prediction.
- The forecast uses target weight, current trend, planned calorie intake and expenditure. Pace can change whether a forecast is permitted without changing its path when both paces remain eligible. A requested date is advisory, not the predicted arrival date.
- The date button is disabled until forecast eligibility passes. Its recovery explanation is farther down the form. Unknown screening answers can withhold dates; the screenshot alone does not establish that they are the only unmet condition.
- Initial expenditure comes from `ForecastEngine.coldStart`: Mifflin–St Jeor resting expenditure × profile activity multiplier. Inputs include current weight, age, height, sex and activity level. The implementation uses a midpoint sex coefficient when sex is missing/other; this assumption belongs in the source explanation.
- When data quality allows Updating, the editor derives expenditure using `EnergyEngine.measuredTdee` over its trailing 14-day slice: average logged intake minus trend weight change converted to daily energy using the engine's 7,700 kcal/kg approximation. This is inferred total daily expenditure, not a directly measured exercise burn. Developing uses the formula; Held does not generate a fresh reliable date.
- The editor passes this estimate to `WloForecastCard`, but does not supply its optional `onExplain` callback. The provenance word alone cannot explain the source, assumptions, input period or value. The supplied screenshots do not show a burn value, so this review does not verify a particular numerical baseline.
- Forecast preview follows all screening controls inside the editor's outer card; the reusable forecast is itself a card. Withheld renders no chart there. Distinguish a chart buried below the fold from one intentionally withheld by policy.

Sources: `feature/f01-onboarding/.../domain/WeightGoalPreview.kt`, `state/GoalsEditorViewModel.kt`, `ui/GoalsEditorScreen.kt`; `feature/f06-weight/.../state/GoalProgressLoader.kt`, `ui/WeightScreen.kt`; `core/engines/.../ForecastEngine.kt`, `EnergyEngine.kt`; `core/designsystem/.../WloForecastCard.kt`, `ProvenanceChip.kt`.

## Drawer defects and proposed replacement

1. “Goal progress” → “Goal” → “Target · loss” repeats context three times. A large outlined card inside an already contained sheet adds another border and another inset.
2. The target is a muted trailing value while the redundant heading dominates. Current trend and remaining distance are absent even though the loader already supplies them.
3. “Next 76.0 kg” does not explain that this is an intermediate milestone. The label and value are disconnected across the row.
4. “Date unavailable” plus “Dates are hidden by the goal safety gate” repeats failure, uses internal policy terminology, and offers no specific recovery.
5. “Progress still counts” is reassurance without information. Show the actual remaining distance instead.
6. The outlined Edit goal action looks inactive in the screenshot, despite being enabled by this branch of the code. It needs clear actionable emphasis.
7. Milestones is an expandable section disguised as an unspecified text action. Show its expansion state and purpose.

Proposed sheet, using the supplied data:

    Your goal
    74.0 kg                 [goal color]
    Current trend 77.1 kg · 3.1 kg to goal

    Next milestone                         76.0 kg
    View milestones                              ›

    [Specific forecast status only if useful]
    [Review required details — if recoverable]

    Edit goal                               [filled button]

No outer card; shared page gutters and Inter roles. Do not invent a percentage-complete bar without a defined journey starting point. Maintenance shows the supported range and relationship to it, not a finish line. Gain uses the same neutral hierarchy. Missing goal gets Set goal; read failure gets Retry; missing trend explains the exact missing prerequisite. If dates are unavailable, keep the milestone weight and avoid a repeated placeholder under every milestone.

## Goal editor proposal

Use a page, titled “Edit goal”, with short sections separated by space rather than a giant border. Reuse the weight page's 24dp content alignment and established Inter roles, adapting labels to standard M3 fields instead of shrinking their touch geometry.

1. **Weight goal:** compact current-trend context, native Loss / Maintain / Gain selection, target field (or maintenance treatment consistent with policy).
2. **Planning preferences:** remove the current unexplained “Pace and date” pair. Recommended long-term model is a weight target first, with optional planning below. Until the domain contract changes, expose requested pace honestly as a planning preference, with its kg/lb-per-week equivalent and “Used to check this goal; the forecast uses your calorie plan.” Do not pretend it drives the curve.
3. **Optional calorie plan:** “Daily calorie target”, with a clear distinction between food intake and estimated expenditure. A read-only row “Estimated daily expenditure” names its current source: “From your profile” or “From your food logs and weight trend”. A source-details action shows exact inputs/window, update date, assumptions and edit-profile route. No unexplained “baseline”; no invented burn when inputs are absent.
4. **Forecast setup:** collapse previously completed screening into an editable summary. If incomplete and blocking the requested operation, present a specific setup row with the missing information and a route to answer it. Keep the existing policy and genuine unanswered state; never infer No or bury a blocker silently.
5. **Prediction:** retain a clear distinction between an estimated arrival range and an optional desired date. Final graph placement is deferred. Wherever it lives, show its availability state and source clearly.
6. **Save goal:** reserved M3 filled action with keyboard/system-bar clearance. “Save as new version” becomes “Save goal”; version history remains available under “Goal history”, using human-readable changes rather than writer wire names. Preserve drafts, discard handling and versioned storage.

### Pace/date decision

Recommended direction: do not ask every weight-tracking user for both a pace and a date. If date planning is retained, use an explicit choice between requested pace and desired date; show the derived counterpart read-only, and explain that desired date is not a forecast. A future pace-led calorie planner must explicitly calculate and preview the corresponding calorie target; never silently overwrite a manually chosen budget.

Removing pace as a required goal input changes current safety/model/Targets contracts. It is a product decision and coordinated implementation, not a field deletion. FEATURES currently states targetDate is advisory and rendered as implied pace. This review proposes a clearer interaction without silently amending that ruling or bypassing eligibility.

## Implementation and verification contract

- Extract shared weight-surface typography roles for reuse by F01 and F06 without feature-to-feature imports; retain native M3 Scaffold, TopAppBar, ModalBottomSheet, fields, ListItems, segmented controls and Buttons.
- Preserve structured reasons and dependency details through GoalProgressUi; do not flatten all Withheld states into one sentence. Render the recovery corresponding to the actual reason. Sensitive screening detail need not be exposed on the routine overview.
- Use one shared expenditure explanation model for editor and progress/forecast: value, formula-derived/log-derived source, inputs, coverage and timestamp. Expose a functioning explanation route.
- Verify a target date visibly overrides/replaces pace before saving; the visible effective value must match the persisted one. Test clearing/changing date, future/past dates, decimal locales, kg/lb and all three goal modes.
- Demonstrate that eligible pace edits currently leave the energy-driven curve unchanged; either explain that contract or deliberately replace it with a specified planner. Keep requested, implied and forecast pace distinctly named.
- Test missing age/height/intake, incomplete screening, policy-withheld, Developing, Updating, Held and read failures. Do not advertise a recovery that cannot enable the feature.
- Drawer shows canonical current trend, target and correct remaining distance. No duplicated date-unavailable copy or internal safety-gate language.
- Save remains versioned, prevents double submission, preserves draft on errors, and provides a conflict recovery route.
- Validate at the same viewport as the weight page with the agent comparison tool. Include 200% text, narrow width, keyboard, TalkBack focus order, modal focus restoration, non-color selection semantics and contrast checks. No claim of accessibility approval from screenshots alone.
- Preserve forecast eligibility rules and current navigation until a separate placement decision. No inferred medical policy changes.

## Review score (current surfaces, 0 missing / 1 partial / 2 ready)

Task clarity 1; hierarchy 0; component semantics 1; token consistency with weight page 1; adaptive behavior unverified; states/recovery 0; accessibility unverified; visual restraint 0. Not ready for design acceptance. Proposed contract requires implementation and device verification before scoring as ready.
