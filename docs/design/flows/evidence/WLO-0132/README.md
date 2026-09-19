# WLO-0132 — direct editing and add-many sheet

Browser verification on 18 September 2026; illustrative Today is 21 September.
`add-many-sheet.png` captures the persistent bottom sheet at 390 × 844. Done remains
sticky while results scroll. After this capture, the sticky subtitle was also
updated to show the current eaten/planned choice while its toggle scrolls away.

Verified: adding yogurt, banana and milk consecutively keeps the sheet open and
records 285 kcal as eaten without a second save. Eaten/Planned choice applies to
subsequent additions; Escape dismisses without undoing saved items. Mark eaten
updates a seven-item lunch immediately; Undo restores its planned state. Editing
bread from 2 to 3 directly in the day changes 1,300 to 1,390 kcal. Tomorrow's
addition stays planned and exposes no mark-eaten action. Browser errors empty;
JavaScript syntax and diff whitespace checks pass. Temporary viewport reset.

Native dialog supplies modal focus behavior; labelled quantity fields, remove
buttons, persistent Done, status feedback and Undo support direct edits. No real
food database, AI call, Android code or actual diary data changed.
