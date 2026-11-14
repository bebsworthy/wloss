# Implement the shared recipe editor and manual creation flow

## Source of truth

- [Functional specification](../../features/F03-recipe-creation.md).
- [Technical specification](../../tech/WLO-kpry-recipe-implementation.md).
- [Accepted mockup](../../design/flows/12-recipe-creation.html), visual direction only; use native M3 and current DESIGN-SYSTEM/IA.
- WLO-kpry tracks the completed specification. This ticket tracks implementation and remains TODO until picked up.

## Outcome

Build the native contextual recipe editor with multiline/manual entry, food correction, typed yield and portions, and saving incomplete recipes.

## Dependencies and ownership

Blocked by WLO-w0vs, WLO-e6yf, WLO-rsj8. WLO-w0vs owns the existing ingredient/content/nutrition foundation; WLO-e6yf owns catalog normalization and revision semantics. Build on their delivered primitives without duplicating or reversing those dependencies.

## Requirements

- Replace/extend the current RecipeEditScreen using the durable draft API and standard M3 components. Library/Share context leads with content; meal context leads with explicit destination, portion and nutrition readiness. Preserve old edit deep links and return state.
- Reuse WLO-w0vs ingredient authoring primitives and calculator. Deliver complete title, source, optional typed yield, sections, ingredient text/rows, instructions and notes editing without requiring macros, directions or nutrition completion.
- Preserve stable IDs and original/current wording across view switching, duplicate identical lines, sections, reorder and remove. Bulk paste explicitly appends or replaces with undo. Food matching results cannot steal focus or overwrite manual choices.
- Integrate row correction with local food search/custom-food creation and explicit quantity/unit/conversion. Return to the same row with focus/scroll preserved. Unsupported amounts and counted-component lines remain visibly unresolved.
- Provide distinct recipe-yield, scale-batch and meal-portion actions. Known servings/count/final-weight bases must not be conflated; missing basis holds dependent actions while Save recipe stays available. Explain food/conversion provenance and unknown contributions.
- Persist Back/exit, validate title plus non-heading ingredient for explicit library save, create a new revision on Edit and retain historical snapshots. Publish a destination-aware editor contract for the atomic meal ticket; do not enable a non-atomic save/add placeholder.

## Acceptance and evidence

FR-01, FR-04–FR-07; AC-03, AC-04, AC-06, AC-12; technical §§4–6, 9; T-02, T-04, T-09.

- Exercise manual multiline groups, unknown yield/amount, empty directions, later resolution, fractional/count/weight portions, scale versus yield changes and source-text fidelity.
- Verify rotation/process recovery, custom-food return, keyboard/IME, 200% text, TalkBack, 360 dp/landscape, visible reorder/remove, reduced motion and standard M3 usage. Use the accepted mockup as visual direction.
- Include this slice's migrations, serialization/restore and focused tests in the implementation; they are not postponed to final integration QA. Record commands and material limitations.

## Scope boundary

Atomic meal commit, recipe discovery screens, actual import/AI execution and cooking remain downstream; do not rebuild nutrition calculations.

Frozen feature/release/consent rulings remain authoritative. WLO-10av source-ranking/carbohydrate questions stay there; do not fabricate answers or expand network permissions to make this ticket pass.
