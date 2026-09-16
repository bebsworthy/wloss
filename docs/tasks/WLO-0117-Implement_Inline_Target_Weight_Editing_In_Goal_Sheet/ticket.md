---
id: WLO-0117
title: Implement inline target-weight editing in goal sheet
status: done
theme:
release:
created: 2026-09-16T21:38:13Z
modified: 2026-09-16T21:51:07Z
closed: 2026-09-16T21:51:07Z
revision: 9306c846f9bc0383
blocks: []
related: []
---

# Description

# Inline weight goal — WLO-0117

Implemented the approved one-field sheet in the weight overview and routed Settings > Goals to the same interaction. Native M3 sheet/outlined field/button; no nested card, milestones, mode, pace/date, calorie-plan or screening UI. Existing plan editor source remains for future planning work, but the Goals navigation no longer opens it. Settings description updated accordingly.

Target-only edits use the existing Studio writer, preserve every other field in an existing Targets document, and append an immutable version. New reference targets request no pace/date/calorie budget. FEATURES records the owner-authorized separation of reference targets from automated recommendations. Forecast safety checks remain unchanged. Read errors do not masquerade as missing goals; conflicts and failures retain input. Numeric parsing accepts decimal comma and units convert at the boundary. Duplicate saves are guarded. Closing/back/scrim discards unsaved changes. Saving does not change calorie intake or infer screening answers.

Verification:
- Full build, lintDebug, ktlintCheck, detekt and architecture checks passed in /tmp/wlo117-verified.log, with 92 tests (45 weight, 24 design-system, 23 app).
- Subsequent Settings copy and persisted-unit race fix passed build, ktlint, detekt and 46 weight tests in /tmp/wlo117-unit-verified.log. Total current relevant tests: 93.
- New tests cover target-only plan preservation and base version, unchanged no-write, dismissal/reopen, conflict retention, first-reference defaults, invalid input, pounds conversion, and delayed settings initialization.
- Device: edit with numeric keyboard; dismiss/reopen restores old value; save 75.5 and reopen persists it; restore original 76.0 through the UI; Settings entry and Done return; 200% text; pounds on immediate Settings entry. Original 76 kg goal and kg display restored. QA created two legitimate version-history entries for test change/restoration; no data cleared.
- Initial 200% screenshot exposed the M3 partially-expanded anchor clipping the action. This sheet now skips that anchor; large-text-verified.png shows the full action above system controls.
- Unit QA exposed initial navigation reading default kg before saved lb loaded. openGoal now awaits the persisted preference; delayed-flow regression test and pounds-verified.png verify the correction.
- Full TalkBack interaction was not exercised. Native components, heading/pane semantics and polite comparison announcements are present.

Visual evidence:
- sheet-verified.png, keyboard.png, saved-reopened.png, large-text-verified.png, settings-entry.png, pounds-verified.png.
- comparison/ is the initial glyph-anchor experiment; input borders/floating labels make that anchor unsuitable. Do not use its translated overlay for layout conclusions.
- screen-aligned/ compares at equal viewport size without arbitrary translation. Underlying page is schematic in the goal mockup, so compare the sheet only. Standard M3 floating label, native system navigation and field metrics deliberately differ from HTML; the app uses the same content gutters/type hierarchy and compact composition.
- final/ captures the final installed normal-scale sheet. matching-data.js adjusts browser memory to the device's existing 76 kg target only.
