# Weight goal sheet — WLO-0116

Owner-refined concept. Open goals.html at 470 × 1045 presentation size, matching the weight mockup. One editable sheet replaces the separate goal editor.

- Target weight input with explicit kg unit.
- Current trend and live above/below-target comparison; exact equality reads At target. No maintenance tolerance or automatic calorie changes implied.
- Done when unchanged, Save goal when edited. Save updates browser memory only; reload resets the illustrative 74 kg goal.
- Close, Escape or outside tap discards unsaved changes. Tap the background goal or external Open goal sheet to reopen.
- Empty, non-numeric and nonpositive inputs show an error and disable save. Decimal comma accepted. This is prototype validation, not the full production domain policy.

Removed: separate editor, milestones, direction selector, pace/date planning, calorie plan and forecast screening. Those larger features must not be inferred from a target-weight editor. Goal history remains a potential secondary production route, not a fabricated history in this focused prototype. Production separation of reference targets and forecast eligibility requires coordinated domain/policy work; this mockup does not amend existing rules.

Uses bundled Inter and the established dark/goal/primary colors. Production should use M3 ModalBottomSheet, OutlinedTextField and Button; HTML is an approximation. Keyboard focus loops inside the sheet, Escape dismisses, and validation/comparison semantics are supplied. Native keyboard insets, TalkBack and 200% text remain implementation acceptance work.

Verified with headless Chromium: rendered progress.png visually inspected; editing.png captured; save, discard, reopen and invalid-input controls pass; no script errors. Retired editor.png and expenditure.png to avoid stale comparisons.

Concept self-audit: task clarity 2; hierarchy 2; component semantics 1; tokens 2; adaptive 1; states 1; accessibility 1; restraint 2. Ready for concept review, not native release approval.
