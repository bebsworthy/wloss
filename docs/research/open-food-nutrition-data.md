# Open food nutrition data for WLO

Research date: 2026-09-19 · Ticket: WLO-0158 · Related implementation: WLO-0157

## Recommendation

Use a versioned, bundled catalog of generic ingredients built from USDA FoodData Central (Foundation Foods plus selected SR Legacy records). Add Ciqual 2025 for French-market coverage and French names. Use Open Food Facts for exact packaged-product/barcode matches, subject to its data licence. This is a recommendation, not a change to frozen product rulings or an implemented importer.

Generic composition tables and branded-product databases solve different problems. A recipe ingredient such as raw carrot needs a composition record for that food and preparation state; a particular branded sauce needs its own label. Do not substitute arbitrary branded matches for generic ingredients or average unrelated records together.

## Sources compared

| Source | Coverage and distribution | Licence / reuse | Fit for WLO |
| --- | --- | --- | --- |
| USDA FoodData Central | Foundation Foods: basic foods with analytical metadata. SR Legacy: broader historical composition data, final release April 2018. FNDDS: foods consumed in dietary surveys. Branded Foods: industry-provided product labels. CSV and JSON downloads. | Public domain / CC0 1.0. USDA requests attribution. | Best initial generic ingredient foundation; use appropriate food states, with SR Legacy filling coverage gaps. |
| ANSES Ciqual 2025 | 3,484 foods, 74 components. French food supply; English spreadsheet and XML distribution also available. | Licence Ouverte; identify ANSES, source/version, and avoid misleading alterations. | Strong French catalog addition; bulk data permits local lookup. |
| Open Food Facts | International packaged products, barcodes, ingredient lists and nutrition. API plus CSV/JSONL exports. Completeness varies by product. | Database ODbL; individual contents DbCL; images have separate CC BY-SA terms. | Exact branded products. Retain quality checks and missing nutrients. |
| UK CoFID 2021 | UK food composition, downloadable Excel; separate older-food file. | GOV.UK publishes under Open Government Licence v3.0 except where stated otherwise. Preserve notices and verify the selected file's terms when packaging. | Useful UK regional pack; the verified bulk release is dated 2021. |
| THL Fineli | Download page lists 4,232 foods, packages of 55 or 74 components; CSV, Finnish/Swedish/English names, household measures and portions. | CC BY 4.0, attribute THL/Fineli. | Good regional alternative with useful portion data. |
| FSANZ Australian Food Composition Database, release 3 | December 2025; 1,588 foods; downloadable Excel, 58 core nutrients, additional components for some foods. Primarily analytical data. | The AFCD-specific download agreement is based on CC BY-SA 3.0 Australia with additional terms. Do not assume the generic FSANZ website licence applies. | Useful Australian pack, but review the exact release agreement before inclusion. |

Sources: [USDA licence/API](https://fdc.nal.usda.gov/api-guide/), [USDA downloads](https://fdc.nal.usda.gov/download-datasets/), [USDA data types](https://fdc.nal.usda.gov/Foundation_Foods_Documentation/); [Ciqual distribution](https://zenodo.org/records/17550133), [Ciqual reuse documentation](https://ciqual.anses.fr/cms/sites/default/files/inline-files/Table%20Ciqual%202025%20doc%20FR_2025_11_19.pdf); [OFF API and bulk access](https://openfoodfacts.github.io/openfoodfacts-server/api/), [OFF licences](https://openfoodfacts.github.io/openfoodfacts-server/api/tutorials/license-be-on-the-legal-side/); [CoFID download](https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid); [Fineli open data](https://fineli.fi/fineli/en/avoin-data); [AFCD downloads](https://www.foodstandards.gov.au/science-data/food-nutrient-databases/afcd/data-files), [AFCD licence agreement](https://www.foodstandards.gov.au/science-data/monitoringnutrients/afcd/datauserlicenceagreement).

## Secondary candidates

- **DTU Frida (Denmark):** worthwhile regional source. The verified version 5.4 documentation permits reproduction with clear source/version acknowledgement. The website redirected to a new frontend and a DTU dataset record could not be fetched; exact current bulk package and licence need checking before selecting it. [DTU documentation](https://frida.fooddata.dk/pdf/en-frida-5.4-dokumentation.pdf).
- **AUSNUT:** distinct from AFCD. The verified 2011–13 release has CC BY 3.0 Australia terms. Do not transfer that licence conclusion to AFCD or later AUSNUT releases. [Release-specific terms](https://www.foodstandards.gov.au/science-data/monitoringnutrients/ausnut/disclaimer).
- **OpenNutrition:** downloadable ODbL aggregate covering generic, branded and restaurant foods. Its own methodology acknowledges LLM estimates and attribution gaps. It is therefore not recommended as WLO's default authoritative source; adopting individual records would require provenance review. [Dataset download](https://www.opennutrition.app/download), [methodology](https://www.opennutrition.app/about), [dataset description](https://www.opennutrition.app/).

## Offline distribution and licensing implications

For the initial catalog, ingest pinned bulk releases during development/build, validate them, and package only required nutrient and provenance fields into a local database. This avoids a runtime API dependency for recipe calculations. Size the actual generated artifact before deciding bundled versus optional packs; no Android bundle size or complete row-quality audit was measured in this research.

USDA's API requires a key and places responsibility on its holder to keep it private. Its downloadable data is a better default for WLO's no-backend architecture. Do not ship a shared secret in the APK. [USDA API guide](https://fdc.nal.usda.gov/api-guide/).

ODbL permits reuse with attribution and imposes share-alike obligations on publicly used derivative databases. It does not simply relicense application source code. Keep source data, transformations, licences and notices identifiable; define the distributed database boundary before merging OFF-derived data into a catalog. This recommendation is not a claim that separate physical tables alone settle licence obligations. [ODbL legal text](https://opendatacommons.org/licenses/odbl/1-0/).

OFF read operations require an identifying User-Agent, not a user login. Its documentation directs bulk users to exports and limits interactive API traffic. Use caching and explicit barcode searches; never fetch an entire catalog through per-product requests. [OFF integration documentation](https://openfoodfacts.github.io/openfoodfacts-server/api/).

Existing product context: FEATURES.md R-C4 governs food database lookups (F13 integration toggle, caching, audit trail). This research does not amend its default. The repository has OpenFoodFactsClient and UsdaFdcRepositoryStub; an existing source enum is not evidence that a USDA catalog importer is implemented.

## Data-model requirements before ingestion

These are proposed WLO requirements inferred from source formats and the existing recipe model:

1. **Record identity and provenance:** dataset namespace, original food ID, release/version, source URL, licence, import timestamp, original nutrient identifiers and derivation method. Database-sourced does not automatically mean independently measured or user label-verified.
2. **Explicit basis:** amount plus mass/volume unit and edible-portion definition. Preserve raw/cooked/dried/drained state. Piece and household measures need food-specific conversions.
3. **Nutrient semantics:** preserve carbohydrate definition and fibre method. USDA documents carbohydrate by difference, so a bare `carbGPer100g` field cannot safely encode every source's semantics. Specify normalization before comparing or summing different definitions. [USDA component documentation](https://fdc.nal.usda.gov/docs/Foundation_Foods_Documentation_Apr2024.pdf).
4. **Energy:** retain reported kcal and its method. If deriving energy, make that a separate documented calculation; do not silently overwrite reported energy with a universal 4/4/9 calculation.
5. **Unknowns:** distinguish missing, zero, trace, and below-quantification values. A missing nutrient must not become zero. AFCD explicitly states that absent reported values do not establish nutrient absence. [AFCD release documentation](https://www.foodstandards.gov.au/sites/default/files/2025-12/About%20the%20Australian%20Food%20Composition%20Database%20-%20Release%203.pdf).
6. **Stable history:** source updates create new catalog revisions; saved recipe/diary snapshots retain the values and source revision originally used. Do not silently change previous meals after a dataset update.
7. **Matching:** curated grocery-to-food mappings for seed recipes, with explicit unresolved matches. Keep aliases for search but retain preparation-state distinctions. A source ID should be part of every recipe ingredient's nutrition explanation.
8. **Cooking:** ingredient sums are estimates of recipe nutrition. Where drained fat, discarded liquid, or cooking losses matter, preserve assumptions and avoid presenting raw-ingredient sums as a measured cooked result. Cooked weight is needed for per-100-g cooked recipes, separately from per-serving calculations.

## Suggested next implementation slice

Start with USDA Foundation + selected SR Legacy records covering the existing recipe ingredients. Measure coverage of kcal/protein/carbs/fat/fibre, unresolved conversions, and packaged size. Implement a deterministic, reproducible importer with a source manifest and a small set of independently checked food examples. Then add Ciqual with its own identifiers and explicit nutrient mappings. WLO-0157 owns ingredient linking and calculation; dataset ingestion should be tracked as a separate implementation ticket when scheduled.

This investigation verified publisher documentation, download listings and reuse terms. It did not download and audit all datasets, prove record-level completeness, or change application code.
