# Implement authorized website recipe import and refresh

## Source of truth

- [Functional specification](../../features/F03-recipe-creation.md).
- [Technical specification](../../tech/WLO-kpry-recipe-implementation.md).
- [Accepted mockup](../../design/flows/12-recipe-creation.html), visual direction only; use native M3 and current DESIGN-SYSTEM/IA.
- WLO-kpry tracks the completed specification. This ticket tracks implementation and remains TODO until picked up.

## Outcome

Add bounded, explicitly authorized page retrieval, structured Recipe extraction, duplicate detection and protected refresh into the shared draft editor.

## Dependencies and ownership

Blocked by WLO-w0vs, WLO-e6yf, WLO-m24w, WLO-8x4r, WLO-rxcf. WLO-w0vs owns the existing ingredient/content/nutrition foundation; WLO-e6yf owns catalog normalization and revision semantics. Build on their delivered primitives without duplicating or reversing those dependencies.

## Requirements

- Implement the proposed one-import non-AI egress policy and dispatcher-owned URL/redirect enforcement from technical §7.1 behind deny-by-default gating. Before enabling, obtain/record the master-contract approval and reconcile FEATURES/F12/F13; R-C4 food lookup is not authorization and this ticket does not create a seventh AI category.
- Enforce HTTPS, actual-host/DNS/connect validation, unsafe local/private destination rejection, operation-bound grants, redirect authorization, byte/decompression/time/parse limits and cancellation. Fetch no trackers/images/scripts/credentials or recursive resources; emit minimized receipts and clean temporary page bytes.
- Parse the specified bounded JSON-LD Recipe subset in objects/arrays/@graph, including candidate selection, ingredient lists, HowToStep/Section, typed/text yield, time and source attribution. Unsupported forms/sign-in/blocked sites retain input and offer paste/open/edit.
- Retain original source fields and separate website nutrition claims from ingredient calculations. Unmatched food, unknown amount and yield do not prevent saving valid content.
- Implement conservative URL/candidate/content-hash duplicate suggestions. Open saved, variation and reviewed refresh are explicit. Three-way refresh preserves manual edits/notes and creates a new version only on Save.
- Persist source/parser provenance and any new identifiers through vault migration/remapping; do not archive whole pages permanently or recalculate historical meals.

## Acceptance and evidence

FR-03, FR-04, FR-07, FR-08; AC-02, AC-06, AC-10; technical §§5–7, 11; T-03, T-04, T-06, T-07, T-09.

- Run the authored structured-page corpus including multiple recipes, absent fields, hostile markup, malformed JSON, unsupported content and all limits; verify no lost unparsed source data.
- Verify zero egress without the correct grant, no OFF-grant reuse, cancellation/revocation/expiry/restart, cross-origin redirects, private-address/DNS cases, receipt minimization and cache cleanup.
- Verify duplicates/refresh with overlapping user corrections, unknown nutrition save, atomic meal addition and offline restore; record supported/unsupported site behavior honestly.
- Include this slice's migrations, serialization/restore and focused tests in the implementation; they are not postponed to final integration QA. Record commands and material limitations.

## Scope boundary

No access-control bypass, WebView scripting, arbitrary site scrapers, recursive fetches, AI fallback, image import or universal-site promise. R-S3 release gate remains.

Frozen feature/release/consent rulings remain authoritative. WLO-10av source-ranking/carbohydrate questions stay there; do not fabricate answers or expand network permissions to make this ticket pass.
