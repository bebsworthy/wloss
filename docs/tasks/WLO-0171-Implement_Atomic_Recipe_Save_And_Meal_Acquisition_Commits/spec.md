# Implement atomic recipe save and meal acquisition commits

## Source of truth

- [Functional specification](../../features/F03-recipe-creation.md).
- [Technical specification](../../tech/WLO-0167-recipe-implementation.md).
- [Accepted mockup](../../design/flows/12-recipe-creation.html), visual direction only; use native M3 and current DESIGN-SYSTEM/IA.
- WLO-0167 tracks the completed specification. This ticket tracks implementation and remains TODO until picked up.

## Outcome

Save recipe drafts and selected meal items in one idempotent transaction using the existing agenda and F02 ownership, with explicit portions and conflict recovery.

## Dependencies and ownership

Blocked by WLO-0157, WLO-0160, WLO-0169. WLO-0157 owns the existing ingredient/content/nutrition foundation; WLO-0160 owns catalog normalization and revision semantics. Build on their delivered primitives without duplicating or reversing those dependencies.

## Requirements

- Implement CommitRecipeAcquisition and operation receipt lookup with profile/operation IDs, immutable command hash, draft/base versions, selected items, typed portions, explicit destination and target/context fingerprints.
- Flush and freeze draft/calculation inputs, then revalidate inside one Room transaction. Write recipe versions, metadata/search updates, all selected meal items and commit receipt together; retain the draft and roll back everything on failure.
- Return the existing receipt on retry of the same command; reject changed payload under the same operation ID. Recover after process death before/after commit without duplicate recipes/items. Resolve notes/recipe/destination conflicts without clobbering user content.
- Integrate Save recipe versus Save & add with the existing agenda item store and immutable nutrition/unit snapshots. Preserve sibling items/order; explicit replacement affects only the named versioned target. Support the multi-item command now for later whole-meal generation.
- Refresh changed context/fit before the same destination-aware explicit action. Unsupported portion basis holds add while library save remains usable; incomplete nutrients remain null with coverage provenance.
- Respect the parked Eaten/Mark eaten release boundary: do not enable it here. If the existing destination is later enabled, writes go through F02 in the shared transaction, not a new diary store.
- Carry commit receipts, draft completion and all snapshot/reference mappings through vault restore so restored drafts cannot duplicate restored items.

## Acceptance and evidence

FR-01, FR-05–FR-07; AC-01, AC-04, AC-06, AC-07, AC-11; technical §§3–6, 11; T-02, T-05, T-07, T-09.

- Inject failures after recipe write and after the first of several item writes; verify full rollback. Test double taps, concurrent edits, same-ID/different-hash retries and process death on both sides of commit.
- Test unknown/fractional portions, existing meal siblings, changed targets/destination, library-only commit, multi-item fixtures, profile isolation and offline restore/idempotency.
- Include this slice's migrations, serialization/restore and focused tests in the implementation; they are not postponed to final integration QA. Record commands and material limitations.

## Scope boundary

Does not implement AI generation, URL extraction, a competing agenda store, shopping or parked Mark eaten UI.

Frozen feature/release/consent rulings remain authoritative. WLO-0159 source-ranking/carbohydrate questions stay there; do not fabricate answers or expand network permissions to make this ticket pass.
