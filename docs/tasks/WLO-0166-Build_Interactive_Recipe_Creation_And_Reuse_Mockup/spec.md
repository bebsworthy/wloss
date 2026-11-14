# Interactive recipe mockup

Create an HTML/CSS/JS flow study from the accepted recipe UX contract, using the current WLO-0156 daily agenda and shared design tokens as the visual baseline. Inspect production theme/type and latest verified screenshots; preserve current prototypes.

First pass: agenda/library entry, AI dish request, fixture website import with unknown yield, manual text entry, shared contextual editor, ingredient correction, portion/save behavior, search/favorites, personal notes and basic persistent cooking state. Use local illustrative fixtures and no real model/network calls. Document extension boundaries and native M3 mapping outside the phone UI.

Deliver a linked prototype, screenshots and browser verification for core journeys, narrow layouts, keyboard dialogs, recovery and unknown nutrition. Keep runtime application code unchanged.

## Delivered first mockup

Interactive prototype: `docs/design/flows/12-recipe-creation.html`, with scoped CSS/JS and an entry in the flow index. Reuses the latest daily-agenda visual baseline and shared design tokens. Includes contextual acquisition/editor, ingredient correction and provenance, incomplete saves, search/favorites, notes, variations, draft recovery and persistent cooking progress.

Evidence: `docs/design/flows/evidence/WLO-0166/README.md`, screenshots, reproducible Playwright script and `verification.json`. Nine browser-check groups passed, including narrow layouts, modal keyboard access, unknown-yield gates, portion math, recipe/cooking revision separation and preserving existing meal food. JavaScript syntax and git whitespace checks passed. AI/import are explicit local fixtures, with no external requests. This is an HTML future-scope flow study, not a native implementation or release-scope expansion.
