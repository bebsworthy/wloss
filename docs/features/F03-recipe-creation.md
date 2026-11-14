# F03 — Recipe Creation, Reuse & Cooking — Functional Specification

WLO-0167 · 21 September 2026 · Implementation contract based on the owner-accepted [WLO-0166 mockup](../design/flows/12-recipe-creation.html) and [recipe UX decisions](../research/recipe-creation-ux.md).

This is the canonical detailed functional contract for this F03 increment. The [technical specification](../tech/WLO-0167-recipe-implementation.md) defines storage, algorithms, interfaces, migration, delivery slices and verification. [FEATURES.md](FEATURES.md) remains authoritative for release scope and frozen rulings. Mockup acceptance does not lift R-S3/R-S10, ship AI/import in the current release, or enable the parked Mark eaten flow. Future Eaten acquisition uses F02 only when that destination is enabled. Basic cooking is a separate follow-on increment.

---

## Identity

| | |
| --- | --- |
| **Feature ID** | F03 — Recipe Creation, Reuse & Cooking; detailed supplement to [F03](F03-meal-planning.md) |
| **Provides** | Acquire a reusable recipe through AI, website/text import or manual entry; correct, save, find, adapt, portion and follow it while cooking. |
| **User problems solved** | Keep a useful recipe without completing a nutrition form; turn an idea into something editable; reuse personal variations and notes; calculate supported portions honestly; return to interrupted preparation. |
| **AI consent category** | Consumes F12 `meal-planning` for cloud generation/refinement. Local generation and deterministic parsing need no AI consent. Website retrieval needs its own explicitly authorized non-AI egress policy; R-C4 does not cover it. |
| **Primary evidence** | [Accepted UX](../research/recipe-creation-ux.md), [Paprika study](../research/paprika-ux-deep-dive.md), [mockup and evidence](../design/flows/evidence/WLO-0166/README.md), [catalog normalization](../research/food-catalog-ingestion-design.md). |

## 1. Purpose & Core Objectives

Make recipe acquisition easier than maintaining a database. AI and import are the expected main acquisition routes (a product hypothesis); manual creation is equally capable and always available. All routes produce the same editable recipe, with no mandatory chat history, nutrition typing or confirmation of already-resolved lines.

A **recipe** is reusable content with ingredients, optional instructions and independently known yield/nutrition. A **meal** is an ordered collection of food, drink and recipe portions attached to a date and planned/eaten destination. A **draft** is recoverable work, not a saved recipe or meal item. A **cooking session** records placekeeping, not consumption.

Objectives:

- Save a named recipe containing at least one ingredient line even when amounts, food matches, instructions or yield are absent.
- Offer one destination-aware commit from the editor. Reuse an existing recipe without repeating creation review.
- Preserve source wording, corrections, personal notes, groups and stable identities through every route and view change.
- Derive nutrition from selected FoodItem revisions and explicit conversions. Never silently invent a serving, conversion, food match or nutrient.
- Preserve previous recipe versions and all historical meal nutrition after edits or catalog updates.
- Recover drafts and cooking placekeeping after navigation and process recreation; manual editing/retrieval work offline.

## 2. User Moments — when and how it is used

At mealtime, the user opens dinner's add action, describes a dish, reviews a proposal, chooses their portion and saves it into dinner. Existing foods remain in place. Later, they can find the recipe by an ingredient and use it again.

While browsing, Android Share or a pasted link captures a recipe for later without requiring a date or meal. In the kitchen, a personal recipe can start as several pasted ingredient lines, with directions added only if useful. During cooking, the phone offers readable steps and reversible checkmarks; leaving the screen does not discard progress.

These are occasional acquisition, frequent reuse and session-based cooking actions. No onboarding, pantry inventory, daily targets, account, categories or AI model setup is required to keep a recipe manually.

## 3. How It Works — functional mechanics

### FR-01 — Acquisition and destination

| Entry | Carried context | Default completion |
| --- | --- | --- |
| Daily agenda meal + | Profile, local date, meal, planned destination, existing items, search/scroll return state | Save & add to the named meal |
| Explicit Eaten acquisition, when enabled | Same context plus explicit F02 destination | Save & log to the named meal |
| Plan toolbar → Recipes → Add | Profile only | Save recipe |
| Android Share → WLO | Shared URL/text, active profile after unlock; no inferred date | Save recipe |
| Recipe details → Edit / Make a variation | Recipe identity and revision / ancestry | Save changes / Save variation |
| Recipes → Drafts | Persisted acquisition stage and original destination | Resume; revalidate destination at commit |

Retain the current daily agenda and four navigation destinations. The recipe library is a toolbar destination from Plan as shown in the accepted mockup, not a restored week-first home. Preserve existing food/recipe search and immediate additions in the Add sheet. Create with AI, Import recipe and Write recipe open full-screen flows after dismissing that sheet. Returning restores query/scroll and previously added items.

Support multiple retained drafts: a new Share creates a new draft rather than replacing the one being edited. The library shows a direct Continue action for one draft and a Drafts list for several. Starting another path never silently discards work.

### FR-02 — Create with AI

1. Ask “What would you like to make?” Show editable people/yield, time and dietary constraints; targets are optional context. Outside a meal default to one recipe; in a meal support **One dish** and **Whole meal** when that delivery slice is enabled.
2. Produce one coherent proposal. Whole meal keeps new recipes and ordinary foods separate, with item selection and portions in the preview. Never synthesize the entire meal into a recipe or replace existing siblings by default.
3. Show editable content immediately after a complete, validated response. Offer Another idea and free-text refinement. A complete proposal remains uncommitted until Save; streamed fragments are not saveable content.
4. Show the changes from refinement; preserve explicit manual changes. Conflicting proposed replacements require field-level choice. Undo restores the prior draft, and later manual edits must not be lost through undo.
5. Calculate nutrition using food matching, not model-provided macro totals. Show actual readiness and the selected meal portion. Yield alone does not scale ingredients; a request to cook for more people scales the proposed batch.

Hard exclusions constrain generation. Withhold suggestions containing a detected prohibited ingredient and explain the conflict; never quietly relax an allergy. Unknown safety is not a verified allergen-free claim. Target-fit is advisory and unavailable when required nutrient coverage is incomplete.

Use a capable installed model first. When absent, unavailable or unsuitable, preserve the request and offer saved recipes, supported local model setup, import/manual or optional BYOK through F12. There is no silent cloud fallback. Cloud calls require `meal-planning` authorization and an exact payload preview. A configured endpoint on another device still sends data off this phone. Cancellation/revocation retains user input and the previous complete draft, discards the interrupted candidate and adds nothing.

### FR-03 — Import URL or text

Receive text/URLs through Android Share or explicit paste. Do not read the clipboard automatically. Display the destination site before requesting a page. A website fetch is an explicit non-AI action; no request occurs merely because a link was shared or typed.

Prefer structured Recipe data from the fetched document. If several candidates exist, present their titles/source and let the user choose. Import only present fields: title, yield wording, ingredients and groups, instructions, time and attribution. Preserve source URL/author, original lines and extraction provenance. Unknown data stays unknown. Linked external content is not fetched recursively.

If fetching fails, requires sign-in, is blocked or produces no usable recipe, keep the link and offer Retry, Paste recipe text, Open website or manual editing. There is no access-control bypass or guarantee of universal site support. Structured import requires no model. Pasted free text is retained in full; uncertain boundaries are editable and unparsed content remains accessible rather than disappearing. The user can assign blocks to ingredients, directions or notes.

Duplicate detection offers Open saved, Make a variation or Review refreshed version. A refresh compares source changes against the saved source and the user's changes, never silently replacing either. Publisher nutrition is a separately labelled claim with its original basis, not a fallback that fills ingredient-calculation gaps.

### FR-04 — Shared authoring

The same editor supports title, source, optional typed yield, ingredient sections/rows, optional structured instruction sections/steps and personal notes. Title plus at least one non-heading, nonblank ingredient line enables Save. Title-only content or a bare URL remains a draft.

Multiline input and structured rows edit one list. Preserve original text separately from current wording and interpretation. Assign stable ingredient, section and step IDs. A row can have a food selection, quantity/unit, preparation note, optional flag and explicit inclusion state. A heading never contributes nutrients. Included optional ingredients count normally; excluding a row is a visible user action.

Unambiguous local parsing may populate a row. Ambiguous search candidates are suggestions, not silent first-hit matches. Inline correction selects food/state, quantity and conversion, reusing F02 food search and custom-food creation. Return to the exact row with focus/scroll retained. No food match or amount is required to keep the line.

Changing an ingredient's food/quantity invalidates affected derived data; unaffected matches stay intact. Matching cannot overwrite newer typing or manual corrections. Bulk paste into an existing list explicitly chooses Append or Replace, with undo. Reorder/remove have visible accessible actions as well as any gesture. Save does not wait indefinitely for background matching: it may preserve the latest content with unresolved nutrition.

Back retains work without a repeated modal. Discard is explicit and has an undo opportunity; drafts are never automatically published. Autosave status reflects actual durable writes, including an error if storage fails.

### FR-05 — Yield, cooking amount and meal portion

| Control | Meaning | Example |
| --- | --- | --- |
| Recipe yield | Output of the recorded ingredients; one or more independently established bases | 4 servings; 12 cookies; 800 g cooked |
| Scale batch | Multiply supported ingredient quantities and known yield bases | Make half the recipe |
| My portion | Select how much of that recipe is added/logged | 1 serving; 3 cookies; 200 g |

A missing yield is not one serving. Counts require a named item basis; weight means final edible recipe weight, not the sum of raw ingredient weights. Conversion between bases requires both to be established for that batch. Serving yield alone cannot convert to grams. Ranges such as “4–6 servings” retain their source wording until the user establishes a specific basis.

Changing yield with ingredients fixed changes per-portion math. Scaling a batch changes supported ingredient amounts and yield together. Do not scale oven temperature, cook time, pan size or vague quantities such as “to taste”. Review these explicitly. Cooking-session scale is temporary; saving a scaled recipe is a separate edit/variation.

### FR-06 — Readiness and nutrition

| Content/data state | Allowed | Held |
| --- | --- | --- |
| Draft without a name or ingredient | Retain, edit, discard | Library or meal commit |
| Saved recipe, yield unknown | Find, favorite, edit, export, read/cook original batch | Serving/count/weight actions without their required basis |
| Known yield, unresolved included ingredient | Save, cook, add a meaningful portion with unknown nutrition | Complete recipe totals, per-serving nutrient panel, automatic target-fit claim |
| Included ingredients resolved, one nutrient unavailable | Use supported portions; show known nutrients and missing nutrient state | That nutrient's complete total and any fit claim needing it |
| Required data resolved | Batch and supported portion nutrition with explanation | Unestablished conversions |

F03's unresolved-ingredient presentation gate applies before the per-nutrient gate. The explainer may expose known contributions explicitly as incomplete; the main panel must not show a partial recipe as complete. Unknown is never zero. The agenda counts incomplete nutrient coverage honestly, with no claimed complete remaining allowance.

Every calculation links to food source/revision, basis, conversion, ingredient contribution, batch yield and selected portion. AI authorship and calculated nutrition are separate provenance facts. Preserve website/legacy nutrition separately and explain disagreement without averaging or silently choosing it over ingredient math.

### FR-07 — Save, add and version

Save commits reusable content only. Save & add atomically saves new recipe(s) and selected planned items; Save & log uses F02 for actual entries only when that destination exists. The date, meal and planned/eaten meaning remain visible. A secondary Save recipe action is always available once content is saveable.

Double taps, retry and process death must produce one result. A storage/destination conflict retains the draft. If targets or destination contents changed, refresh context and fit feedback before the same explicit commit; do not add a duplicate review screen. Additions preserve siblings. Replacement, if offered, names the affected item and previews the change.

Edit creates a new revision of the same recipe; variation creates a separate identity with ancestry/source. Neither changes historical meals or active cooking sessions. Catalog updates do not silently recalculate saved revisions. Explicit rematching/recalculation is a reviewed edit. Archive removes a recipe from normal search while retaining references/history, with undo. Missing or archived references remain inspectable as historical content.

### FR-08 — Library, reuse and reference links

Local search matches names, source and all ingredient wording, including unresolved text. Multiple terms narrow results using AND semantics. Recent sorts by saved/updated content time, not planned/eaten time; favorites are independent of nutrition readiness. Empty library, empty favorites and no search matches have distinct recovery copy. Preserve query, filter and scroll on return.

Details provides ingredients/instructions, source, notes, nutrition explanation, Edit, Make a variation, Favorite, Add to meal, Share/export and Archive. Reuse asks only for missing portion/destination information. A previous portion is a suggestion tied to a compatible basis, not a cross-revision assumption. Personal notes persist across import refresh and AI refinement and appear during cooking.

Reference links use stable recipe IDs and optional display labels; renaming does not break navigation. They add no nutrition or shopping demand. A counted child recipe is a different, deferred ingredient type requiring pinned revision, portion basis and cycle checks. Until that extension ships, preserve its source line as unresolved and do not expose a working counted-component action.

### FR-09 — Basic cooking follow-on

Start/Resume cooking opens the pinned recipe revision and batch scale. Ingredients have reversible checkmarks and steps have an explicit current state. Nutrition/yield completeness does not block reading the original batch. Directions may be absent. Preserve marks, step, reading position and scale after navigation, rotation and process recreation.

Allow one active session per profile initially. Starting another recipe offers Resume current or End current and start this one. Editing a saved recipe leaves the session unchanged; using the newer version requires explicit restart. Keep the screen awake only while the cooking surface is foreground; release it on exit/background. Do not implement a background CPU lock for reading a recipe.

End cooking ends placekeeping and records that session's end, not food intake. Do not call it proof that the recipe was cooked/eaten. Multiple simultaneous dishes, timers and hands-free control are separate extensions.

## 4. User Interaction Model

Use the accepted [flow 12](../design/flows/12-recipe-creation.html) for visual direction and [DESIGN-SYSTEM.md](../design/DESIGN-SYSTEM.md)/[IA.md](../design/IA.md) for native tokens and navigation. The HTML fixture behavior is not production logic.

Library/Share review leads with recipe content and keeps nutrition secondary. Meal review leads with destination, portion and nutrition readiness, then ingredients and expandable instructions. Both retain source and notes. On larger windows use supporting panes with the same reading order; on compact windows use one scrollable editor with the primary action above IME/system insets.

Use standard M3 Scaffold, TopAppBar, ListItem, OutlinedTextField, Button, Checkbox, FilterChip, ModalBottomSheet and Snackbar through their documented slots. No custom lookalikes or per-ingredient cards. Sheets handle contextual choices and correction; do not stack modals.

Input minimization: reuse imported wording, parse whole lists, retain recent food selections, offer existing recipes, reuse compatible portions and carry the destination. Never require calories, macros, categories, directions, pantry stock or targets to save.

Motion/haptics follow shared tokens: native ripples/sheet motion, brief checkmark/favorite state feedback and save completion only after durable success. No numeric count-up through invented intermediate nutrition, no streamed-token announcements or fake progress percentages. Reduced motion preserves all state information. Errors do not vibrate repeatedly while typing.

Accessibility acceptance: 48 dp minimum action targets, persistent labels, explicit units, locale-aware decimal/fraction entry, 200% font scaling without clipped content/actions, keyboard/IME-safe save, logical TalkBack focus and announced row-specific errors. Restore focus after correction and preserve it on check-off. Current/completed/unknown state is conveyed by text/semantics, not color alone. Provide visible alternatives to drag/swipe. Validate portrait, landscape and supporting-pane layouts.

## 5. What the User Gets Out

Reusable recipe content, personal notes/favorites, a recoverable draft, an optional meal portion and cooking placekeeping. Calculated output covers the nutrient vocabulary supplied by WLO-0160 and R-A4; the first compact panel shows kcal, protein, carbohydrate, fat and fiber, with sugar/sodium available when the shared model supports them.

The explanation exposes batch → supported yield → selected portion, source state and every unknown reason. Report numbers as derived from reported/estimated ingredient observations; never promote AI prose or database presence into measured certainty. This increment adds no adherence score, health recommendation, calorie budget algorithm or new chart.

### Functional acceptance matrix

| ID | Scenario and required result | Technical coverage |
| --- | --- | --- |
| AC-01 | Meal + → AI dish → correct/refine → fractional portion → Save & add; one new recipe/item, prior meal food unchanged | T-02, T-05, T-08 |
| AC-02 | Share URL → unknown yield/drained amount → save → find by ingredient → resolve → add to lunch; no fabricated values at any stage | T-01, T-03, T-06, T-09 |
| AC-03 | Manual multiline sections → correction → rows/text round-trip → optional empty directions → save variation; originals and IDs survive | T-01, T-04, T-09 |
| AC-04 | Same content from library versus meal gets contextual hierarchy and correct Save behavior; no duplicate review | T-05, T-09 |
| AC-05 | Favorites, multi-term/source search, notes and later reuse retain state and require no authoring review | T-04, T-09 |
| AC-06 | Kill process during editing, parsing, generation or commit; recover durable input or exactly one committed result | T-04, T-05, T-08 |
| AC-07 | Catalog/recipe edit, archive and backup restore leave historical meal values and active cooking revision stable | T-01, T-02, T-07, T-10 |
| AC-08 | Rename linked recipe; link still opens and adds no nutrients. Unsupported counted line holds calculation | T-01, T-02 |
| AC-09 | Cooking marks/step/scale survive recreation; end creates no diary item; awake state releases on exit | T-10 |
| AC-10 | Cloud disabled/revoked, unsupported site and offline catalog recovery retain user work and emit no unauthorized requests | T-06, T-08 |
| AC-11 | Whole meal has independent recipes/foods, selected portions and one atomic commit; no replacement of existing siblings | T-05, T-08 |
| AC-12 | 200% text, TalkBack, IME, narrow/landscape layouts and visible action alternatives complete all core journeys | T-09 |

## 6. Motivation & Psychology

The return value is a personal collection that becomes easier to use: familiar food, remembered adaptations and a saved place in preparation. Favorites are organization, not achievement. There are no completion scores, draft nags, acquisition streaks or guilt around missing nutrition. Say “Yield needed” or “Choose the drained weight”, not “Invalid recipe” or “You failed to finish”.

## 7. Relations to Other Features

- **Consumes:** F01/F07 targets and explicit dietary preferences; F02 food search/custom-food creation and nutrient rules; F12 local/BYOK capability execution; F13 persistence, app lock, egress receipts and portability.
- **Feeds:** F03's existing multi-item agenda with frozen portion snapshots; F02 actual entries through its repository when enabled; F13 recipe revisions/drafts/notes/session export. F04 may later consume grocery references and scaled ingredients under its existing reconciliation rules.
- **Boundaries:** Do not introduce a second agenda/diary store, a separate nutrient engine, a seventh AI category, a backend or cloud account. Shopping/pantry UI, Mark eaten, week planning and catalog distribution are not pulled into this delivery. WLO-0157 owns direct ingredient nutrition; WLO-0160 owns source ingestion/normalization.

## 8. Blue Sky Ideas

- **[v1.x, sequenced increment]** Durable authoring/library and ingredient math; then structured import and AI acquisition, under release/egress gates. See technical delivery slices.
- **[v1.x, follow-on]** Basic single-session cooking with recovery.
- **[future]** Counted sub-recipes, multiple cooking sessions and named timers with separate validation contracts.
- **[future]** Recipe-page photos, voice entry, file converters, menus, saved meal combinations and optional images. Each feeds the same draft/content model rather than a second recipe system.

## 9. Guardrails, Privacy & Sensitivity

All content is profile-scoped, local and covered by F13's lock/export rules. Sharing is user-initiated; recipe export excludes diary, private draft prompts and API secrets. Optional personal notes are disclosed before a public Share. Raw fetched HTML is temporary, not a hidden permanent browser archive. Do not fetch trackers/images/scripts or send user context to a recipe website. AI receives only the previewed request and relevant constraints/context; unrelated weight/gut/history records are excluded.

Author/source attribution survives edits and exports. A copied recipe is not automatically open-licensed or eligible for WLO's distributed seed content. Preserve known license metadata; unknown stays unknown. This is a content-preservation rule, not a blanket legal conclusion about republication.

## 10. Open Questions

The functional flows and data-integrity requirements above are specified. These external decisions remain explicit dependencies rather than invented approvals:

- **WLO-0159 q-000041/q-000042:** generic catalog ranking and primary carbohydrate convention remain unanswered. WLO-0160 owns their resolution; use versioned policy inputs and never mix conventions meanwhile.
- **Website egress:** the technical spec proposes one-import authorization with no persistent grant. Add the approved exception to the master consent contract before enabling network import; current R-C4 does not authorize recipe websites.
- **Release and model capability:** R-S3/R-S10 still defer acquisition; model selection must meet the stated validation/capability gates. No particular local model or cloud provider is claimed ready by this document.

These gates do not block implementing durable manual authoring, recipe/library storage or test fixtures. The mockup validates direction, not extraction quality, native accessibility, model suitability or performance; those require the technical acceptance evidence.
