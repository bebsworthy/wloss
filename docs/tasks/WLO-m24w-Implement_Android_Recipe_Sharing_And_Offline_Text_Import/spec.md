# Implement Android recipe sharing and offline text import

## Source of truth

- [Functional specification](../../features/F03-recipe-creation.md).
- [Technical specification](../../tech/WLO-kpry-recipe-implementation.md).
- [Accepted mockup](../../design/flows/12-recipe-creation.html), visual direction only; use native M3 and current DESIGN-SYSTEM/IA.
- WLO-kpry tracks the completed specification. This ticket tracks implementation and remains TODO until picked up.

## Outcome

Receive shared recipe URLs/text into independent durable drafts and parse pasted recipe content locally with lossless fallback to the shared editor.

## Dependencies and ownership

Blocked by WLO-w0vs, WLO-e6yf, WLO-12dt. WLO-w0vs owns the existing ingredient/content/nutrition foundation; WLO-e6yf owns catalog normalization and revision semantics. Build on their delivered primitives without duplicating or reversing those dependencies.

## Requirements

- Wire app-owned Android text/URL Share handling through app lock and active-profile validation. Handle initial and subsequent intent delivery; persist before navigation, deduplicate repeated delivery by operation token, and preserve separate deliberate new shares.
- A shared/pasted URL creates retained input only. No network request occurs on receiving, typing or opening a draft. No automatic clipboard inspection or executable/remote-file scheme handling.
- Implement bounded local pasted-text block classification and raw-text retention, with title/ingredients/sections/instruction/notes suggestions. Unassigned or uncertain blocks remain accessible with explicit assignment controls; do not discard directions or turn them into nutrient rows.
- Reuse editor ingredient parsing and quantity normalization for decimal commas, fractions, ranges, alternatives and unknown units. Preserve original text and manual corrections. Over-limit/unsupported input reports recovery without silent truncation.
- Expose Retry/edit/manual/paste recovery and retain the acquisition stage through process recreation. Keep model parsing and page fetching out of this local slice.
- Include Share draft/source/unassigned-block data and intent/job recovery state in vault serialization; grants and active jobs are never restored.

## Acceptance and evidence

FR-01, FR-03, FR-04; AC-02, AC-03, AC-06, AC-10; technical §§5, 7.1, 7.3, 9, 11; T-03, T-04, T-06, T-07, T-09.

- Exercise new intent while another draft is open, locked/profile-switch entry, repeated delivery, long/malformed text and URL-only retention with zero network calls.
- Verify ingredient-only and mixed recipe text, ambiguous sections, decimal/fraction inputs, unassigned content preservation, process restart and backup/restore into the same editor.
- Include this slice's migrations, serialization/restore and focused tests in the implementation; they are not postponed to final integration QA. Record commands and material limitations.

## Scope boundary

Website fetch/structured-page extraction and AI parsing are separate. Import release deferral remains; offline functionality must not quietly enable deferred acquisition entry points.

Frozen feature/release/consent rulings remain authoritative. WLO-10av source-ranking/carbohydrate questions stay there; do not fabricate answers or expand network permissions to make this ticket pass.
