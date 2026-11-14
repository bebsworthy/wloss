# Verify integrated recipe acquisition, recovery and portability

## Source of truth

- [Functional specification](../../features/F03-recipe-creation.md).
- [Technical specification](../../tech/WLO-0167-recipe-implementation.md).
- [Accepted mockup](../../design/flows/12-recipe-creation.html), visual direction only; use native M3 and current DESIGN-SYSTEM/IA.
- WLO-0167 tracks the completed specification. This ticket tracks implementation and remains TODO until picked up.

## Outcome

Complete the cross-feature native acceptance evidence for the implemented recipe increments, focusing on end-to-end recovery, privacy, history and portability rather than deferring each slice's own tests.

## Dependencies and ownership

Blocked by WLO-0157, WLO-0160, WLO-0168, WLO-0169, WLO-0170, WLO-0171, WLO-0172, WLO-0173, WLO-0174, WLO-0175, WLO-0176. WLO-0157 owns the existing ingredient/content/nutrition foundation; WLO-0160 owns catalog normalization and revision semantics. Build on their delivered primitives without duplicating or reversing those dependencies.

## Requirements

- Assemble the functional AC-01–AC-12 traceability matrix against implemented slices and their existing unit/Room/parser/model/device evidence. Every slice must already carry its own migration and integrity tests; this task owns combined-system validation and closes concrete integration defects.
- Exercise real native paths across manual creation, shared/website import, AI one dish/whole meal, correction, save/add, library reuse/variation, public Share and cooking. Include incomplete recipes and already-populated meals.
- Test process recreation across the full chain, interrupted acquisition/commit, catalog and recipe edits, migrated legacy recipes, offline backup/restore/merge and retry after restoration. Historical nutrition and committed identities must remain stable.
- Verify feature gates/Pure mode and consent authorization at actual dispatch boundaries; record no unintended website/model/subresource egress or private content in share/diagnostics.
- Measure specified search/calculation/checkpoint/commit/restore budgets on a declared representative device. Validate 360 dp, landscape/supporting pane, 200% text, TalkBack, IME and all non-gesture alternatives.
- Generate mockup-versus-native evidence with tools/screenshot-compare/agent.py as required by AGENTS.md. Publish commands/results, screenshots/UI trees, measured performance, coverage/known limits and source/model evaluations under docs/tech/WLO-0167-evidence/.
- Do not treat passing acceptance as approval to lift R-S3/R-S10 or website-egress gates. Record the actual enabled release scope and remaining explicit decisions.

## Acceptance and evidence

AC-01–AC-12; technical §§12–15; T-01–T-10; all functional FR-01–FR-09.

- All AC rows have linked passing evidence for their enabled increment; any excluded release path is explicitly gated rather than falsely marked verified.
- No unresolved data-loss, duplicate-commit, history-rewrite, unauthorized-egress or accessibility-blocking defect in the delivered scope. Verify dependency ticket completion and document bounded limitations.
- Include this slice's migrations, serialization/restore and focused tests in the implementation; they are not postponed to final integration QA. Record commands and material limitations.

## Scope boundary

No redesign or scope expansion into timers, counted sub-recipes, pantry/shopping, converters or parked Mark eaten. Do not repeat passing checks without an integration risk/failure that justifies it.

Frozen feature/release/consent rulings remain authoritative. WLO-0159 source-ranking/carbohydrate questions stay there; do not fabricate answers or expand network permissions to make this ticket pass.
