# Recipe creation and reuse — UX specification

WLO-0161 · Revised with owner approval in WLO-0165 · 2026-09-19.

Accepted UX decisions and rationale, retained under research at the owner's request (WLO-0162). The saving, review, text-entry, retrieval, cooking-follow-on and component-link requirements below incorporate the accepted [Paprika research](paprika-ux-deep-dive.md). The accepted [mockup](../design/flows/12-recipe-creation.html) supplies visual direction. The canonical detailed [functional specification](../features/F03-recipe-creation.md) and [technical specification](../tech/WLO-0167-recipe-implementation.md), prepared in WLO-0167 after mockup acceptance, supply the implementation contract and acceptance criteria.

This explores the intended feature, following the owner's renewed interest in AI-created meals and imported recipes. It does not re-enable them in the current WLO-0156 implementation: WLO-0151 / R-S3 and R-S10 still govern release scope. Current application/prototype code and release boundaries remain unchanged. WLO-0157 supplies ingredient-based recipe nutrition; WLO-0160 supplies food catalogs and normalization.

## Product premise

Treat AI and import as the expected ways to acquire a new recipe, with manual creation clearly available. This is the owner's product hypothesis, not measured frequency data. Design for someone deciding what to eat or keeping something they found, not maintaining a database.

A meal is a collection of foods, drinks and recipe portions. A recipe is a reusable set of ingredients and optional instructions, with a yield that may initially be unknown. AI may suggest a meal containing a recipe, yogurt and fruit; do not turn the entire meal into a synthetic recipe or require the user to author one first.

All acquisition routes converge on one editable draft. Users should review the recipe itself, with unresolved details called out. They should not have to approve every correctly matched ingredient or read a chat transcript before saving. The underlying saved recipe structure is the same whichever route created it.

## Entry points and hierarchy

| Starting intent | Entry | Context carried forward | Completion |
| --- | --- | --- | --- |
| Make something for a particular meal | Daily agenda meal + → Create with AI | Date, meal, current contents and explicit planned/eaten destination | Save recipe(s) and add selected portions/items to that meal |
| Use a recipe found elsewhere | Meal + → Import recipe → URL or pasted text | Same meal context | Save recipe and add the selected portion |
| Keep a recipe for later | Recipes → Add recipe | No date or meal required | Save recipe |
| Find something in a browser | Android Share → WLO | Shared URL/text; no inferred meal destination | Review and save; Add to meal is optional afterward |
| Enter a personal recipe | Add recipe → Write recipe | Calling context, if any | Same preview and completion |
| Adapt a saved recipe | Recipe details → Make a variation | Original recipe and attribution | Save a separate variation; original unchanged |
| Continue work | Recipes → Drafts | Saved input, unresolved details and originating context | Resume at the same stage |
| Use an existing recipe | Ordinary recipe search result | Requested portion and destination | Existing add behavior; no creation review |

Inside the existing Add sheet, keep food/recipe search and immediate additions intact. Add clearly labelled Create with AI, Import recipe and Write recipe actions; present the first two prominently and the third visibly, without burying it in settings. Creation opens a full screen, not a second sheet stacked above Add. Returning preserves the Add query, agenda position and any items already added.

Recipes needs a discoverable library entry alongside the agenda's acquisition flow; exact top-level placement must be reconciled with the evolving Plan navigation. Do not restore the historical week-first navigation or bring pantry/shopping back into this study.

Android supports receiving text/URLs via Share and recommends allowing review/editing before use. Use that platform path rather than requiring users to copy and paste every URL. [Android sharing guidance](https://developer.android.com/develop/ui/compose/sharing/receive).

## Path A — create with AI

1. **Capture the request in context.** A short field: “What would you like to make?” Example: “A lentil dinner for two, about 25 minutes.” Carry dietary exclusions and the existing day's target context where available. Show editable constraints compactly; do not repeat onboarding or require targets before creating a recipe. No pantry prerequisite.
2. **Make the scope explicit.** Within a meal, allow One dish or Whole meal. Outside a meal, default to One recipe. Whole meal can contain one new recipe plus ordinary foods or several dishes; meal items retain separate identities. Do not automatically expand this request into a week plan.
3. **Propose one complete option.** Present a named dish/meal, yield, realistic time estimate and the chosen portion's calculated nutrition when available. Offer Another idea and a short refinement input. One coherent proposal is the recommended starting point; a carousel of many alternatives adds comparison work. The user can explicitly ask for alternatives.
4. **Refine the same draft.** “No mushrooms”, “use chickpeas instead”, or “less preparation”. Show the changed ingredients/steps and recalculate from matched foods. An alternative remains a draft; revising it never overwrites a saved recipe or the agenda. Preserve manual edits unless the user chooses to replace them; allow Undo of the refinement.
5. **Review and commit.** The shared preview below is already editable. The user does not visit a second duplicate review screen. Save or save-and-add is the single explicit commit.

AI authors proposed ingredients and instructions. Food matches and quantity conversions supply nutrition. Model-asserted calorie totals are not a substitute for the WLO calculation; unsupported food matches remain unresolved. Hard dietary exclusions constrain suggestions; detected conflicts are explained and unsuitable suggestions withheld. Do not claim allergen safety merely because an LLM or incomplete food record found no conflict.

Existing meal items stay visible as context and are preserved. “Create dinner” for an already populated meal offers additions by default; replacing a specific item must be an explicitly scoped action with before/after preview. Ingredient targets are advisory and reflect existing coverage, with no invented remainder when data is incomplete and no pressure to use an entire day's allowance in one dish.

On-device generation is the preferred route when capable; do not imply that a suitable local model already ships. If unavailable, show the actual recovery choices: existing recipe suggestions, supported model setup, import/manual, or optional BYOK cloud through F12's meal-planning consent. Never switch to cloud silently. The required payload preview shows the actual request/context sent; exclude weight history and unrelated personal records. Consent revocation cancels the call and discards partial cloud output as specified by F03/F12. User-authored input and previously saved recipes survive.

## Path B — import a website recipe

1. **Receive a shared URL or paste one.** Show the source domain. Website fetching is a separate non-AI network action whose egress policy must be added explicitly when this feature ships; R-C4 food-lookup permission does not cover arbitrary recipe sites.
2. **Extract what is present.** Prefer structured Recipe data locally after fetching. If a page includes several recipes, ask which one. Import title, ingredient lines, yield, instructions and source attribution where available; do not invent omitted quantities or cooking steps. Schema.org defines these recipe fields, but sites may omit or misuse them. [Recipe schema](https://schema.org/Recipe).
3. **Open the shared editable preview.** Keep the original ingredient text beside the interpretation when needed: “1 can chickpeas, drained” → select the food state and drained quantity. Easy matches stay out of the user's way. Surface only uncertainties requiring a decision.
4. **Save or save-and-add.** Outside WLO, the default is Save recipe. Do not require the user to choose a meal/date just to collect it.

Fallbacks: if the site is unavailable, blocked, requires sign-in or exposes no usable content, retain the link and offer Paste recipe text or Open website. Do not promise every site works or attempt to bypass access restrictions. Pasted text supports recipes copied from messages or another AI app as well as websites. AI-assisted parsing, if offered later, is a distinct consented capability path; ordinary structured import must not turn on AI.

Publisher nutrition can be retained as “Website nutrition” with its declared basis. The primary calculated panel uses resolved ingredients. If they differ, expose both in the nutrition explainer without averaging them or forcing an interruption for every rounding discrepancy. Missing publisher serving basis remains unknown. Preserve original author/source even after editing; a substantial variation retains an “Adapted from” link.

If already imported, offer Open saved recipe, Make a variation, or review a refreshed version. Never duplicate blindly or silently overwrite prior saved nutrition.

## Path C — write entirely by hand

Use the same recipe editor, initially empty:

- Recipe name.
- What the recipe makes, when known: servings, or a count/weight yield. Missing yield does not block saving.
- Ingredients entered as multiline text or structured rows with food search, quantity, unit and preparation note. Both edit the same ingredient list; reuse recent selections and allow several additions without returning to the agenda.
- Optional instructions and notes; an overnight-oats recipe need not have formal cooking steps.

Nutrition updates from the linked foods. No requirement to type calories or macros. If an ingredient is missing, create a custom food from its label in a focused subflow and return to the same row, or leave it unresolved. Do not discard the recipe. A typed ingredient without a selected food is visibly unresolved, not silently linked to the first search hit.

A named draft can be kept before it is complete. An explicitly saved recipe needs a name and at least one ingredient line; food matches, amounts and yield may remain unresolved. A bare URL, empty editor or title alone remains a draft. Manual creation and correction work without AI setup or network access.

### Text-first ingredients and progressive matching

Typing or pasting several lines is a first-class input path. Preserve each source line, its order, section headings such as “Sauce” and “Filling”, preparation notes and alternatives. Headings are not ingredients and never contribute nutrients. Keep source wording separately from the user's current editable wording and the interpreted quantity/food reference; the original remains inspectable after correction.

Parse and suggest matches without requiring a catalog selection for every line. Unambiguous results may populate the existing preview; uncertainty stays attached to the affected row. The user can correct one line, choose a food, create a custom food or keep it unresolved. Never silently select the first search result or discard unparsed lines.

Use stable ingredient and section IDs so edits, reordering and view changes preserve unaffected matches and notes. Changing a line's food or amount invalidates only the affected interpretation/calculation, with a visible pending state until resolved. Re-parsing or AI refinement must preserve explicit user corrections or present a replacement for review. Bulk paste into an existing list offers explicit append or replace with undo; switching between text and row views must not duplicate ingredients or lose grouping.

### Saved content and calculation readiness

| State | Available behavior | Held behavior |
| --- | --- | --- |
| Resumable draft | Continue editing or explicitly discard | No library/meal commit; interrupted AI generation is not a complete recipe |
| Saved recipe, yield unknown | Search, favorite, edit, share and read/cook from the recipe; show “Yield needed” | Per-serving nutrition and actions requesting servings/count/weight portions without an established basis |
| Saved recipe, ingredient nutrition incomplete | Same recipe activities; add a meaningful known portion with unknown nutrition under agenda rules | Complete nutrient totals and automatic claims that the recipe fits a target |
| Saved recipe, relevant yield and nutrients resolved | Calculate and use supported portions with provenance | Unsupported unit conversions remain held |

Readiness is derived from the data, not a required multi-step completion wizard. Yield and nutrition completeness are independent: a whole-batch nutrient calculation need not depend on a serving count, but it still obeys F03's unresolved-ingredient presentation gate. Never default an unknown yield to one serving or show zero nutrition. In a meal context, explain what prevents Save & add/log and keep Save recipe available; completing the missing basis can enable the original action without another import. Saving is explicit even when local draft recovery is automatic.

## Path D — adapt or capture something familiar

Make a variation reuses a saved recipe as a draft with original attribution. Changes produce a new recipe identity; routine correction through Edit creates a new version of that recipe. Neither rewrites historic meals.

As a later extension, a collection of already-selected meal items can be used to prefill a recipe, but only on explicit request. A glass of wine and yogurt should not be silently added as ingredients to the cooked dish. Repeated meal combinations may deserve a separate saved-meal concept; do not misuse recipes to implement it.

Photo of a recipe page and voice dictation can later feed the same draft editor. They add recognition uncertainty and their own capability/consent paths. A photo of cooked food is food logging, not reliable reconstruction of a recipe; do not treat them as equivalent.

## Shared review/editor

Compact window: full-screen Scaffold and TopAppBar with Up, a scrollable editor, and one primary destination-aware action above IME/system insets. Wider window: ingredients/instructions with a supporting nutrition/context pane. Keep the same reading and focus order.

Use the same editor and data in two calling contexts:

| Context | Initial content hierarchy | Primary action |
| --- | --- | --- |
| Library / external Share | Title and source, yield if known, ingredients, visible instructions, personal notes; compact nutrition/status beneath the cooking content | Save recipe |
| Meal acquisition | Title and source, selected portion and nutrition/completeness, ingredients, instructions with a clearly labelled expand action, notes | Save & add/log to the explicit destination when the portion basis permits |

Images are optional. Nutrition can be expanded in either context; source and personal notes remain accessible. Matched catalog details are secondary, and uncertainty appears on the affected row: “Choose the drained weight”, not “Entity mapping unresolved”. Keep the initial hierarchy stable during editing; background matching must not move fields or steal focus. Changing context retains content, edits, expansion state and the actual destination. Wider windows use a supporting pane while preserving a logical reading order.

Review focuses on meaningful decisions, not blanket confirmation. When everything resolves, there is no checklist. When not, an inline summary such as “2 ingredients need amounts” jumps to the first affected row. The user can correct these or save with nutrition incomplete. Saving a recipe never means it was eaten.

Allow explicit draft retention without publishing incomplete AI output as a complete recipe. A resumable user-visible draft is not a library recipe, committed plan item or diary entry. Dismissing/back preserves the local draft with no repeated modal; Discard is an explicit action. Partial streamed generation is not a usable saved draft; retain the input and report interruption. Avoid automatic notifications or nag counts for drafts.

## Yield, portion and commitment

Three controls must not be conflated:

- **Recipe yield:** the whole recipe makes four servings (or 12 cookies / a stated cooked weight).
- **Cooking amount:** scaling the recipe to make a different batch; ingredients scale together while a recipe edit/version remains explicit.
- **My meal portion:** how much is added to this person's meal, e.g. one serving or three cookies with an established conversion.

Changing yield alone with fixed ingredients means smaller/larger portions; scaling the batch changes ingredients as well. Give these distinct labels/actions. Never assume “12 cookies” means “12 meals” or that cooking for four means logging four portions for the user.

| Context | Primary action | Effect |
| --- | --- | --- |
| Library or external Share | Save recipe | Saves reusable recipe only |
| Planned meal acquisition | Save & add to dinner (date visible) | Saves recipe and adds only the selected portion as planned |
| Explicit Eaten acquisition | Save & log to lunch (date visible) | Saves recipe and logs selected portion through F02 |
| Existing recipe edit | Save changes | New recipe version; earlier diary/plan snapshots unchanged |
| Variation | Save variation | New saved recipe, original retained |

A secondary Save recipe action can retain a recipe without adding it when entered from a meal. Do not add another confirmation page after the shared review. Existing recipe search retains WLO-0156's immediate + behavior; this transaction applies to newly acquired drafts.

Accepted new recipes are kept in the user's library; discarded AI alternatives are not. No second “also save?” checkbox. Favourite is an independent way to curate frequently used recipes. Newly saved recipes remain discoverable by name/source/recently added.

Commit stores the recipe and requested meal addition together, once. Whole-meal generation previews all proposed items and commits them together without replacing existing siblings. On destination changes or write failure, retain the draft and explain the affected destination; do not retry into duplicate recipes/items. Cancellation adds nothing, while preserving items committed before entering creation.

If nutrition is unresolved, saved recipes may remain usable as clearly incomplete entries; automatic target-fit suggestions must not advertise them as known fits. Apply F03's recipe presentation gate and the agenda's per-nutrient completeness rules without presenting partial totals as complete.

## Finding and reusing recipes

The minimum library supports local search over names, ingredient wording and source, including unresolved text ingredients. Multiple ingredient terms narrow results to recipes containing all entered terms; distinguish “no matching recipes” from an empty library and retain the query when returning from a recipe. Recent means recently saved, not recently eaten. Favorites are independent of nutritional completeness. Categories are optional and never a prerequisite for save or retrieval; nested taxonomy and additional ratings/tried controls are not required for the first acquisition delivery.

Recipe details expose personal notes, Edit, Make a variation and Add to meal. Notes survive imports/refinements and remain visible during cooking. Reusing a recipe asks only for the portion/destination needed by the calling context; it does not repeat creation review. Prior portion choices may be offered with their basis but cannot silently cross incompatible recipe revisions. “Last planned”, any future “Last cooked”, and “Last eaten” must use their actual events; scheduling alone cannot establish cooking or consumption.

## Linked recipes and counted components

Keep two explicit relationships:

- **Reference link:** “Serve with this dressing” opens another recipe. It adds no ingredients, shopping demand or nutrients. Use a stable recipe ID and a separate display label; renaming must not break the link. Missing/deleted references remain visibly unresolved without losing the surrounding recipe text.
- **Counted component:** “50 g of this dressing” is one ingredient contribution referencing a specific child recipe revision and a quantity/basis. It can be calculated only when the child has the matching yield/conversion and sufficient nutrition. A serving yield does not establish a gram conversion. Display the child contribution and allow inspection of its underlying ingredients without counting both the child total and those expanded ingredients again.

Counted components require an explicit authoring action. Importing a hyperlink or recognizing a matching recipe name never creates one automatically. A parent uses the selected child revision; adopting a later child revision creates a new parent version and never rewrites historical meals. Reject direct or indirect calculation cycles with a path explaining the conflict. Missing child data holds the affected calculation, not the saved parent recipe.

Reference links belong in the recipe-content contract. Recursive counted-component calculation is a separate implementation extension, not a prerequisite for WLO-0157's direct FoodItem ingredient math. Until supported, preserve such source lines as unresolved ingredients and do not expose a working counted-component action or pretend the line has zero contribution. When shopping is later integrated, count the required scaled leaves once; avoid adding a prepared component and its expanded leaves to the same generated demand.

## Cooking follow-on — bounded scope

Basic cooking support is a deliberate follow-on to recipe acquisition and retrieval. It remains outside the current WLO-0156 delivery and weight-first release; this contract replaces the former unbounded Blue Sky description.

The first cooking increment includes an explicit Start cooking / Resume cooking action, keeping the screen awake while that foreground session is displayed, reversible ingredient check-offs, a marked current step and recoverable placekeeping. Release the keep-awake behavior on leaving the cooking surface or ending the session. Directions may be absent; ingredient-only recipes still support check-offs. Missing nutrition or yield does not block following the original recipe.

Persist a local session with the recipe revision, displayed batch scale, stable ingredient/step IDs, marks and current position. Resume after navigation, rotation or process recreation without silently resetting progress. Editing a recipe while a session exists leaves that session on its original revision; explicitly starting over on a newer revision creates fresh progress. Batch scaling affects supported quantities only, not cooking time, temperature or pan size. Ending cooking clears active placekeeping only through the explicit session action; it never marks a meal eaten or changes permanent recipe content.

Multiple active dishes and named timers are a subsequent increment. That increment must define independent session progress, switching between dishes, background timer behavior, interruption/restart handling and accessible expiry feedback before implementation. Automatic duration detection must offer editable timers rather than start them silently. Hands-free navigation and per-step macro callouts are not part of the basic cooking scope.

Use standard M3 ListItem/Checkbox for reversible ingredient state, accessible state semantics for the current direction, and labelled Button/IconButton actions for start/resume/end. Keep instructions readable with large text and TalkBack; completed state must be conveyed beyond strike-through or color. Provide quick ingredient reference from directions in compact windows and simultaneous reading panes when space permits. Changing a mark must preserve focus and scroll position. Session recovery and ending must work through visible actions, not gestures alone.

## Recovery and accessibility

| State | User-visible recovery |
| --- | --- |
| AI cannot satisfy constraints | Name the conflicting constraint; edit it explicitly, choose another idea, or use existing recipes. Never relax allergies silently. |
| AI/model unavailable or cloud disabled | Preserve request; show supported local setup/existing recipes/import/manual options. |
| Import interrupted or unsupported | Preserve URL/text; retry or paste content without starting over. |
| Unknown ingredient or quantity | Correct that row, enter a custom food, or save incomplete. |
| Missing recipe yield | Save to the library with “Yield needed”; provide the relevant basis before a portion-dependent action. |
| App recreation / another shared recipe arrives | Restore draft; new share becomes a separate draft and never overwrites current work. |
| Recipe already exists | Open it, create variation, or compare refresh. |
| Target or destination changed during creation | Preserve recipe; refresh the fit preview and confirm the actual destination through the existing commit action. |
| Commit interrupted | Recover an idempotent operation; show saved state or retry with draft intact. |

Use M3 ModalBottomSheet only for contextual action choice, standard ListItem ingredient rows, OutlinedTextField inputs, buttons for actions and FilterChip only for actual filters/constraints. Do not put every ingredient in a card or stack modal editors. Theme comes from DESIGN-SYSTEM.md; use held text/icon/state together, never color alone. No approval of exact pixels or new custom widgets in this conceptual proposal.

Ensure persistent field labels, announced validation tied to rows, keyboard-safe primary action, logical TalkBack focus, dynamic text without clipping, non-gesture alternatives for reorder/remove, and sensible restore focus after editing a food. Announce meaningful loading/completion without reading every streamed token. No invented progress percentage. Reduced motion does not remove information.

## Acceptance scenarios and prototype validation

The acquisition delivery must cover these scenarios in the shared editor:

1. Tomorrow's dinner → AI whole meal for two → replace one ingredient → choose one serving for me → save and add.
2. Browser share → recipe with missing yield and drained weight → save immediately to the library → retrieve by an ingredient → resolve those fields later → add a supported portion to lunch. Before resolution, no invented serving count, complete nutrition or target-fit claim appears.
3. Manual recipe → paste several ingredient lines and two section headings → correct one match → switch between text/rows → save with directions empty → make a variation. Wording, groups, unaffected matches and the original recipe survive.
4. Save the same source from the library and from a dinner context. Verify content-first versus portion-first emphasis, visible save destination, preservation of manual edits and Save recipe remaining available when a portion cannot yet be calculated.
5. Find a saved recipe by name, multiple ingredients and source; favorite it without categories; add a personal note; reuse it next week without a second creation review. An empty search is recoverable without losing its query.
6. Rename a referenced dressing recipe and follow its stable link. Confirm it contributes no nutrition. Preserve an unsupported counted-component line as unresolved rather than dropping it from totals.

For the basic cooking follow-on, start a session, cross off ingredients, mark a step, leave and resume after process recreation. Test large text and TalkBack, then edit the recipe outside the session: the session stays on its original revision. Ending it creates no diary entry. Verify screen-awake behavior is limited to the foreground cooking session.

Before enabling counted components, test a child with a known weight yield, one lacking that conversion, a renamed child, an explicit child-revision update, a direct/indirect cycle and child-versus-leaf double-counting. Before enabling multiple dishes/timers, test independent progress and timer delivery/recovery across interruption. These extension checks do not expand the first ingredient-calculation ticket.

Include one interrupted draft, a recipe with incomplete nutrition, and an already-populated meal. Test whether users understand yield versus their portion, whether unresolved rows can be corrected without opening every ingredient, and whether they can predict the result of Save versus Save & add/log.

Accepted interaction direction: one shared editable recipe, contextual emphasis, save despite unresolved yield/nutrition, text-first manual entry, optional organization, explicit save and recoverable drafts, followed by bounded cooking support. The one-proposal AI presentation and One dish/Whole meal controls remain prototype choices to validate. This specification defines future behavior, not a claim of implementation or a change to R-S3/R-S10's delivery boundaries.
