# Recipe creation and reuse — first mockup

WLO-0166 · 19 September 2026 · [Open prototype](../../12-recipe-creation.html)

This interactive study implements the first pass of the [recipe UX specification](../../../../research/recipe-creation-ux.md). Use the scenario buttons beside the phone, or enter through the agenda's meal add button. Browser storage preserves the sample recipes, draft and cooking session; **Reset sample data** starts again.

## Design baseline

The current WLO-0156 daily agenda and [flow 11](../../11-flexible-meal-planning.html) provide the baseline. The prototype reuses `_wlo.css` and `11-meal-planning.css`, local Inter fonts, dark/teal tokens, divider-separated rows and compact statistics. Recipes is proposed as a toolbar destination from Plan. The four existing navigation destinations remain visible on the agenda; nested screens hide that navigation.

HTML controls approximate native M3 Scaffold, TopAppBar, ListItem, OutlinedTextField, FilterChip, Button, ModalBottomSheet, Checkbox and Snackbar. This is a flow study, not a native pixel comparison or a Compose implementation. The desktop sidebars are review tools, not app navigation.

## Implemented paths

- Agenda → saved-recipe search, AI draft, website import or manual entry → shared editable review → save or save and add to the selected meal.
- Library → search by name, ingredient or source; favorites; recipe details; personal notes; a separate variation.
- Ingredient text ↔ rows, section headings, unresolved food/amount correction, missing yield, ingredient calculation explanation and portion changes.
- Leave and resume a draft, including after reload. Save incomplete recipes; hold portion-dependent actions when yield is unknown.
- Cooking checkmarks and current step persist independently of recipe edits. Finishing cooking does not log food.

## Verification

`verify.cjs` passed nine groups of browser checks; see [verification.json](verification.json). They cover keyboard focus within sheets and Escape, half-serving calculations, refinement and undo, incomplete import recovery, manual ingredient groups, draft recovery, cooking revision stability, distinct variations, four-serving fixture scaling, adding to breakfast without replacing existing food, narrow layouts and a library text-enlargement check. No JavaScript errors or external network requests were observed.

The prototype was visually inspected at 390 × 844 and 360 × 800. Screenshots include [library](library.png), [acquisition sheet](acquisition.png), [AI review](ai-review.png), [incomplete import](import-review.png), [ingredient correction](ingredient-correction.png), [manual entry](manual-mobile.png), [cooking](cooking.png) and [enlarged library text](library-large-text.png). [Workbench](workbench.png) shows the scenario navigation and design notes together.

To reproduce, serve the repository root on port 8766 (`python3 -m http.server 8766 --bind 127.0.0.1`), then run `node docs/design/flows/evidence/WLO-0166/verify.cjs` with Playwright available through `NODE_PATH` and its Chromium browser installed. The test creates its own fresh browser context.

## Limits of this pass

AI and website import use prepared examples. The AI example scales a fixed recipe to the requested yield and offers one substitution; it does not interpret arbitrary prompts. Pasted text becomes editable ingredient lines, with directions entered separately. Food matching uses a small illustrative vocabulary and unknown lines remain unresolved. There are no model calls, website fetches or writes to the Android app.

Recipe acquisition remains future product scope under the existing release rulings. Provider setup/consent, import quality, multi-recipe meals, counted sub-recipes, nested recipe links, timers and tablet app layouts are not explored here. Native keyboard/IME behavior, TalkBack, full-system font scaling and screen-awake reliability need validation when implemented in Android. The large-text browser check covers only the library; it is not accessibility certification.
