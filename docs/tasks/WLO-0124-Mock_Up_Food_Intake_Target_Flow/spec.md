Build a local interactive HTML phone-frame mockup of WLO-0123 using WLO dark tokens and Material 3 component semantics. Show start, intake review, and saved state together. Implement manual entry, formula explanation, nutrient and schedule editing, save and reset. Sample profile is explicitly illustrative and already eligible; no production nutrition engine. Verify browser rendering and interactions, add to flow index, and open for owner review.


Completed: docs/design/flows/10-intake-target-f01.html, flow-index entry, and evidence/10-intake-target.png. Browser checks passed for explanation, manual-entry floor validation, saving, schedule redistribution, macro editing and narrow viewport. No JavaScript errors. Screenshot visually reviewed. Local preview opened in Codex. Sample already-eligible adult profile; mockup only, not the production engine.


Owner correction applied: removed chosen-pace summary, pace editor, pace state and pace-derived target calculation. Goal context now shows weight-loss direction only. Starting suggestion is explicitly illustrative and editable, with formula provenance restricted to the maintenance estimate. Browser checks passed for no pace entry, explanation, manual edit and save; screenshot refreshed. Production starting-deficit policy remains outside this mockup.


Revision 3: replaced three-screen storyboard with a single intake editor. Calorie amount edits directly; explanation, macros/fiber and schedule expand inline. Save returns to the goal view with a compact intake row and brief confirmation. Back cancels draft; reopening restores saved settings. Removed method selection and receipt screen. Verified validation, save, reopen, cancel, weekend schedule and mobile overflow in browser; no JS errors. Refreshed and visually reviewed screenshot.


Revision 4: estimated daily maintenance is always visible above the editable calorie target, with plain-language meaning and formula provenance. The live difference is visible below the target; detailed calculation remains expandable. Browser verified reading order, 2,100 kcal → 285 kcal difference and saving; screenshot refreshed and inspected.


Revision 5: maintenance → editable positive deficit → computed intake. Added direct-intake toggle preserving the same calculation. Example now uses the proposed guideline-qualified 500 kcal deficit: 2,385 − 500 = 1,885 kcal/day. Saves resulting intake, not an automatically floating deficit. Explanation cites guideline population and preserves Apply-only future changes. Browser verified initial amount, live deficit, both toggle directions, save/reopen and below-floor rejection with no JavaScript errors. Screenshot refreshed and visually reviewed. This interactive example covers loss; gain/maintenance behavior is documented for handoff, not implemented in this sample.


Revision 6: added native range slider paired with exact numeric deficit entry. Drag/keyboard use 25 kcal steps; range maximum respects the example intake floor and weekday redistribution. Direct intake mode hides the deficit slider. Verified keyboard steps, exact-value synchronization, linked intake, upper bound, mode toggle and save in browser; no JS errors. Refreshed screenshot visually inspected. Compose handoff specifies standard M3 Slider.


Revision 7 — explicit owner direction: manual calorie values must not be blocked by safety floors or goal direction; zero deficit is valid. Replaced deficit-only slider with signed adjustment centered on zero (negative deficit, positive surplus); symmetric range expands for typed values. Added external objective preview selecting −500 loss / 0 maintenance / +300 gain. Removed endpoint values/instruction copy, duplicate arithmetic and start-date reassurance. Tested zero, positive surplus and below-floor save/reopen, direct entry, objective defaults and keyboard adjustment; no JS errors. Screenshot reviewed. This records owner-authorized mockup behavior; production safety-contract reconciliation is a separate implementation change.


Revision 8: inspected WeightScreen.kt summary row, WeightOverviewTypography.kt, WloTheme.kt and the WLO-0103 Inter overview screenshot. Applied the weight summary's equal-width columns and right-aligned goal pattern to top-level maintenance/intake. Intake uses exact dark chartGoal #F4D35E; maintenance stays neutral. Moved live intake above adjustment and removed its old placement. Verified column alignment, exact color, zero/gain calculations, saving and narrow viewport; no JS errors. Screenshot refreshed and visually reviewed. This is a layout/token reuse, not a pixel-match claim.


Revision 9: added “With this intake” projection between paired maintenance/intake stats and adjustment slider. Gold goal line, widening illustrative band and example goal window respond to intake. Added maintenance/no-date, moving-away, unavailable intake and missing-profile states; manual Save remains available when projection is unavailable. External data-state selector supports review. Browser checks passed for all states, live redraw, gain and unrestricted save; corrected SVG hidden handling found by verification. Screenshot refreshed and visually inspected. Projection geometry and ranges are explicitly illustrative, uncalibrated and not the production forecast engine.


Revision 10: replaced “Set intake directly” link with a capsule Adjustment / Intake selector immediately above the numeric input. Radio semantics and visible selected check follow single-choice segmented control behavior; Compose handoff names SingleChoiceSegmentedButtonRow. Mode changes preserve the underlying intake and projection. Verified both conversions, keyboard switching and save; explicit accessible labels prevent selected check glyph from changing control names. Screenshot refreshed and visually reviewed.


Revision 11: slider remains visible in Adjustment and Intake modes. Intake translates the scale to total kcal/day with maintenance at center; adjustment remains centered on zero. Switching preserves thumb position, exact numeric amount and forecast. Verified visible intake slider, 25 kcal keyboard increment, reverse conversion and saved amount; no JS errors. Refreshed screenshots for both modes and inspected intake mode.
