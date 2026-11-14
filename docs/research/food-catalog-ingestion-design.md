# Food catalog ingestion and normalization — proposal

2026-09-19 · WLO-0159 · Builds on WLO-0158; informs WLO-0157.

Status: design proposal. Source ranking and the primary carbohydrate convention are owner questions q-000041/q-000042. No frozen ruling or application behavior is changed here.

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

## Implementation sequence and checks

1. **Contract and fixtures:** settle the two owner questions; publish the typed basis/nutrient/revision contract. Add real source fixtures for oil, raw/cooked rice, lentils, milk, a missing-fibre food, a trace/bounded value and an as-sold/prepared packaged product. Clearly separate synthetic arithmetic fixtures from sourced values.
2. **Ingestion spike:** pinned USDA + Ciqual adapters, coverage/rejection report, source manifests and source-specific packs. Measure compressed size, installed size, search latency and how many existing seed ingredients have unambiguous complete profiles. Final mappings require review of full release dictionaries, including alternate sugar IDs.
3. **Persistence/search:** migrate FoodItem, source references, sugar/sodium and serving units. Make legacy ambiguous basis/convention explicit; preserve old totals and ask for correction when required. Add source-aware search without changing recipe selections automatically.
4. **Recipe integration:** WLO-0157 adds food revision references, conversions and per-ingredient contributions. Test fractional servings, missing nutrients, unit errors and history invariance. Ingredient-sum nutrition is estimated; cooked per-100-g nutrition additionally needs actual cooked yield and relevant loss assumptions.
5. **OFF adapter/cache:** extend the existing client projection for basis/state, definitions, revision and quality metadata; implement verified cache receipts and throttling. Current FoodSource lacks CIQUAL and the USDA client is a stub. Keep future regional OFF packs separate from this first integration.

Validation must distinguish hard parse/unit/reference errors from plausible analytical rounding differences. Do not reject a record just because kcal differs from a 4/4/9 reconstruction or nutrient fractions fail to sum perfectly. Run imports twice to establish idempotency, test failed-pack rollback, and verify backup/restore without network access.

The present work is a design and a small Foundation-data probe. No production importer, full Ciqual composition audit, OFF corpus download, Android migration or pack benchmark has been completed.
