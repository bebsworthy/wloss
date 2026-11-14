# Implement recipe library, reuse, variations and sharing

## Source of truth

- [Functional specification](../../features/F03-recipe-creation.md).
- [Technical specification](../../tech/WLO-kpry-recipe-implementation.md).
- [Accepted mockup](../../design/flows/12-recipe-creation.html), visual direction only; use native M3 and current DESIGN-SYSTEM/IA.
- WLO-kpry tracks the completed specification. This ticket tracks implementation and remains TODO until picked up.

## Outcome

Add the Plan recipe library and details with search, favorites, notes, variations, stable links, archive and portable recipe sharing.

## Dependencies and ownership

Blocked by WLO-w0vs, WLO-e6yf, WLO-12dt. WLO-w0vs owns the existing ingredient/content/nutrition foundation; WLO-e6yf owns catalog normalization and revision semantics. Build on their delivered primitives without duplicating or reversing those dependencies.

## Requirements

- Add the accepted Plan toolbar library entry and nested library/details routes without changing the current daily agenda or four navigation destinations. Show saved recipes separately from one/multiple retained drafts.
- Implement local profile-scoped AND-term search across names, source and all ingredient wording including unresolved text, combined with favorites. Page results; preserve query/filter/scroll; distinguish empty library, empty favorites and no matches. Recent uses content saved time, not meal usage.
- Expose details, favorite, personal notes, Edit, Make a variation, Archive/undo and the supported portion/destination return contract. Existing recipe reuse does not repeat acquisition review. Meal writes are delegated to the atomic integration ticket.
- Variation gets a distinct identity with ancestry/attribution; Edit creates a revision. Stable navigation links survive rename and have no nutrient contribution; archived/missing targets are explicit. Do not enable counted-component math.
- Deliver readable recipe text/Markdown and versioned self-contained JSON sharing through SAF/Android Share. Preview optional notes (excluded by default); omit private prompts, diary, keys and unrelated content. Preserve selected recipe food/conversion evidence offline.
- Complete search-index/metadata/export migrations and merge remapping for this slice; no blind ID overwrite or source-license loss.

## Acceptance and evidence

FR-01, FR-07, FR-08; AC-03, AC-05, AC-07, AC-08, AC-12; technical §§3, 9, 11–12; T-01, T-04, T-07, T-09.

- Verify ingredient/source multi-term search, retained query, favorites on incomplete content, notes across edits, variations versus revisions, stable links, archive recovery and profile isolation.
- Verify export/share privacy, offline round-trip without catalog packs, merge collisions and historical stability. Measure first-page search on the specified 5,000-recipe corpus and validate native accessibility.
- Include this slice's migrations, serialization/restore and focused tests in the implementation; they are not postponed to final integration QA. Record commands and material limitations.

## Scope boundary

Network import, AI, meal transaction ownership, cooking, categories/ratings/taxonomy and recursive recipe components are not added here.

Frozen feature/release/consent rulings remain authoritative. WLO-10av source-ranking/carbohydrate questions stay there; do not fabricate answers or expand network permissions to make this ticket pass.
