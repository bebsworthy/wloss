# Implement AI whole-meal proposals and multi-item acceptance

## Source of truth

- [Functional specification](../../features/F03-recipe-creation.md).
- [Technical specification](../../tech/WLO-0167-recipe-implementation.md).
- [Accepted mockup](../../design/flows/12-recipe-creation.html), visual direction only; use native M3 and current DESIGN-SYSTEM/IA.
- WLO-0167 tracks the completed specification. This ticket tracks implementation and remains TODO until picked up.

## Outcome

Extend validated recipe generation to whole meals containing separate recipes and ordinary foods, with per-item choices and one atomic commit.

## Dependencies and ownership

Blocked by WLO-0157, WLO-0160, WLO-0174, WLO-0171. WLO-0157 owns the existing ingredient/content/nutrition foundation; WLO-0160 owns catalog normalization and revision semantics. Build on their delivered primitives without duplicating or reversing those dependencies.

## Requirements

- Enable One dish/Whole meal only in meal acquisition when this path is complete. Request one meal, not a week, and retain existing meal contents and the explicit date/destination.
- Validate the bounded items[] schema with new_recipe versus food_suggestion identities. Preserve independent dishes/foods; never turn yogurt/fruit/drinks and recipes into one synthetic recipe.
- Show item selection, new recipe review/refinement, supported portion basis and per-item/combined nutrient readiness. Preserve unaffected items and manual corrections when refining one dish.
- Save only selected new recipes and add selected portions/ordinary foods in one operation using the existing atomic coordinator. Unselected proposals remain drafts or are explicitly discarded; no silent library clutter or sibling replacement.
- Revalidate changed dietary rules/targets/destination before commit. Preserve unknowns, prohibit unsupported portion conversions and fit claims, and keep explicit cloud consent/capability behavior from the one-dish implementation.
- Persist/recover the multi-item draft graph and its operation IDs through process recreation and vault remapping without duplicate commits.

## Acceptance and evidence

FR-02, FR-05–FR-07; AC-01, AC-11; technical §§6, 8, 13; T-02, T-05, T-08, T-09.

- Test a meal with two new recipes plus an ordinary food, selection/deselection, different count/weight/serving bases, missing nutrient/portion basis and a populated destination.
- Inject failure midway through commit and simulate response/draft/commit process death; verify all-or-nothing recipes/items, preserved siblings, one receipt and no duplication after restore.
- Extend model/native evidence to whole-meal prompts, item-specific refinement and changed constraints with no invented nutrition or identity.
- Include this slice's migrations, serialization/restore and focused tests in the implementation; they are not postponed to final integration QA. Record commands and material limitations.

## Scope boundary

No week solver, saved-menu system, shopping/pantry, implicit replacement or new recipe nutrient calculation engine.

Frozen feature/release/consent rulings remain authoritative. WLO-0159 source-ranking/carbohydrate questions stay there; do not fabricate answers or expand network permissions to make this ticket pass.
