## Objective

Design download, local distribution, provenance and nutrient normalization for USDA FoodData Central, Ciqual and Open Food Facts. Produce an implementation-ready proposal, with unresolved owner choices explicit. Do not implement application or importer changes in this ticket.

## Direction under analysis

- Build-time ingestion of pinned USDA Foundation/SR Legacy and Ciqual releases into compact, source-attributed offline catalogs.
- On-demand Open Food Facts barcode lookup with persistent local caching; evaluate optional regional packs separately.
- Explicit mass/volume basis, food preparation state, source revisions, typed nutrient definitions, unknown/trace/bounded values and reproducible calculations.
- Preserve original source records and recipe/diary history; use curated cross-source mappings without averaging nutrient values.
- Include sugar and sodium required by current R-A4, beyond the five values currently exposed by FoodItem.

## Deliverable

docs/research/food-catalog-ingestion-design.md. Related: WLO-w0vs (ingredient links), WLO-93ax (source research).


## Draft delivered

The proposal is written in docs/research/food-catalog-ingestion-design.md. It covers source acquisition, reproducible pack generation, local FoodItem revisions, nutrient/basis normalization, matching, licence boundaries, cache/update behavior, backup portability and implementation order.

A small upstream probe inspected the pinned USDA Foundation April 2026 JSON: 469,303 bytes; 363 food objects plus 32 null entries; 178 objects missing nutrient ID 1079 (fibre). URL and SHA-256 are recorded in the document. This supports explicit anomaly reporting and retaining SR Legacy as a separate candidate set; it is not a complete catalog audit.

Owner choices q-000041 and q-000042 remain open: source ranking and the primary carbohydrate convention. Architecture independent of those choices is drafted; their proposed defaults are clearly labelled. No application code or governing rulings were changed.
