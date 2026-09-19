# WLO-0131 — multiple items per meal

Captured in the Codex browser on 18 September 2026. Illustrative Today remains 21 September. Food and nutrition values are fixtures, not a live food database.

- `seven-item-lunch-mobile.png`: full-page capture with a 390 × 844 viewport. Daily agenda with seven independently editable lunch items; content scrolls.
- `review-multi-item-log.png`: food-log review with the dish, starter, yogurt, banana, bread, wine and milk. Save/Cancel remain separate from item edits.

Verified through browser interactions:

- Bread quantity 2 → 3 updates the meal from 1,300 → 1,390 kcal and preserves six siblings.
- Removing the 90 kcal starter in logging review and cancelling retains all seven planned items. Repeating and saving logs six items at 1,300 kcal.
- Adding milk to a previously logged three-item lunch retains the three items and saves four at 905 kcal.
- Unknown-calorie soup plus a 620 kcal recipe shows 620 known, total incomplete. Replacing soup with 80 kcal yogurt preserves the recipe and produces 700 kcal.
- An empty future dinner suggestion adds recipe + starter separately (710 kcal), without a Log action.
- Multi-day suggestions produce multi-item meals and preserve existing entries.
- Browser error log empty; JavaScript syntax and diff whitespace checks pass.

Same shared tokens, semantic buttons and labelled fields as Flow 11. Item targets are at least 48 px, with visible focus and textual planned/logged states. The viewport override was reset after inspection. This remains HTML UX work; no Android code or real diary data was changed.
