# Offline food catalogs and nutrition normalization

## Objective and user outcome

Give WLO a traceable local food-nutrition catalog usable for food logging and ingredient-based recipe calculations. A user can search generic foods without a network, choose a specific food/preparation state, enter a compatible quantity, and inspect the source and calculation behind calories/macros. Barcode lookups add exact packaged products to a persistent local cache. Previously saved meals and recipes retain their original nutrition when sources update.

This is the implementation specification for WLO-0160, based on the completed source research WLO-0158 and design WLO-0159. The complete design is embedded below so this ticket is independently reviewable. Proposals explicitly marked as pending remain recommendations, not owner decisions.

## Scope and delivery boundary

Required in this feature:

- Reproducible acquisition and normalization of USDA Foundation Foods + SR Legacy, and Ciqual, into bundled offline generic-food catalogs.
- Common nutrient, measurement-basis, revision and provenance contracts across all three sources.
- Local catalog search and selection into profile-owned FoodItems, with source/preparation-state visibility and user corrections.
- Open Food Facts barcode integration using the same normalization contract, persistent caching, real throttling and honest cache/egress receipts.
- Kcal, protein, carbohydrate, fat, fibre, total sugars and sodium throughout the affected model/persistence/calculation/export paths; R-A4 already requires sugars and sodium. Additional source nutrients needed to explain methods may be retained without adding micronutrient panels.
- Migration, immutable historical snapshots, source notices, backup/restore, validation reports and focused automated/in-app verification.

WLO-0157 owns recipe-ingredient linking and recipe calculation UI. This ticket owns the catalog/normalization foundations that implementation consumes. Coordinate the shared schema changes; do not create competing revision or conversion models. Integration with WLO-0157 is part of end-to-end acceptance, without duplicating its implementation ownership.

Not required for this delivery: worldwide OFF corpus ingestion, downloadable regional OFF packs, runtime USDA API lookup, a WLO backend, accounts, cloud AI, recipe import/AI drafting, automatic multilingual translation, or new micronutrient panels. Optional-pack architecture below describes an extension boundary, not a required launch workflow. Do not add a release deadline or change the broader weight-first launch boundary through this ticket.

## Governing constraints

- FEATURES.md governs frozen decisions; respect R-A4 nutrients, R-B9 profile partitioning, R-C4 food-DB network toggle/cache/audit and F13 portability. R-C4 currently states default-on; this ticket does not change it or silently resolve conflicting wording elsewhere.
- Standard Material 3 components through documented slots/metrics; apply DESIGN-SYSTEM.md for touched UI.
- All personal data stays local. No upload of recipes, diary history, corrections or preferences to upstream databases.
- Weak/unresolved data is held with an explanation, never silently set to zero or replaced by a guess.
- Preserve the app's Apache-2.0 code licence and the separate licences/notices applicable to bundled or cached data.

## Owner decisions still open

Canonical questions remain on WLO-0159; do not duplicate them or treat this ticket's creation as answers:

1. q-000041: generic-food source ranking. Recommended: region-aware Ciqual/USDA ranking with explicit user selection; alternatives are one global preferred source.
2. q-000042: the primary carbohydrate convention for UI totals and targets. Recommended initial convention: excluding fibre, with clearly labelled derived estimates; alternatives are total including fibre or an explicit user-selectable convention.

Proceed on independent schema/adapters/fixtures. Do not finalize dependent ranking defaults or silently reinterpret old targets until answers are recorded. Source records always retain their original definitions regardless of the answers. Any final decision affecting cross-feature totals/targets must be reflected in FEATURES.md and affected specs before rollout.

## User flows

1. **Search and select:** opening the food picker searches the installed generic catalogs and profile-owned foods offline. Results identify food state and source; selecting a food materializes a profile-owned reference/revision. A label language change does not change food identity or nutrient convention.
2. **Quantity and explanation:** choose grams/ml or a supported named portion. Show the basis and conversion behind the result. If density/portion data is missing, offer a compatible unit or explicit correction and retain the unresolved state.
3. **Barcode:** local cache first. On a miss, use the existing R-C4-gated egress path. A hit shows source and as-sold/prepared state for user review before use. Disabled, offline, rate-limited and not-found states keep manual entry available; no unrelated generic food is auto-selected.
4. **Correct:** user corrections create a profile-owned override/revision with its own provenance. They do not edit source packs or transmit to upstream providers. Refresh never overwrites an override.
5. **Refresh:** an explicit refresh can show a changed source profile for adoption. Existing FoodItem selections, recipe versions and logged nutrition stay stable until the appropriate explicit update creates a new revision.
6. **Restore:** restore selected foods, recipes and logs with working nutrition explanations in airplane mode even when the original catalog pack is unavailable.

## Detailed technical specification

## Download and distribution

Use a reproducible developer-side pipeline, with three adapters and a common normalized schema:

```text
Pinned USDA downloads ── USDA adapter ───┐
Pinned Ciqual files ───── Ciqual adapter ├─ validation + provenance ─ source packs
OFF bulk export (later) ─ OFF adapter ───┘                          │
                                                     local catalog search
OFF barcode API ─ same OFF normalization ─ persistent cache ────────┘
                                                                  │
                                       selected FoodItem revision │
                                                                  ▼
                                            ingredient → recipe nutrition
```

| Source | Acquisition proposal | What Android receives |
| --- | --- | --- |
| USDA | Pin Foundation April 2026 and SR Legacy April 2018 downloads. Prefer CSV for production joins and audit metadata; JSON is useful for a first spike. Include supporting dictionaries required by older archives. | Compact generic-food pack, with source IDs and nutrient/portion definitions. No USDA API key needed. |
| Ciqual | Pin the 2025 release. Use XML food, component, composition and source tables; stream composition rows. Use bilingual names from published files and retain source codes. | Compact French/English generic-food pack. |
| Open Food Facts | Initially request exact barcodes, using a schema-versioned API adapter and persistent cache. Later, if justified by measured demand/size, stream a dated bulk JSONL export into regional packs on the build machine. | Cached products selected by the user. Optional packs are a later proposal, not a launch dependency. |

Source access: [USDA download listing](https://fdc.nal.usda.gov/download-datasets/), [Ciqual versioned files](https://zenodo.org/records/17550133), [OFF API and bulk guidance](https://openfoodfacts.github.io/openfoodfacts-server/api/).

Build a manifest with source release, exact URLs, publisher checksum when available, our SHA-256, downloaded bytes, licence/attribution, adapter version, mapping version and output hash. Keep downloaded originals in a reproducible local build cache, outside Git. A mutable upstream URL must never silently replace a pinned release. The same input hashes and importer version should produce the same logical records and stable pack bytes; wall-clock run timestamps belong in a separate run report.

Pipeline stages: fetch → verify → parse/stage → normalize → validate → build indexes → package → compare with previous release. Invalid records go into an explicit rejection report, not silently missing output. Fail publication on schema drift, unexpected count/coverage changes or broken references. An accepted baseline can include documented source anomalies.

Bundle the initial generic packs with app releases. Measure size before deciding to make larger packs optional. Release assets can host optional immutable packs without a WLO application backend; use a trusted manifest, declared size/hash, schema compatibility check, temporary download and atomic activation. Retain the last valid pack on failure. Do not introduce background catalog downloads or alter R-C4 consent defaults through this design.

## Initial upstream check

Downloaded and inspected the Foundation JSON archive linked by USDA on 2026-09-19:

- URL: https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_foundation_food_json_2026-04-30.zip
- Bytes: 469,303; SHA-256: `186e988ec542e913f51ef62b86a47758e8cdd0d1dc3889e7b055581f3c09c77a`.
- `FoundationFoods` contains 395 positions: 363 food objects and 32 null entries.
- Of those objects, 178 lack nutrient ID 1079 (fibre); 42 lack ID 1005 (carbohydrate by difference). This measures presence of specific IDs, not a full usable-nutrition audit.
- A portion example associates 2 tablespoons of commercial hummus with 33.9 g. Preserve both amount and weight: this is not 33.9 g per tablespoon.

This makes Foundation alone insufficient as a complete recipe library. Import SR Legacy as a separate candidate set; prefer an appropriate complete food profile over a newer but unusable profile. Do not fill a missing nutrient from a different record just because names resemble one another. Full SR/Ciqual/OFF coverage and pack-size audits remain future work.

## Local data model

Keep the existing profile-owned `FoodItem` as the identity used by recipes and logs. Separate its mutable display/customization fields from immutable nutrition revisions. Proposed conceptual entities (names are provisional):

| Entity | Essential fields |
| --- | --- |
| SourceRelease | source namespace, release ID, original URLs/hash, licence, adapter version |
| SourceFoodRevision | source food ID, source revision/hash, native names, brand/barcodes, country of composition, food state, source release |
| NutritionProfile | source food revision, basis amount/unit, edible/as-sold/prepared state, nutrient observations |
| NutrientObservation | nutrient definition, original ID, value/unit, value status, method, source reference; derivation inputs when calculated |
| PortionConversion | food revision, portion label and amount, grams or ml, source and assumptions |
| FoodItem / revision | profileId, selected source profile or custom profile, local aliases, explicit user overrides |
| RecipeIngredient | groceryItemId for shopping, foodItemId plus nutrition revision for math, quantity/unit and conversion reference |

An observation needs two independent axes: value status (`known`, `unknown`, `trace`, `less-than`, `invalid`) and derivation (`reported`, `converted`, `estimated`). Zero is a known numeric value. A database-reported value may itself be analytical, label-based or calculated; keep that upstream method. Never promote all database values to `macrosVerified`.

Static pack rows are source assets; profile-owned selections, preferences, overrides and cached products keep `profileId` per R-B9. Avoid copying an entire worldwide catalog per profile. A repository can search source packs and the profile's local catalog together, then materialize a selected source record as a profile-owned FoodItem/revision. This requires a deliberate repository migration; it is not supported by today's simple Room-only search as-is.

## Normalization contract

### Food identity and preparation

Keep source-qualified IDs. A Ciqual carrot and a USDA carrot are distinct observations even when linked to the same grocery concept. Curated mappings may group equivalent search choices; they must not average values or silently replace a recipe selection.

Raw/cooked, dry/rehydrated, drained/undrained, peeled/unpeeled and edible/as-purchased are meaningful distinctions. Preserve native descriptions where a controlled state cannot be established. Do not infer state or country from UI language. Keep translated names and aliases separate from identity.

For exact branded foods, use the barcode as a string with leading zeroes preserved, validate recognized formats/check digits and implement tested GTIN equivalence rules. Include revision/content hash because a product may be reformulated under the same barcode. No match falls back to a user-confirmed generic food or manual entry.

### Basis, units and portions

Represent an explicit `100 g` or `100 ml` denominator. USDA/Ciqual composition records should be imported according to their published mass basis even when the food is a liquid. OFF `_100g` fields can represent per 100 ml for liquids; the suffix alone is insufficient. If the adapter cannot establish the basis, hold scaling until corrected. [OFF product schema](https://openfoodfacts.github.io/documentation/docs/Product-Opener/schemas/schemas/product/).

Use g for protein/fat/carbs/fibre/sugars, mg for sodium, kcal for energy. Parse decimal text deliberately and retain original precision; round at presentation. kJ→kcal divides by 4.184 and records the conversion. Mass→volume requires a sourced food-specific density; count→mass requires a sourced portion weight. A household cup has no universal food weight.

For mass-basis records: nutrient contribution = nutrient per 100 g × ingredient grams / 100. For volume-basis records, apply the equivalent formula in ml. For example, an illustrative milk record with 60 kcal/100 ml contributes 90 kcal at 150 ml; weighing it in grams requires density. Do not assume 1 g = 1 ml.

### Carbohydrates: store definitions before choosing display

Store at least carbohydrate including fibre, carbohydrate excluding fibre, dietary fibre, and polyols when present as separate concepts. The primary daily total and diet targets must use the same convention. A user changing language must not silently change their targets or historical totals.

USDA ID 1005 includes fibre. Its documentation also warns that separately measured fractions do not necessarily reconcile exactly. Preserve the native value. A proposed conversion, total minus fibre, is a derived estimate only when both usable inputs exist on the same basis; negative or contradictory results are held rather than clamped. Missing fibre means the conversion is unknown. Do not subtract polyols under this policy. [USDA definitions](https://fdc.nal.usda.gov/Foundation_Foods_Documentation/).

Ciqual component 31000 carries the available-carbohydrate INFOODS tag CHOAVL; fibre is separate. OFF's current schema distinguishes `carbohydrates` (excluding fibre) from `carbohydrates-total` (including fibre). Do not subtract fibre a second time, or choose semantics from the country a product is sold in. Pin and test the OFF schema contract. [Ciqual dictionary](https://zenodo.org/records/17550133/files/const_2025_11_03.xml?download=1), [OFF schema](https://openfoodfacts.github.io/documentation/docs/Product-Opener/schemas/schemas/product/).

Proposed initial display convention: carbohydrate excluding fibre, labelled clearly, pending owner choice. Preserve native and derived values; when calculating a recipe, normalize each ingredient into that convention before summation. An unavailable conversion makes that nutrient total incomplete, not every other known nutrient zero.

### Energy and protein methods

Retain each source's reported energy and method. Proposed USDA selection: food-specific Atwater energy (2048), otherwise general Atwater (2047), otherwise legacy energy (1008), with the selected ID recorded; never sum these alternative energy values. This preference is a WLO policy to validate in fixtures, not an upstream mandate.

Ciqual exposes two protein conventions (Jones factor, and N×6.25) and corresponding energy alternatives. Proposed European display selects the regulatory kcal value (328) and N×6.25 protein (25003), while retaining the alternatives (333/25000). Validate these pairings against the release documentation before shipping. A fallback to another method must carry its actual method. Do not claim macro-derived calories exactly reproduce reported calories. [Ciqual component dictionary](https://zenodo.org/records/17550133/files/const_2025_11_03.xml?download=1).

For OFF use source kcal, else documented kJ conversion. Store as-sold and prepared profiles separately; a cocoa-and-milk prepared profile must not be used for cocoa powder plus milk again. Do not manufacture missing calories from an incomplete macro panel.

### Sugar, sodium and absent values

R-A4 includes sugar and sodium, although the current FoodItem/Recipe models only expose kcal, protein, carbs, fat and fibre. Include those two nutrients in ingestion and plan their end-to-end model migration; do not discard them at the source boundary.

Preserve total sugars separately from added sugars; map nutrient definitions, not substring matches. Prefer reported sodium. For OFF normalized sodium expressed in grams, convert to mg once; do not apply the original label's unit a second time. If supporting a salt-equivalent-to-sodium derivation, encode and test that convention explicitly and mark it derived, never add salt and sodium together.

Keep missing markers, trace and upper bounds. A trace must not become exact zero; a value reported as less than a threshold must not become exactly that threshold. Initial UI can hold an exact aggregate and expose the reason instead of implementing interval presentation immediately. Known subtotal may appear only as an explicitly incomplete subtotal. F03's unresolved-ingredient gate continues to govern recipe nutrition presentation.

## Source selection and licensing

Recommended ranking: exact selected brand/barcode, user's prior explicit selection, then generic matches appropriate to state and region. Pending owner decision, France would prefer Ciqual; US would prefer USDA. Relevance and completeness precede source age. Never select a different food merely to complete a macro panel. Let users inspect and change the source, with a new recipe version when applicable.

USDA CC0, Ciqual Licence Ouverte and OFF ODbL notices travel with their records. Keep packs source-attributed, with reproducible transformations. ODbL obligations concern the distributed database, not just file extensions or table boundaries; separate packs do not automatically resolve whether a combined dataset is derivative. Before distributing an OFF-derived pack, document the database boundary and provide the required attribution/share-alike material. Do not upload private diary data to satisfy a public catalog workflow. [USDA licence](https://fdc.nal.usda.gov/api-guide/), [Ciqual reuse terms](https://ciqual.anses.fr/cms/sites/default/files/inline-files/Table%20Ciqual%202025%20doc%20FR_2025_11_19.pdf), [ODbL](https://opendatacommons.org/licenses/odbl/1-0/).

## Updates, caching and portability

Generic source updates arrive as reviewed app/catalog releases. Diff counts, nutrient changes, removed records, methods and seed-recipe mappings. Retired source records remain resolvable by saved revisions. Never silently recalculate saved recipes or diary entries; refreshing nutrition is an explicit operation that creates a recipe revision.

OFF cache freshness and selected-food nutrition history are separate: refreshing a product may propose a new FoodItem revision but does not mutate previous meals. Use actual persistent cache metadata, conditional requests where supported, bounded retry/backoff for 429/5xx, and a real rate limiter. A per-process set of previously scanned barcodes is not a rate limiter or reliable evidence of a persistent cache hit. Reuse EgressPort, R-C4's toggle and receipt trail. Initial proposal: refresh only on an explicit user action; offline cache remains usable with its fetched date.

F13 exports must contain enough selected-food snapshots, units, source IDs/releases, conversion provenance and recipe nutrient contributions to restore without downloading a catalog. Catalog-pack deletion must not destroy the snapshots used by personal data. Barcode refreshes must preserve user overrides rather than overwrite them.

## Implementation sequence and checks from the design

1. **Contract and fixtures:** settle the two owner questions; publish the typed basis/nutrient/revision contract. Add real source fixtures for oil, raw/cooked rice, lentils, milk, a missing-fibre food, a trace/bounded value and an as-sold/prepared packaged product. Clearly separate synthetic arithmetic fixtures from sourced values.
2. **Ingestion spike:** pinned USDA + Ciqual adapters, coverage/rejection report, source manifests and source-specific packs. Measure compressed size, installed size, search latency and how many existing seed ingredients have unambiguous complete profiles. Final mappings require review of full release dictionaries, including alternate sugar IDs.
3. **Persistence/search:** migrate FoodItem, source references, sugar/sodium and serving units. Make legacy ambiguous basis/convention explicit; preserve old totals and ask for correction when required. Add source-aware search without changing recipe selections automatically.
4. **Recipe integration:** WLO-0157 adds food revision references, conversions and per-ingredient contributions. Test fractional servings, missing nutrients, unit errors and history invariance. Ingredient-sum nutrition is estimated; cooked per-100-g nutrition additionally needs actual cooked yield and relevant loss assumptions.
5. **OFF adapter/cache:** extend the existing client projection for basis/state, definitions, revision and quality metadata; implement verified cache receipts and throttling. Current FoodSource lacks CIQUAL and the USDA client is a stub. Keep future regional OFF packs separate from this first integration.

Validation must distinguish hard parse/unit/reference errors from plausible analytical rounding differences. Do not reject a record just because kcal differs from a 4/4/9 reconstruction or nutrient fractions fail to sum perfectly. Run imports twice to establish idempotency, test failed-pack rollback, and verify backup/restore without network access.

The present work is a design and a small Foundation-data probe. No production importer, full Ciqual composition audit, OFF corpus download, Android migration or pack benchmark has been completed.


## Acceptance criteria

### Acquisition and generated artifacts

- AC01: From a clean build cache, documented commands fetch the selected immutable source releases, verify pinned hashes, normalize and create installable generic packs. With cached inputs, rebuild works offline. No credential is embedded in the APK or repository.
- AC02: Repeating a build from identical inputs and tool versions produces identical logical records and deterministic distributable bytes. Output manifests include licences, release/source references and transform versions; non-deterministic execution metadata is kept outside the pack.
- AC03: Reports account for every parsed/accepted/rejected record, per-nutrient availability, duplicate IDs, broken references, ambiguous food states/bases and seed-ingredient coverage. Unexpected schema/coverage changes block publication; documented baseline anomalies remain visible.
- AC04: Record the actual compressed/installed pack sizes and search timing on the project's documented Android test target, with record counts and cold/warm p50/p95 measurements. Set and document the operational budget from the prototype before final packaging; do not invent an already-proven size or latency target.

### Correct normalization

- AC05: Fixtures demonstrate that 100 g and 100 ml are distinct, kJ→kcal happens once, sodium g→mg happens once, fractional servings scale correctly, and a source portion representing two tablespoons is not misread as one. Missing density or portion weight holds incompatible-unit calculation.
- AC06: Raw/cooked and as-sold/prepared profiles remain distinct through search, selection, recipe use and export. Dry mix plus added ingredients must not also count an already-prepared nutrition profile.
- AC07: Both carbohydrate definitions remain recoverable. The chosen UI/target convention is explicit; conversions use compatible inputs with recorded derivation. No double fibre subtraction, automatic polyol deduction, negative clamping or implicit reinterpretation on locale change.
- AC08: Distinct energy/protein alternatives are retained with methods and one selected value; energy alternatives are never summed. Source values are not rejected or overwritten merely because they differ from a 4/4/9 reconstruction.
- AC09: Zero, missing, trace, upper-bound and invalid observations survive serialization and restoration distinctly. An incomplete nutrient cannot appear as a complete aggregate. Known unrelated nutrients are retained subject to F03's presentation gate.
- AC10: Kcal/macros/fibre/sugars/sodium survive source→FoodItem revision→calculation→export/restore. Added sugars do not silently replace total sugars, and salt/sodium are not double-counted. Source-specific nutrient dictionaries, including alternate sugar IDs, are covered by reviewed mappings.

### Local use, history and network

- AC11: Generic search/selection and recipe math work from a fresh install in airplane mode. Source identity, preparation state and how-the-number-was-calculated details are inspectable. Profile data and overrides do not leak between profile partitions.
- AC12: An OFF barcode lookup persists across process death and reuses cached data offline. Cache-hit status comes from actual cache metadata, not a process-local set. Enabled uncached requests have correct User-Agent, throttling/backoff and receipts; disabled egress sends no request. Test many distinct barcodes, not only repeated scans.
- AC13: Updating a pack or refreshing a product with the same barcode produces a new source revision without changing prior diary nutrition or recipe versions. Adopted changes create explicit new revisions, preserving user overrides and retired references.
- AC14: Migration preserves existing IDs/relationships and historic totals. Legacy ambiguous units/conventions are labelled unresolved rather than guessed; the migration is safe on re-entry. No destructive database reset is acceptable.
- AC15: Encrypted backup/export and restore preserve selected source snapshots, units, nutrient definitions/statuses, conversions and notices. Restore with no network or source pack succeeds for those selected foods. Removing an optional pack cannot remove personal history.
- AC16: Tampered/incompatible generated packs are rejected, installation is atomic, and a failed update leaves the last valid catalog usable. Keep this check in the build/install path even if initial packs ship only with app updates.
- AC17: WLO-0157 integration exercises a real multi-ingredient recipe with fractional servings, an unresolved ingredient and a revised source food. Contribution explanations agree with the selected food revisions and prior recipe versions remain unchanged.
- AC18: Attribution and release/version details are accessible in the app and carried in exported selected-source data. Distribution includes required data licences/notices and a documented OFF reuse boundary; private user data is never published as part of catalog maintenance.

## Validation evidence and deliverables

- Checked-in source manifests, adapter/mapping code and concise maintainers' build/update instructions. Large upstream archives remain outside Git; small redistributable fixtures carry attribution.
- Versioned normalized schema, explicit mappings, migration scripts and fixtures. Core math runs locally and does not depend on a successful network lookup.
- Generated coverage/diff reports and measured size/search evidence, including an explicit list of unresolved seed-ingredient mappings.
- Focused parser/math/schema/history/backup tests plus Android flow checks for offline search, a cached barcode after restart, missing conversion, explicit source refresh and restoration without packs. Do not call live upstream services from routine automated tests; use recorded fixtures/test servers.
- Material 3 UI updates for source/state selection, provenance, missing data and revision adoption. Accessibility labels distinguish kcal, grams, millilitres and nutrient status.
- Update F02/F03/F13 and technical docs as affected; amend the master FEATURE rulings only when an actual owner decision requires it. Cross-link the source research and design.

## Implementation checkpoints

A. Resolve the canonical owner questions; independently build contracts, source fixtures and mappings.
B. Complete the generic-source ingestion prototype and measure coverage/size before selecting final packing/index details.
C. Migrate model, persistence and search; integrate the catalog with WLO-0157 using one shared nutrition-revision contract.
D. Upgrade OFF normalization/cache/throttling and verify offline/error paths.
E. Verify historical invariance, migration, provenance, licences and offline restore; capture acceptance evidence and documentation.

Each checkpoint can be split into linked child tickets during implementation. WLO-0160 remains the umbrella acceptance contract; do not mark it done merely because the build-time importer works.

## Code entry points

- core/model/src/commonMain/kotlin/app/wlo/core/model/Food.kt
- core/model/src/commonMain/kotlin/app/wlo/core/model/Planning.kt
- core/model/src/commonMain/kotlin/app/wlo/core/model/Diary.kt
- core/data/src/commonMain/kotlin/app/wlo/core/data/FoodRepository.kt
- core/data/src/commonMain/kotlin/app/wlo/core/data/RecipeRepository.kt
- core/data/src/commonMain/kotlin/app/wlo/core/data/RecipeSeed.kt
- core/database/src/commonMain/kotlin/app/wlo/core/database/Entities.kt
- core/network/src/main/kotlin/app/wlo/core/network/OpenFoodFactsClient.kt
- core/network/src/main/kotlin/app/wlo/core/network/UsdaFdcRepositoryStub.kt
- core/engines/src/commonMain/kotlin/app/wlo/core/engines/FoodMath.kt
- core/vault/src/commonMain/kotlin/app/wlo/core/vault/ (schema, snapshots, restore)

## References

- docs/research/open-food-nutrition-data.md — WLO-0158 source comparison.
- docs/research/food-catalog-ingestion-design.md — WLO-0159 design and evidence.
- docs/features/FEATURES.md — governing rulings and release boundary.
- docs/features/F02-food-logging.md and docs/features/F03-meal-planning.md.
- WLO-0157 — ingredient-to-FoodItem linking and recipe nutrition calculation.

## Recipe consumer contract — WLO-0167

The complete recipe handoff is docs/features/F03-recipe-creation.md and docs/tech/WLO-0167-recipe-implementation.md. Recipe calculations consume this ticket's explicit food basis, immutable source observations, conversions and versioned nutrient policy; snapshots must support offline historical explanation/restore. Technical §§3–4/11 and tests T-02/T-07 define that consumer boundary. The unanswered source-ranking and carbohydrate questions remain on WLO-0159; the recipe specification does not choose or override those policies. Source download/normalization scope remains owned here.

## Downstream implementation tickets — 21 September 2026

At the owner's request, WLO-0168 through WLO-0177 are TODO and explicitly blocked by both WLO-0157 and WLO-0160. The complete ticket map and additional dependencies are in docs/tech/WLO-0167-recipe-implementation.md §14 and WLO-0167. This prerequisite retains its existing scope; downstream authoring, library, acquisition and cooking tickets consume its delivered models/calculation/normalization facilities instead of duplicating them.
