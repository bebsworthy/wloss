# Recipe acquisition, reuse and cooking — implementation handoff

## Canonical deliverables

- Functional specification: [docs/features/F03-recipe-creation.md](../../features/F03-recipe-creation.md).
- Technical specification: [docs/tech/WLO-kpry-recipe-implementation.md](../../tech/WLO-kpry-recipe-implementation.md).
- Accepted visual direction: [WLO-nmc5 mockup](../../design/flows/12-recipe-creation.html).
- Accepted UX rationale: [recipe creation and reuse](../../research/recipe-creation-ux.md).

The functional document defines FR-01–FR-09 and AC-01–AC-12. The technical document defines the current-code gap analysis, module ownership, content/yield/food-revision models, calculation and unknown propagation, durable draft lifecycle, transaction/idempotency protocol, bounded URL/text parser, AI schema/refinement/consent, native UI, cooking state, Room/vault migration, search/performance, tests T-01–T-10 and delivery slices A–F.

## Integrity and scope

Save incomplete recipe content without inventing yield/nutrition. Every supported meal portion uses a known basis; calculations use pinned food/conversion observations. Preserve source wording, manual edits, recipe versions and previous meal snapshots. Recipe-plus-meal commit is atomic and idempotent. Drafts and cooking sessions recover locally. Full backup restores recipes and their evidence without catalogs/network; outward sharing excludes private context.

WLO-w0vs owns ingredient-backed nutrition; WLO-e6yf owns source catalogs and normalization. WLO-10av's source-ranking/carbohydrate questions remain open. R-S3/R-S10 release deferrals and parked Mark eaten are unchanged. Website retrieval needs the explicitly approved non-AI egress policy described in the technical spec before enabling. Cooking is a separate follow-on; counted components, timers, multiple dishes and shopping UI remain later extensions.

## Completion for this specification task

Both canonical documents exist; functional requirements map to technical tests and scoped delivery slices; F03, FEATURES, F12, F13 and UX forwarding docs point to the handoff; existing dependency tickets link to it. Verify local links, structure, identifiers and whitespace. This task completes specification only, not runtime implementation.

## Specification verification — 21 September 2026

Completed both canonical documents and reconciled F03, FEATURES, F12, F13 and UX forwarding references. Verified 71 local documentation links across the eight affected documentation files, the required functional-template sections, FR-01–FR-09, AC-01–AC-12 and T-01–T-10 identifiers. Git whitespace validation passed. Inspected current models/repositories, Room schema 11, backup schema 2 and network purposes; checked primary Schema.org Recipe and Android Share/screen-awake documentation. No runtime code was changed or application tests claimed. Linked WLO-w0vs/WLO-e6yf consumer contracts without broadening their implementation scopes.

### Implementation tickets — owner sequencing, 21 September 2026

All ten implementation tickets below are **TODO** and have explicit **blocked-by** links to both existing TODO prerequisites: **WLO-w0vs** (ingredient-linked recipe nutrition) and **WLO-e6yf** (food catalogs/normalization). Additional dependencies are shown below. This is the requested execution order; the existing nutrition tickets remain the foundation and are not duplicated. All tickets link back to WLO-kpry and include scoped requirements, acceptance criteria, migration/portability obligations and test IDs.

| Ticket | Implementation scope | Additional blockers |
| --- | --- | --- |
| [WLO-rsj8](../WLO-rsj8-Implement_Durable_Recipe_Drafts_And_Library_Metadata/spec.md) | Implement durable recipe drafts and library metadata | Nutrition prerequisites only |
| [WLO-12dt](../WLO-12dt-Implement_The_Shared_Recipe_Editor_And_Manual_Creation_Flow/spec.md) | Implement the shared recipe editor and manual creation flow | WLO-rsj8 |
| [WLO-8x4r](../WLO-8x4r-Implement_Recipe_Library_Reuse_Variations_And_Sharing/spec.md) | Implement recipe library, reuse, variations and sharing | WLO-12dt |
| [WLO-rxcf](../WLO-rxcf-Implement_Atomic_Recipe_Save_And_Meal_Acquisition_Commits/spec.md) | Implement atomic recipe save and meal acquisition commits | WLO-12dt |
| [WLO-m24w](../WLO-m24w-Implement_Android_Recipe_Sharing_And_Offline_Text_Import/spec.md) | Implement Android recipe sharing and offline text import | WLO-12dt |
| [WLO-x4k9](../WLO-x4k9-Implement_Authorized_Website_Recipe_Import_And_Refresh/spec.md) | Implement authorized website recipe import and refresh | WLO-8x4r, WLO-rxcf, WLO-m24w |
| [WLO-hew2](../WLO-hew2-Implement_AI_Recipe_Drafting_And_Protected_Refinement/spec.md) | Implement AI recipe drafting and protected refinement | WLO-083v, WLO-12dt, WLO-rxcf |
| [WLO-zf1c](../WLO-zf1c-Implement_AI_Whole_Meal_Proposals_And_Multi_Item_Acceptance/spec.md) | Implement AI whole-meal proposals and multi-item acceptance | WLO-rxcf, WLO-hew2 |
| [WLO-j72p](../WLO-j72p-Implement_Recoverable_Single_Recipe_Cooking_Sessions/spec.md) | Implement recoverable single-recipe cooking sessions | WLO-8x4r |
| [WLO-45gn](../WLO-45gn-Verify_Integrated_Recipe_Acquisition_Recovery_And/spec.md) | Verify integrated recipe acquisition, recovery and portability | WLO-rsj8, WLO-12dt, WLO-8x4r, WLO-rxcf, WLO-m24w, WLO-x4k9, WLO-hew2, WLO-zf1c, WLO-j72p |

WLO-hew2 additionally depends on WLO-083v's persisted dietary rules, so generated recipes use the same authoritative exclusions as existing suggestions. Each implementation ticket owns its focused tests and migrations. WLO-45gn owns combined-system evidence; it does not postpone those responsibilities. Website authorization and release/model capability gates remain as specified in §15.
