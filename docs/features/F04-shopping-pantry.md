# F04 — Shopping List & Pantry — Functional Specification

---

## Identity

| | |
|---|---|
| **Feature ID** | F04 — Shopping List & Pantry |
| **Provides** | An automatically generated, unit-aware, aisle-learning shopping list derived from the meal plan (F03), plus a local pantry with quantities, expiry and staples auto-deduction — engineered so check-off state survives any plan edit. |
| **User problems solved** | • Transcribing a plan into a list by hand is the chore that kills meal planning; here the list exists the moment the plan does. • Mealime resets your checked-off items when the plan changes — enraging in a crowded aisle; WLO reconciles by delta instead of regenerating. • Everyone buys a fifth jar of cumin because no app knows the cupboard; the pantry knows. • Paprika's brilliant list model is buried under multi-step UI and zero automation — WLO keeps the data model and deletes the friction. |
| **AI consent category** | Core list/pantry engine needs **no AI and no consent** (deterministic). Optional vision helpers (receipt-scan price capture, future pantry photo stock-take) run as on-device OCR/vision in v1/v1.x — **consent-free** per R-C3; a future cloud accuracy boost would consume `food-photo` (making F04 its consumer, again per R-C3). |
| **Primary evidence** | `paprika.md` (the data-model gold standard: unit-aware consolidation "2+3 eggs=5", aisle auto-assignment that learns from corrections, pantry with expiry/purchase-dates and staples auto-uncheck, per-item recipe provenance, multi-list, cook-mode state loss); `mealime.md` (plan→consolidated→aisle-sorted→check-off pipeline, list-reset-on-edit fatal bug, missing pantry deduction as top gap, no cost features); `synthesis.md` §1.3, §2 item 5, §3 (delivery hand-off note), §6 checklist. |

## 1. Purpose & Core Objectives

- F04 is the last mile of the plan: it converts F03's calendar into a store-ready artifact and maintains the on-device truth about what food exists at home. It is the feature most exposed to *rage risk* — it runs one-handed, in a crowded aisle, with cold hands, mid-errand — and therefore the one where edit-stability and one-tap ergonomics are correctness criteria, not polish.
- Core objectives (verifiable):
  - Any F03 date range → consolidated list in **< 1 s**, fully offline, with unit-aware merging (2 eggs + 3 eggs = **5 eggs**; 250 ml + 1 cup milk merges to a single sane quantity).
  - **Check-state is never destroyed by a plan edit**: reconciliation applies deltas; checked items stay checked unless their quantity actually changed.
  - Aisle auto-assignment is correct out of the box for common foods and **learns permanently from every user correction** (≤ 1 drag teaches an item forever).
  - Items marked as staples are **auto-deducted from every generated list** (Paprika's auto-uncheck), and pantry entries carry quantity, expiry date and an out-of-stock flag.
  - Every list item shows its **recipe provenance** (which meals need it, at what scaled amounts) one tap away.
- Benefit to the user: shop once a week, in aisle order, without re-deciding anything, and stop re-buying what you own. Benefit to other features: F04 owns the "what's in the house" ground truth that makes F03's generation pantry-aware, F10's "running low" nudges possible, and cost stats real.

## 2. User Moments — when and how it is used

- **Sunday, post-planning** (couch): tap "Build list" on the week plan → review consolidated items, check against the cupboard in 30 seconds, add extras ("cat food") to a second list.
- **In-store** (supermarket, one-handed, interrupted, possibly offline): the killer flow — walk the aisle-ordered list, tap-to-check, filtered to "to buy".
- **Post-shop** (home, kitchen): "sweep to pantry" — everything purchased lands in inventory with one action; staples auto-marked; fresh items get quick expiry chips.
- **Midweek glance** (kitchen, cooking): "do I have X?" pantry search; "use within 3 days" triage glance while deciding dinner.
- **Seasonal pantry audit** (a few times a year, unhurried): walk the cupboard, fix quantities, flag out-of-stock, prune staples that no longer apply — the maintenance pass that keeps deduction math honest.
- **Budget review** (monthly, [v1.x]): weekly cost bars, staple price trends, cost-per-serving on recipes.
- **Most common flow, end-to-end:** F03 "Build list" → list appears aisle-sorted with consolidated quantities → shop day: open list, tap-tap-tap through aisles (springy check-offs) → home: "Move purchased to pantry" → expiry pre-filled for known staples, chip prompts only for fresh items.

## 3. How It Works — functional mechanics

- **Inputs:** F03 plan date-range requests (ingredients + per-recipe scaled quantities + cook-vs-leftover semantics); manual additions (typed with autocomplete); F01 household defaults; the user's pantry state, staples set and aisle corrections; optional receipt scans / price entries.
- **Consolidation examples (the canonical cases):**

  | Requested across recipes | Merged list result |
  |---|---|
  | eggs ×2 (pancakes) + eggs ×3 (dinner) | **eggs ×5** |
  | milk 250 ml + milk 1 cup | milk **≈487 ml** (dominant display unit) |
  | flour 300 g (bread) + flour 2 cups (pancakes) | grouped under one item, per-recipe amounts shown — **never** a fake cross-dimension conversion |
  | onions: ½ (curry) + 1 (chili) | onions ×1.5, provenance per recipe on tap |

- **Reconciliation rules (the anti-Mealime contract):**

  | Plan change after list exists | List effect | Check state |
  |---|---|---|
  | ingredient unchanged | untouched | preserved |
  | quantity changed | inline "+2" delta chip, still in place | preserved |
  | new ingredient | appended at its aisle, unchecked | — |
  | ingredient removed | struck through, recoverable from list menu | preserved |
  | cook-event count changed | leftover-linked quantities recomputed, deltas shown | preserved |
- **Data model (Paprika's, hardened):**
  - `ListItem {id, canonicalItem, qty, unit, aisle, sources[recipeRef×scaledQty], checked, checkedAt, deltas[]}` — the stable ID is the anchor of reconciliation.
  - `PantryItem {canonicalItem, qty, unit, expiry, addedDate, lastPurchased[], isStaple, outOfStock}`.
  - `List {name, generatedFrom[planRange], items[], archivedRuns[]}` — the default "Shopping" list plus unlimited named lists ("Costco run", "Market Saturday"); the default is renameable but never deletable (Paprika's rule — it anchors the archive), and archived list runs power the purchase-history stats.
- **Processing:**
  - **Consolidation:** merge rules are unit-aware — same unit sums (2+3 eggs=5); convertible units sum in a dominant display unit (250 ml + 1 cup → "≈487 ml"); **incompatible units don't fake it** — they group under one item with per-recipe amounts shown ("flour: 300 g (bread) + 2 cups (pancakes)"), preserving provenance instead of lying with a bogus conversion. A `Consolidate` toggle shows raw per-recipe lines (Paprika's setting).
  - **Reconciliation, not regeneration (the anti-Mealime core):** the list is a *derived-but-stable artifact*. A plan edit produces a diff against current items — quantity unchanged → untouched (check preserved); quantity changed → inline delta chip ("+2", subtle pulse, still checked); new ingredient → appended unchecked; removed ingredient → struck through and recoverable. Nothing is ever cleared silently.
  - **Aisle engine:** a shipped canonical taxonomy (Produce, Dairy, Bakery, Meat & Fish, Frozen, Pantry, Household…), editable wholesale; unknown items assigned by an on-device keyword/embedding model; **every user reassignment is remembered per-item and applied to all future lists** (Paprika's teach-the-system loop). List sort order = the user's own aisle order, drag-reorderable once, respected forever.
  - **Pantry logic:** staples are auto-deducted from every generation; **partial-stock math** ("need 5 eggs, have 3 → buy 2"); "Move to pantry" sweeps checked items into inventory (all, or purchased-only, Paprika's flows); barcode/label-scan at check-in prefills quantity/expiry where data exists (capture stack shared with F02/F13); out-of-stock flags put an item on the next generated list automatically; "last purchased" history powers resurfacing prompts.
  - **Pantry signals F04 emits (consumed downstream):** `expiringSoon` (≤ 3 days, fresh categories only), `runningLow` (quantity under the item's learned threshold or out-of-stock flag), `purchaseCadence` (median days between purchases per item), `stapleMissing` (a marked staple with empty stock). F03 uses the first for generation weighting; F10 turns the rest into quiet nudges; nothing nags more than once per item per week.
  - **Price/cost tracking [v1.x]:** per-item price entry or receipt-photo capture (below); stores price history per item and per shop date → weekly cost totals, unit-price normalization, cost-per-serving rolled back into F03 recipe cards.
  - **AI touchpoints (both optional; on-device OCR/vision is consent-free in v1/v1.x per R-C3 — only a cloud accuracy boost would consume F12 **`food-photo`**):** (1) *receipt scan* — on-device OCR extracts items + prices → line-by-line confirm → prices stored, matched items optionally update pantry quantities. (2) *pantry stock-take [future]* — a cupboard photo drafts inventory entries on-device. **Fallbacks:** both degrade to fully manual entry with autocomplete; the entire feature works with AI toggles off and network off, forever. Cloud vision (BYOK) is only an accuracy booster for hard receipts/photos, surfaced at point of use with a preview of what's sent.
- **Outputs / artifacts:** consolidated check-off lists (per list), pantry inventory with expiry timeline, purchase history, price history + weekly cost rollups, aisle-correction training data, "running low / expiring soon" events for F10, waste/reuse stats inputs.
- **State owned:** list items + check states, aisle taxonomy + learned assignments, pantry inventory, staples set, purchase and price history, list-generation settings, archived list runs.

## 4. User Interaction Model

- **Entry points:** F03 "Build list" action; F10 Daily Hub shopping-day nudge card ("12 items for this week's plan"); home-screen widget (list count by aisle, tap to open — [v1.x], riding F10's framework per R-U12); pantry tab; share-sheet (send any text/URL → parsed item draft); post-shop sweep banner; F03 recipe view ("missing: 3 ingredients" chip → adds them to the list).
- **Happy path (in-store):** open list → single-tap items to check (strikethrough + sink to checked group) → aisle headers collapse as aisles complete → "To buy" filter keeps only what remains → done → banner "Sweep 14 items to pantry?"
- **Primary flows:** *Generate* (F03 date-range → list, < 1 s); *Shop & sweep* (above); *Manual add* (type-ahead → auto-aisle → done in ~3 s); *Stock-take* (pantry → "+ item" → scan or search → qty chips → expiry chips); *Multi-list* (list switcher → "New list" → name chip or text → generate into it or add manually; a merged view shows overlapping items across lists flagged per list).
- **Fallback paths:** (a) *Plan edited mid-shop* — reconciliation banner: "Plan changed: 2 items added, 1 quantity up. Your checks are safe." Applied as deltas, never a reset. (b) *Unknown item* (manual add) — aisle guessed by the on-device model; one drag teaches it permanently. (c) *Offline first run* — everything above works; nothing in F04 requires network, ever.
- **Input minimization — never typed:** the weekly list requires zero input (generated); staples need marking once; the pantry sweep defaults quantities to purchased amounts; expiry prompts only for fresh categories, with a chip row of sensible defaults (+3 d / +7 d / date wheel); manual adds get autocomplete + auto-aisle; prices come from receipt scan or are skipped entirely.
- **Micro-interactions:**
  - Check-off: springy strikethrough draws left-to-right in 150 ms, the item sinks with a settle bounce into the checked section, single soft tick haptic; undo by tapping the ghost — items never vanish abruptly.
  - Aisle completion: when the last item in an aisle is checked, its header collapses with a zipper motion and a slightly deeper haptic — the list physically *shortens* as you walk the store.
  - List completion: the checked section does a gentle "confetti-free" wave (items pulse once, in sequence, 300 ms) and the sweep-to-pantry card rises — celebration without circus (no tamagotchi, per synthesis §4).
  - Sweep to pantry: checked items visually *pour* into the pantry tab icon (shared-element transition); the pantry count odometer rolls up.
  - Expiry: fresh items get tinted dots that breathe (2 s period) only in the "use soon" band — urgency without alarm-red guilt.
  - Reconciliation deltas: inline "+2" chips fade in with a 200 ms scale and a faint outline pulse; a left-swipe on a chip shows the exact plan change that caused it.
  - Aisle drag-teach: the item drags between aisle groups with notch haptics; drop lands with a confirm double-tick and a one-time "WLO will remember this" toast.
  - Price entry [v1.x]: receipt scan animates a line-by-line reveal, each parsed row snapping into place with a micro-tick as OCR resolves it.
  - Widget: the home-screen list widget shows per-aisle counts as tiny stacked bars; checking an item anywhere pulses the widget count down — ambient progress without opening the app.
- **Data-quality gating:** waste and cost-trend stats are **refused** until ≥ 4 weeks of pantry/purchase data exist ("not enough history to claim this"); unit prices need ≥ 3 purchases of an item; a "you'll run out by Thursday" projection states its basis (consumption estimate from purchase cadence) or doesn't appear; receipt OCR below a confidence threshold asks for confirmation line-by-line rather than bulk-committing.

## 5. What the User Gets Out

- **The aisle-ordered list:** consolidated quantities, provenance chips (tap an item → "for: Thai curry ×4 servings, Weekend eggs ×2"), check-state that survives everything, multi-list switcher, archive of past lists.
- **The pantry:** inventory with quantities, expiry timeline (color-banded: this week / next week / later), out-of-stock flags, "last purchased 5 weeks ago" history, search ("do we have feta?").
- **Pantry turnover ring:** items classed by how fast they're consumed (fast / medium / never) from purchase and sweep history — the quiet diagnostic for "why do I own three half-used grains?"
- **Cost panel [v1.x]:** this week's list total, cost per serving per recipe (fed back to F03), price trend per staple ("olive oil +12 % since June"), monthly grocery spend vs trailing average. All cost math is explicitly a private mirror of the user's own receipts — no market averages, no "shoppers like you", no price shaming.
- **Waste & reuse stats:** ingredient-overlap savings (from F03 plans), items used before expiry vs discarded (self-reported at sweep, one tap) — neutral framing, never a guilt ledger.
- **"Use it or plan it" cards:** expiring-soon items each carry one actionable chip — "add to list" (rebuy), "plan around it" (hands to F03), "mark used" — so every pantry signal has a one-tap resolution instead of ambient anxiety.
- **Provenance rule:** every derived number (buy-2 deduction, run-out projection, weekly cost) carries `derived` provenance and a "how we got here" breakdown (pantry stock, plan needs, consumption estimate). Deductions that surprised the user are the #1 trust risk in this feature; the breakdown must be one tap from the number, never buried in settings.
- **Visualizations owned by F04:** expiry timeline strip, weekly cost bars, staple price sparklines, pantry turnover ring (fast / medium / never).

## 6. Motivation & Psychology

- The itch this scratches is **competence**: the app knew what you needed, the shop took 20 minutes, nothing got thrown out — a weekly proof the system works. The list-completion wave and the pantry sweep are the two designed "closed loop" moments.
- The secondary itch is **closure in the aisle**: a list that ends, visibly, aisle by aisle, beats any productivity app — the collapse animation exists because watching the remaining work shrink is the reward.
- Gamification hooks (local events only; F11 owns mechanics): list-completed event, planning→shopping→sweep loop streak, pantry-turnover personal bests (F04 computes, F11 decorates). No virtual pet, no guilt counter for discarded items — waste data is framed as *information with an exit ramp* ("buy 2 fewer eggs next plan" is a suggestion chip on the next generation).
- The deeper motivational bet: the list and pantry are where WLO touches the user's *money and home*, not just their body. Respecting both — accurate quantities, no manipulative urgency, honest cost math — is the trust loop that keeps the weight-side features believed.
- Tone rules: waste language is always forward-looking and optional; price tracking never scolds ("that's 12 % more than usual" — no judgment about whether to buy); nothing in-aisle demands attention (no nagging notifications while the phone's context suggests shopping).

## 7. Relations to Other Features

- **Consumes from:** **F03** (plan date-ranges, per-recipe scaled ingredients, cook-vs-leftover semantics, plan-edit deltas for reconciliation); **F01** (household size → default portion multipliers; budget band for cost hints); **F02**'s food data (item names/nutrition hints for autocomplete); **F12** (`food-photo` consent only for a future cloud accuracy boost — on-device OCR/vision is consent-free per R-C3); **F13** (export/import of pantry + lists; shared barcode/label-scan capture stack).
- **Feeds into:** **F03** (pantry stock + expiring-soon for pantry-aware generation; cost-per-serving back into recipe cards); **F10** ("running low", "expires in 3 days", shopping-day nudge cards); **F11** (loop-streak and list-completion hook events); **F13** (pantry and price history in export bundles).
- **Shared concepts:** Provenance, Consent, Targets (budget band), Logs (purchase history).
- **Conflict/boundary notes:** F04 **never mutates the plan** — it derives, reconciles and suggests. The list is F04-owned state even though it is plan-derived; this ownership split is precisely what makes check-state stability possible. Barcode scanning technology is shared with F02's capture stack (F13/F02 decision), but pantry check-in flows are F04's.

## 8. Blue Sky Ideas

- **[v1] Reconciliation as a first-class interaction.** Plan edits produce visible, undoable deltas on the list — Mealime's fatal bug inverted into a trust feature. The "your checks are safe" banner is small; the loyalty it buys is not.
- **[v1] Aisle engine that visibly learns.** The first correction teaches forever; a tiny "learned" checkmark on the aisle chip shows the system adapting — the app demonstrably gets more *yours* every week (Paprika's loop, made perceptible).
- **[v1] Staples with partial-stock math.** "Need 5, have 3 → buy 2" — the pantry participates in the list, not just as an on/off filter.
- **[v1.x] Receipt-photo price capture.** Snap the receipt at home → on-device OCR drafts item+price lines → confirm → the weekly cost panel starts existing with near-zero ongoing effort (F12 `food-photo` for optional cloud assist on crumpled receipts; manual entry fallback).
- **[v1.x] Cost-per-serving feedback loop.** Recipe cards (F03) gain "€2.10/plate" from real purchase prices; the moonshot solver can later optimize on it. Mealime claimed budget wins; WLO *shows the receipts* — literally.
- **[v1.x] Expiry triage board.** A "use within 3 days" strip that one-swipes into F03 as a generation constraint ("replan Wednesday–Friday around these"). The planner and the cupboard finally talk.
- **[v1.x] Shopping-rhythm forecast.** From purchase cadence, predict the next shop date and pre-build the list the night before ("Friday list is ready — 11 items, ~€24 estimated"). Zero-tap list existence.
- **[future] Pantry photo stock-take.** One photo of the cupboard/shelf → on-device detector drafts inventory (items + rough quantities) → confirm in a grid. Never perfect, always drafted; cloud vision only under `food-photo` consent.
- **[moonshot] Serverless household list sync.** Partners' phones sync lists and pantry over Nearby Share / Wi-Fi Direct with a CRDT merge — check-offs from two people in the same store converge without any server, staying 100 % inside the no-SaaS wall. The one delivery-adjacent feature that is architecturally pure.
- **[moonshot] Remnant recipes.** "Cook something from what expires this week" → F03's solver synthesizes a recipe from pantry remnants + staples, drafted on-device, saved only on confirm. The zero-waste endgame of the plan↔pantry loop.

## 9. Guardrails, Privacy & Sensitivity

- **No delivery integrations.** Instacart-style hand-off is rejected for v1 and beyond (conflicts with no-SaaS; synthesis §3). The only sanctioned path: export/share the list as text/CSV/JSON to wherever the user wants it — a format, not a partnership.
- Pantry contents, prices and shopping patterns are sensitive behavioral data: local-only, inside F13's encrypted store, never analyzed anywhere but on-device; receipt photos are transient — processed on-device and discarded unless the user saves them.
- Price capture and all cost math are user-data byproducts, never a market-research channel: no aggregated price data can leave the device, because nothing leaves the device except explicit F13 exports.
- The list must remain **usable completely offline, forever** — no feature may ever require connectivity, including aisle suggestions and barcode lookups (F13's local cache covers those).
- Free-forever rule applied: cost tracking, multi-list, pantry, aisle learning — all present or future capabilities here are and remain ungated; nothing in this spec may become a "Pro" feature because there is no Pro.
- List archives and purchase history are retained by default but fully clearable per-list and per-category; clearing is explicit and undo-protected (F13's destructive-action pattern), never bundled with unrelated resets.

## 10. Open Questions

- **Consent-category mapping for receipt/stock-take vision:** F12's six-category taxonomy is frozen and F04 is not a listed consumer; F12's contract says capabilities fitting no category are master-doc decisions. Options: extend `food-photo` consumers to include F04 (receipt/label OCR are close cousins), define a new category, or keep receipt OCR consent-free as plain on-device text recognition (F12 treats plain DB lookups as non-AI precedent). Needs a master-doc ruling before the [v1.x] receipt feature. *(Resolved: R-C3 — on-device OCR/vision is consent-free in v1/v1.x; a future cloud assist consumes `food-photo`.)*
- **Consolidation display unit policy:** when units are convertible (ml/cup, g/oz), which is "dominant" — the first-seen, the largest, or the user's locale/metric preference? Current lean: locale preference, overridable per item.
- **Barcode check-in for pantry in v1 vs v1.x:** high convenience, but depends on F02/F13's scanner stack timing; v1 could ship without it (expiry chip prompts suffice) — confirm sequencing.
- **Partial-stock deduction defaults:** should "have 3 eggs" auto-deduct by default (with a global off switch), or opt-in per staple? Default-on is smarter but risks surprising deductions; needs a UX call. *(Resolved: R-S5 — off by default globally; one-time prompt at first generation with remember-choice.)*
- **Price entry ergonomics:** per-item numeric entry in-store is friction; is receipt-scan [v1.x] the *only* sanctioned price path, or do we also support item-price tap-in at check-off time?
- **Sweep default scope:** "Move purchased to pantry" defaults to purchased-only, but bulk-flour/oil shoppers want sweep-all; decide whether the default is smart (perishable detection) or a persisted per-user toggle.
- **Reconciliation edge case:** what happens when a plan edit changes *servings* (not ingredients)? Current lean: proportional quantity deltas with a single summary chip ("curry ×2 → ×4: +1 onion, +200 g rice"), not per-ingredient noise. Needs a UX review with F03.
- **Waste self-report:** discard marking at pantry sweep is honest but adds a tap; ship as an optional "sweep review" step, or infer silently and ask only weekly? Data-quality implications for the waste stat need deciding.
- **Aisle taxonomy localization:** ship one canonical taxonomy with auto-translation of aisle names per locale, or per-locale seed taxonomies (German supermarkets differ structurally)?
- **Widget scope in v1:** the home-screen list widget is referenced by openScale precedent (synthesis §3) — confirm it lands with F04 v1 or waits for the F10 widget framework so the two don't build parallel widget stacks. *(Resolved: R-U12 — one widget framework owned by F10; the F04 list widget rides it [v1.x].)*
