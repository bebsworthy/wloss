## Problem

RecipeIngredient currently references GroceryItem for shopping/pantry but has no link to FoodItem, which owns calories, protein, carbohydrate, fat and fiber per 100 g (documented as per 100 ml for liquids). Recipe stores independent per-serving nutrition totals, so ingredient quantities cannot currently explain or drive those totals.

## Intended behavior

Link each resolved recipe ingredient to a FoodItem while retaining its GroceryItem reference for shopping and pantry. Calculate ingredient nutrient contributions from the selected food and quantity, sum them for the recipe, and divide by a resolved positive serving yield for per-serving nutrition. Unknown yield is allowed for a saved recipe and never defaults to one. Editing ingredients, quantities or base servings updates derived nutrition in the saved recipe version.

## Requirements and acceptance criteria

- Add the FoodItem reference to recipe ingredient domain, persistence and serialization models. Preserve grocery identity and optional-ingredient semantics; an included optional ingredient still contributes nutrition.
- Make the nutrition basis explicit and unambiguous (mass versus volume); do not assume that a field named per100g is always a mass basis when handling existing liquid data.
- Resolve quantity to the selected food's nutrition basis using explicit, traceable conversions. Mass/volume conversions require density; piece/portion conversions require a known weight or volume. Never assume 1 ml = 1 g.
- For each known nutrient, contribution = amount in the food's basis / 100 × nutrient per 100 units. Recipe totals are the sum of contributions; per-serving values divide by a valid positive servingsBase.
- Missing food matches, conversions or nutrient values remain unknown, never zero. Do not display a partial sum as a complete recipe total; enforce F03's unresolved-ingredient gating and governing provenance rules.
- Provide a per-ingredient contribution breakdown and source/basis information for the nutrition explainer.
- Support choosing/correcting the linked food in recipe authoring using standard Material 3 components. Reuse the existing food catalog and nutrition calculation facilities where applicable.
- Preserve immutable recipe version history and historical planned/logged nutrition when food catalog values or recipes change. Define the snapshot/recalculation boundary explicitly.
- Migrate existing and seeded recipes without inventing food matches. Define how legacy recipe-level nutrition is retained and labelled until ingredients are resolved.
- Carry references and any required nutrition snapshots through backup/export/restore, including reference remapping where applicable.
- Cover weighted sums, fractional servings, supported conversions, unknown propagation, recipe edits and historical stability with focused tests. Update the F03/data-model documentation to describe the implemented relationship.

## Starting points

- core/model/src/commonMain/kotlin/app/wlo/core/model/Planning.kt: RecipeIngredient, GroceryItem, Recipe, NutritionPerServing
- core/model/src/commonMain/kotlin/app/wlo/core/model/Food.kt: FoodItem, ServingPreset
- core/database/src/commonMain/kotlin/app/wlo/core/database/Entities.kt: RecipeEntity
- core/data/src/commonMain/kotlin/app/wlo/core/data/RecipeSeed.kt: ingredient JSON codecs and seed loading
- core/data/src/commonMain/kotlin/app/wlo/core/data/RecipeRepository.kt
- docs/features/F03-meal-planning.md (locate the current F03 filename); FEATURES.md governing rulings

## Scope

Ingredient-backed recipe nutrition and its necessary storage, authoring and portability changes. Recipe import and AI recipe drafting remain deferred; this ticket does not enable them.

## Accepted recipe UX integration — WLO-5hvz

The canonical contract is docs/research/recipe-creation-ux.md, reconciled in F03.

- Preserve stable ingredient/section IDs, original ingredient lines, current user wording, order and preparation notes alongside optional parsed quantities and food references. Unresolved text is first-class recipe content; a section heading is not a nutrient-bearing ingredient.
- Permit saving a named recipe with ingredient lines when yield, quantities or matches are missing. Represent absent yield explicitly through domain, persistence, migration and serialization; retain existing known servings. Do not synthesize one serving, zero quantities or zero nutrients to satisfy a non-null field.
- Keep library eligibility independent of calculation readiness. Missing yield holds per-serving calculations and any portion action needing that basis. A meaningful supported portion with incomplete nutrients follows the existing agenda unknown/coverage rules. Complete batch math still requires its ingredient data, but not a serving count. Respect F03's presentation gate.
- Authoring supports multiline text and row editing against one list. Preserve explicit user corrections, allow undo of bulk replacement and invalidate only affected interpretations after edits. Retain unaffected row matches and personal notes when parsing/refining.
- Preserve notes, attribution, variations and historical snapshots. Stable recipe reference links are navigation-only and do not contribute nutrients. Recursive counted-component calculation remains a separate extension; until supported, its ingredient line stays unresolved and contributes unknown rather than zero.
- Validation includes save/restore with absent yield and unmatched lines, later completion without changing prior records, section/row round-trip fidelity, edit invalidation, and no fabricated per-serving totals. Library retrieval and cooking UI are governed by the broader UX contract, not added wholesale to this calculation ticket.

## Complete implementation handoff — WLO-kpry

The accepted mockup now has a full functional contract in docs/features/F03-recipe-creation.md and technical contract in docs/tech/WLO-kpry-recipe-implementation.md. For this ticket use technical §§3–6/11 and tests T-01/T-02/T-05/T-07 for stable content, typed nullable yield, pinned food/conversion evidence, calculation readiness, historical stability and migration. WLO-w0vs retains its direct ingredient-calculation scope; import, AI acquisition and cooking are separate delivery slices, not additional prerequisites for this ticket.

## Downstream implementation tickets — 21 September 2026

At the owner's request, WLO-rsj8 through WLO-45gn are TODO and explicitly blocked by both WLO-w0vs and WLO-e6yf. The complete ticket map and additional dependencies are in docs/tech/WLO-kpry-recipe-implementation.md §14 and WLO-kpry. This prerequisite retains its existing scope; downstream authoring, library, acquisition and cooking tickets consume its delivered models/calculation/normalization facilities instead of duplicating them.
