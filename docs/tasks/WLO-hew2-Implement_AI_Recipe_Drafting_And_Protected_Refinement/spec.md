# Implement AI recipe drafting and protected refinement

## Source of truth

- [Functional specification](../../features/F03-recipe-creation.md).
- [Technical specification](../../tech/WLO-kpry-recipe-implementation.md).
- [Accepted mockup](../../design/flows/12-recipe-creation.html), visual direction only; use native M3 and current DESIGN-SYSTEM/IA.
- WLO-kpry tracks the completed specification. This ticket tracks implementation and remains TODO until picked up.

## Outcome

Implement one-dish recipe requests and revision-aware refinement through F12, with capable local execution, explicit BYOK consent, deterministic validation and ingredient-based nutrition.

## Dependencies and ownership

Blocked by WLO-w0vs, WLO-e6yf, WLO-12dt, WLO-rxcf, WLO-083v. WLO-w0vs owns the existing ingredient/content/nutrition foundation; WLO-e6yf owns catalog normalization and revision semantics. Build on their delivered primitives without duplicating or reversing those dependencies.

## Requirements

- Wire RecipeGenerationPort to F12 local/provider adapters and the durable draft lifecycle. Own the recipe-specific capability/output adapter and model evaluation; missing runtime support must be implemented within the existing ports/module boundaries or reported as a concrete gate, never hidden with fixtures.
- Provide one-dish request constraints and one coherent editable proposal. Consume persisted dietary rules from WLO-083v, optional target/coverage and narrowly scoped existing meal context. Withhold detected hard-exclusion conflicts without silently relaxing them; unknown data does not establish allergen safety.
- Prefer a validated capable installed local model. Preserve input and offer disclosed saved-recipe/import/manual/setup alternatives if unavailable. Cloud requires meal-planning consent, BYOK, the actual payload preview and receipts; LAN off-device execution is also egress. Do not switch to cloud automatically.
- Validate bounded schema-v1 output before candidate persistence. Resolve food suggestions through WLO-w0vs/WLO-e6yf; never accept model-provided macro totals, fabricated local IDs or automatic commit actions.
- Implement Another idea and refinement as revision-aware proposals with field-level diff, protected user corrections, conflict choices and compatible undo. Notes/destination persist; late/stale/cancelled/revoked output cannot apply.
- Keep partial streamed output non-saveable; restart retains input and prior complete draft with explicit retry. Serialize AI provenance and durable candidate state while excluding grants/keys/unvalidated output from backups.
- Keep R-S3/R-S10 gating intact until the appropriate release. Whole meal remains disabled until its separate ticket passes.

## Acceptance and evidence

FR-02, FR-04, FR-06, FR-07; AC-01, AC-06, AC-10; technical §§4–6, 8–9, 11, 13; T-02, T-04, T-05, T-08, T-09.

- Exercise invalid/oversize schema, malformed quantities, hard conflicts, incomplete nutrition, stale results and concurrent user edits, undo after later edits, cancellation/revocation at request/response boundaries and no unauthorized calls.
- Evaluate each enabled model with the versioned prompt corpus (at least 50) from technical §13; report completion, unresolved food, constraints, latency/memory and actual capability limits. Verify one-dish save/add through the atomic coordinator.
- Include this slice's migrations, serialization/restore and focused tests in the implementation; they are not postponed to final integration QA. Record commands and material limitations.

## Scope boundary

Whole-meal orchestration, week planning, image/voice recipes and new consent categories are excluded. This ticket does not resolve WLO-10av catalog policy by assumption.

Frozen feature/release/consent rulings remain authoritative. WLO-10av source-ranking/carbohydrate questions stay there; do not fabricate answers or expand network permissions to make this ticket pass.
