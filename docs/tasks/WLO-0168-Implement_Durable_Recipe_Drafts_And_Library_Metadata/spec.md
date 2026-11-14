# Implement durable recipe drafts and library metadata

## Source of truth

- [Functional specification](../../features/F03-recipe-creation.md).
- [Technical specification](../../tech/WLO-0167-recipe-implementation.md).
- [Accepted mockup](../../design/flows/12-recipe-creation.html), visual direction only; use native M3 and current DESIGN-SYSTEM/IA.
- WLO-0167 tracks the completed specification. This ticket tracks implementation and remains TODO until picked up.

## Outcome

Add the durable acquisition-draft and mutable library-metadata infrastructure on top of WLO-0157 recipe content and WLO-0160 food revisions, including recovery and vault support.

## Dependencies and ownership

Blocked by WLO-0157, WLO-0160. WLO-0157 owns the existing ingredient/content/nutrition foundation; WLO-0160 owns catalog normalization and revision semantics. Build on their delivered primitives without duplicating or reversing those dependencies.

## Requirements

- Extend the existing recipe authority with profile-scoped metadata (favorites, notes, current version, saved time, archive state), durable multiple drafts, stable acquisition operation/job identities and version-aware repository APIs. Reuse the content/yield/food snapshot codecs supplied by WLO-0157 rather than creating a second model or nutrient engine.
- Implement serialized draft mutations with expected draftRevision, protected user fields, bounded structural/refinement undo and stale-result rejection. Persist input before acquisition and complete candidates before reporting them recoverable. Preserve the acknowledged draft through process recreation; never resume external jobs automatically.
- Support independent drafts for new acquisition/Share, source input, originating destination and navigation anchors. Exclude drafts from the saved library. Explicit discard/undo must not affect saved recipes or other drafts.
- Make notes/favorites separate from immutable recipe content and historical nutrition. Enforce profile ownership and compare-and-set updates; stale editing must not overwrite newer notes or a favorite toggle.
- Own Room/vault migrations for these new records, offline restore, draft/metadata reference remapping and job/grant clearing on restore. Existing recipe-content/nutrition migrations remain with WLO-0157; extend rather than duplicate them.

## Acceptance and evidence

FR-01, FR-04, FR-07, FR-08; technical §§2–3, 5–6, 11–12; T-01, T-04, T-07.

- Kill/recreate after acknowledged edits and after candidate persistence; the correct draft, original text and destination return. Storage failure never reports a successful checkpoint.
- Verify two simultaneous drafts, stale parser/refinement results, revision conflicts, undo after later edits, profile isolation, metadata versus content history, and old/new backup round-trips.
- Include this slice's migrations, serialization/restore and focused tests in the implementation; they are not postponed to final integration QA. Record commands and material limitations.

## Scope boundary

Native editor/library screens, ingredient math, network/model adapters and cooking are delivered by downstream tickets.

Frozen feature/release/consent rulings remain authoritative. WLO-0159 source-ranking/carbohydrate questions stay there; do not fabricate answers or expand network permissions to make this ticket pass.
