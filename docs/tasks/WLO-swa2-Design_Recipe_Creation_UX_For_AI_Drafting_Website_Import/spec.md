# Recipe creation and reuse — accepted UX contract

Canonical document: docs/research/recipe-creation-ux.md, retained under research at the owner's request (WLO-vh78). The former design path remains a forwarding link. WLO-5hvz applies the accepted Paprika research recommendations from WLO-4f0d.

## Interaction contract

- AI drafting, website/text import and manual creation converge on one editable recipe while preserving calling context and provenance.
- Explicitly save a named recipe with ingredient text even if yield, quantities or nutrient matches are unresolved. Keep draft lifecycle distinct from saved-content completeness; interrupted AI output is not a completed recipe.
- Collection emphasizes ingredients/instructions; meal acquisition emphasizes portion/nutrition. Same editor and data, with stable hierarchy during editing.
- Multiline ingredient entry/paste and structured rows preserve original wording, sections, stable IDs and explicit corrections. Progressive matching cannot discard unknown lines.
- Recipe yield, cooking batch and personal portion remain distinct. Missing basis holds only operations requiring it; never invent serving counts or complete totals.
- Local name/ingredient/source search, recent/favorites and personal notes support repeat use without required categories. Variations preserve the original and historical meal versions.
- Stable navigational recipe references contribute no nutrition. Explicit counted components require child revisions, compatible yield, cycle rejection and no double-counting; recursive calculation is a separate extension.
- Basic cooking follows acquisition/retrieval: foreground keep-awake, reversible ingredient/step state and recoverable local sessions. Multiple dishes/timers follow later; per-step macro callouts are excluded from the basic scope.
- Standard M3 components, accessible state/focus, explicit commits, recovery and current consent rules apply throughout.

## Scope and delivery

WLO-8ah2/R-S3 import/drafting deferral, R-S10 cloud scope and current WLO-yt93 delivery remain unchanged. F03 and FEATURES.md link the accepted future contract. WLO-w0vs's calculation/persistence requirements are aligned; WLO-e6yf's catalog foundation remains unchanged. This is specification work, not application or prototype implementation.

The canonical document includes acquisition, reuse, recovery, cooking and component-extension acceptance scenarios. One-proposal AI presentation and One dish/Whole meal controls remain prototype choices to validate; accepted Paprika-driven behavior is no longer an unapproved recommendation.
