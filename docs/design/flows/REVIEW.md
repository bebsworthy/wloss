# WLO flows — full-screen design review

*2026-09-11 · every one of the 88 phone frames (41 wireframes + 47 annotated
mocks) captured in a browser at full resolution and judged individually.
Method and the six axes are in §1; per-screen findings in §2; the prioritized
improvement plan is §3. Companion: ticket for the fix round (see git log).*

## 1. Method & verdict

Each frame was screenshotted from the served prototypes
(`python3 -m http.server`, phone frames at native 390×844) and assessed on the
six review axes:

1. **Complete** — does the screen carry everything its spec (§4 interaction
   model, frozen anatomies, rulings) requires?
2. **Purpose** — does it serve its one moment (FEATURES §2.4)?
3. **Understandable** — can a first-time reader parse it without the caption?
4. **Right information promoted** — hierarchy, density, Adaptive Day Model.
5. **IA** — does the surface sit where IA.md says it lives, with the right
   entries/exits?
6. **Majestic & beautiful** — the design bar: minimalist, dense with stats,
   rich micro-interactions, zero guilt.

**Verdict: the set is conceptually excellent and passes the design bar on
tone, provenance, and rulings — no screen contradicts a frozen R-\* ruling,
and several artifacts (03's "how we got here" sheet, 04's gain-day variant,
06's heatmap legend, 07's pinned-total schedule) are genuinely best-in-class.
What stands between the mocks and "majestic" is execution hygiene: a handful
of rendering bugs (overlapping text layers, clipped content under the nav
bar, one near-unreadable dark screen), one cross-flow data-coherence problem
(lb vs kg personas, unreconciled budget numbers), and annotation-pin
discipline.**

| Flow | Completeness vs spec | Layout quality | Clarity | Beauty | Standout defect |
|---|---|---|---|---|---|
| 01 Day loop | high | good | high | high | ring↔diary numbers don't reconcile; rail clipped |
| 02 Photo ladder | high | good | high | high | offline screen ~unreadable; garbled save-arc numerals |
| 03 Check-in | high | fair | high | high | frozen anatomy over-stuffed at 390 dp; ghost numerals |
| 04 Weigh-in/Archive | high | good | high | **highest** | stray "92.1" kg value; unit mixing in outlier guard |
| 05 Plan→shop | high | good | high | high | badge on wrong nav tab; minor copy bugs |
| 06 Report card/Bristol | high | good | high | high | kg persona; bottom cards clipped |
| 07 Onboarding | high | **best** | high | high | placeholder macro dots; wall card in default state |

## 2. Per-screen findings

Legend: ✅ strength · ⚠ defect · · minor. Screen IDs: `w` = wireframe,
`m` = annotated mock (numbered left→right in each page's stage).

### Flow 01 — The Day loop (F10)

- **w1–w4** ✅ Adaptive Day Model card order correct in all four day states;
  R-U17 hero promotion drawn in w4; evening keeps recap in-app (R-U9).
- **w5** ✅ night layout, nudge, widget triptych. · lower half empty.
- **m1 Morning** ✅ weigh-in card leads with teal edge + "Step on, snap, or
  type" (R-U15 in four words); hero 179.1 with `DERIVED · EWMA α 0.15`;
  ring `ADAPTIVE · CHECK-IN SEP 8`; provenance on every number.
  ⚠ quick-action rail (in the HTML between plan card and navbar) is **clipped
  below the 844 px fold** — the first-screen contract says "zero scrolling",
  so the rail must fit or the contract is broken. ⚠ macro row wraps
  mid-unit ("P 92/165␤g"). ⚠ header report-ready pill truncated ("r•y").
  ⚠ ring shows 1,180 consumed at 09:41 with nothing logged yet (story bug).
  · Archive nav icon is a plain square, not the R-D9 stacked-outline glyph
  (true of every nav in the set).
- **m2 Midday** ✅ ring leads; "lunch logged in 6 s · photo" micro-stat;
  trend demoted; diary chips `AI-est`/`DB-verified`; day-status dots.
  ⚠ ring math vs diary incoherent (1,660 consumed vs 380+512=892 logged).
  ⚠ a teal button sliver is clipped under the nav bar — needs underlap
  treatment or scroll padding.
- **m3 Evening** ✅ Close-the-day card; `est ±30 %` chip; "Evening in band —
  The week is what compounds" is the set's best copy. Meals 3·1,910 matches
  the ring here (proving m1/m2 are the outliers). · bottom 45 % empty —
  sparse vs the density bar. · "1,880 kcal" on tomorrow's plan is the plan
  sum, not the target — label it.
- **m4 Check-in day** ✅ frozen anatomy complete and ordered (status → trend
  → adherence → burn → proposal → Apply/Keep/Discuss); "Computed on your
  device · how we got here". ⚠ ring copy "Yesterday's proposal sits one tap
  away" contradicts the ready-to-Apply proposal above it (flow 03 m2 has the
  correct wording — reuse it).
- **m5 Off-app** ✅ invitation-tone nudge, ≤2 actions, silent-channel caption,
  lock-screen redaction dots. · widget floats on app background — a home-
  screen backdrop would make it read as a widget.

### Flow 02 — Photo ladder (F02)

- **w1–w6** ✅ all six states present and ordered per F02 §4; ladder
  equality explicit; offline framed as non-state.
- **m1 Viewfinder** ✅ capture-over-context chip ("Lunch · from today's
  plan") + budget chip; five equal ladder rungs; shutter hold-actions
  caption. ⚠ budget "1,277 left" matches nothing else in the same-day story
  (flow 01: 720 morning / 240 midday). · abstract food blobs are barren —
  a plate/bowls composition would sell the segmentation mask.
- **m2 Analyzing** ✅ frozen frame + shimmer; dealt vs ghosted chips;
  "reading the plate — on-device · recognizer v1.4" inside the frame;
  "cancel is always available · no spinner, no server wait". · context chip
  from m1 disappears.
- **m3 Result card** ✅ the full correction loop: per-item DB provenance
  (USDA FDC #, OFF cache), confidence ring 0.74, portion slider with detents,
  hint field, Save live + "photo discarded at save (R-U14) · median log 8 s".
  · "kcal / conf 0.74" cramped inside the ring.
- **m4 Low confidence** ✅ amber irregular-stroke ring 0.46; "rough guess —
  adjust what's wrong"; two least-confident chips pre-expanded with variant
  chips; "a rough log beats no log · nothing here blocks you".
  ⚠ explainer sentence truncated mid-clause: "…the ring's stroke is
  irregular because the estimate is".
- **m5 Offline** ⚠ **worst defect in the set: the screen renders at a
  fraction of normal brightness** — several rows are near-unreadable, which
  contradicts "offline is a non-state". The fallback-trail narrative itself
  (barcode → miss → label OCR → local DB → cached) is excellent.
- **m6 Save arc** ✅ personal accuracy stat ("your scans: ±12 % · best on
  breakfast"); diary continuity with m3 (roti 35 → 40 g); Food Memory card.
  ⚠ hero numerals **overlap**: "1,204 left" printed over a ghost "720 left";
  and the semantics are wrong (1,204 is consumed; ring at 63 % ⇒ left =
  696). ⚠ Food Memory CTA reads "Log again · 2 tap".

### Flow 03 — Sunday check-in (F07→F01)

- **w1–w6** ✅ six beats, frozen anatomy order respected, held variant and
  plan diff both scoped.
- **m1 Mid-reveal** ✅ four-beat choreography captioned with ms budgets.
  ⚠ "on pace" collides with the −0.6 kg/wk chip.
- **m2 Complete card** ✅ inline Sunday fix (Logged/Skipped/Fasted one tap);
  "The ring keeps yesterday's target until you tap Apply — nothing changes
  behind your back." ⚠ **layout: the frozen anatomy is over-stuffed at
  390 dp** — header wraps to two lines, chips crowd the right edge, the
  "+70 vs last wk" chip stacks vertically, "1,900 kcal" and "P 165 +5"
  wrap mid-row. Needs a deliberate density pass (R-D1/D3 vocabulary), not
  smaller text everywhere.
  ⚠ ghost-numeral bug: "1,045" overlaps the TODAY card header/body (same
  two-layer defect as 02 m6; recurs in m4/m5).
- **m3 How we got here** ✅ **the trust centerpiece**: four-term breakdown
  that actually sums (1,690+120+630+0=2,440); window + excluded rough days
  named; "Context, never credit" (R-B2); constants published (R-A1);
  doubles as R-C7's local explainer. · pins cover three row labels.
- **m4 Apply** ✅ count-up caption, ledger entry #13 (browsable & exportable,
  F13), tap-to-revert, provenance re-stamp "check-in Sep 6 → Sep 13",
  hand-off links (F03 regenerates, F04 rebuilds, cone re-blooms → 07).
  ⚠ ghost numeral on the TODAY card again.
- **m5 Held** ✅ exemplary tone: "no debt, no penalty", auto-release, "the
  card waits up to 7 days, zero guilt"; no proposal row; burn frozen at last
  good; amber discipline kept. ⚠ ghost numeral; bottom caption clipped.
- **m6 Plan diff** ✅ strikethrough diffs; revert writes v14 ("history is
  never rewritten"); version history attributes engine vs you.
  · hand-off text clips at the nav; pin overlaps the "now" chip.

### Flow 04 — Weigh-in + trend (F06) & Archive (F08)

- **w1–w8** ✅ all eight states; R-U15 three equal paths; R-U16 zero-photo
  discipline throughout (verified: no `<img>` on the page).
- **m1 Entry** ✅ Scale·Snap·Type equal segments; assist cards *above* the
  pad ("never instead"); ±0.1 steppers; date chips; "scale path
  auto-confirms: 0 taps". ⚠ stray persona value: scale card shows "92.1
  arriving…" — a kg number in the lb persona (mirror is 81.2).
- **m2 Confirmation** ✅ **the set's best artifact**: frozen anatomy (trend
  hero odometer, raw as small print, ribbon) plus the gain-day variant —
  "a gain day feels identical in effort, different only in the small print."
- **m3 Outlier guard** ✅ one line, Keep/Correct, "an admitted typo is
  fixed, not judged". ⚠ unit mixing: lb persona, sheet says "4.2 kg above
  yesterday" (deliberately spec-verbatim, but should localize to persona).
  · pin floats unanchored; dimmed background mostly empty.
- **m4 Trend history** ✅ range chips, discreet eye, honest smoothing chips,
  body-fat `estimated · impedance ±3–4 %`, BMI "on request — ranges, not
  verdicts", week-1 dots-only teaching state. ⚠ "Progress number" row wraps
  awkwardly; logbook caption clips under the nav.
- **m5 Lock gate** ✅ correct restraint; FLAG_SECURE / auto-relock /
  deep-links-land-here captions complete. · the lock glyph is amber — amber
  means *held* in WLO's semantic palette; a neutral lock is more on-system.
- **m6 Capture ritual** ✅ pose-gated skeleton with amber failing segments;
  rejection names cause + fix ("left ankle out of frame — step back 30 cm");
  silent-shutter caption.
- **m7 Delta card** ✅ exemplary R-U16 payoff: faceless vector outlines,
  within-noise deltas greyed ("no fake number"), quiet-scale card "calm,
  not carnival", "share = faceless vector strip only".
- **m8 Timeline** ✅ "a coverage stat, not a duty streak"; gaps as faint
  ellipsis cells; COMPARE wipe divider; COLLECTING "capture, never conclude".
  · unexplained affordances: dashed border on one cell, amber dot on
  another — need a legend or removal.

### Flow 05 — Plan → shop (F03/F04)

- **w1–w6** ✅ six states; 390 dp one-card-per-day reflow reasoned in the
  caption; week-level editing per R-U11.
- **m1 Week grid** ✅ one-tab pipeline (Plan·Recipes·List·Pantry); "Why this
  plan" economics (€4.10 under an unshared week); deterministic · on-device
  chip (R-S10); leftover tupperware connector; "Friday's plan met real life
  — replanned. Nothing owed." ⚠ copy typo: "AIry influence: off".
- **m2 Swap sheet** ✅ top-3 ranked to restore the day; per-swap delta
  chips; day-ring preview ("the week's targets never move"); allergen
  respect; "list reconciles silently — no checked item is touched".
- **m3 In-store list** ✅ aisle order is the user's; unit-honest rows
  (milk ≈ 487 ml dominant unit); checked-feta strikethrough with ghost-undo;
  "works with the network off, forever". ⚠ header line truncated
  ("…forever · targets"); three pins sit on row text; bottom row clipped.
- **m4 Reconciliation** ✅ the anti-Mealime banner, verbatim from F04 §4;
  per-item delta chips (+2); honest "prototype" labeling of the open
  servings-edit pattern (FEATURES §6.8 #8). · pin sits on the banner text.
- **m5 Completion + sweep** ✅ "celebration is geometry" (300 ms sequence
  pulse, confetti-free); sweep scoping; waste review "neutral, always —
  waste is information with an exit ramp"; € ledger refuses <4 weeks of
  history. ⚠ **nav badge "127" (pantry count) renders on the Digestion
  tab** — wrong tab.
- **m6 Pantry** ✅ "do we have feta?" search; banded USE SOON timeline
  ("later bands sit still"); one actionable card per expiring item ("never
  ambient anxiety"); cadence-derived run-out dates; R-S5 caption.
  · two amber-edged cards stacked reads heavy; bottom caption clips.

### Flow 06 — Report card + Bristol (F11/F09)

- **w1–w6** ✅ reveal/held anatomy/share composer/two-tap log/correction/
  couch stats all scoped.
- **m1 Reveal** ✅ six stamps flip 1→6; GUT renders "— collecting", never a
  failure; composite disables itself on held weeks; PR banner "deliberately
  not confetti". Grade stamps match ratified R-D7 (minimal geometric,
  numbers-first).
- **m2 Held anatomy** ✅ "Why / what would unlock it — today still counts";
  "Dismiss — held is fine"; rubric version + quality score published;
  "no streak-loss notifications, ever". · Dismiss button wraps to two
  lines (uneven with its neighbor); bottom captions clip.
- **m3 Share composer** ✅ fixed-4:5 preview ("an estimate, not a promise");
  per-domain share toggles; Archive locked — "a pixel path does not exist".
- **m4 Bristol happy path** ✅ "two taps to done"; warm education line
  ("dinner's 31 g of fiber is landing"); confidence stored on entry; zone
  halo; "no sound — bathroom". · dimmed Hub hero shows kg (global persona
  issue); pain chip placement matches R-D8.
- **m5 Correction** ✅ toast "Noted — tunes your on-device model"; prior
  preserved ("was Type 4 · 82 %"); provenance "AI-suggested, corrected";
  R-B6 correction-cache caption.
- **m6 Couch stats** ✅ heatmap legend "dashed = quiet days, not gaps
  (R-U13)" is textbook; press-bubble auto-redacted; fiber ring "an
  invitation, not an alarm"; doctor card "no diagnosis, no severity
  scoring". · four amber accents on one screen dilute the amber budget;
  bottom cards clip under the nav.

### Flow 07 — Onboarding (F01)

- **w1–w7** ✅ all steps; permissions deferred; Start as first write.
- **m1 Welcome** ✅ proof-before-ask; no-account chips; equal import branch;
  "'later' never 'incomplete'"; the-next-7-steps table. Best first
  impression in the set.
- **m2 Goal** ✅ per-field provenance; pace triple-translation (0.5 % ≈
  0.46 kg ≈ −510 kcal/day); dates→pace; THE WALL counter-offer card.
  · the wall's explanation shows in the *default* state — it belongs to
  the pushed-past-1.0 % state.
- **m3 Forecast bloom** ✅ honest dopamine: bands, range dates, "WHY THE
  CURVE BENDS — deceleration is modeled, not drawn", tap-band assumptions,
  cold-start links to flows 03/04. Arithmetic checks (14.4 kg @ 0.46 %/wk
  ⇒ May 6). · the "shimmer" annotation watermark sits on the chart like
  stray UI.
- **m4 Templates** ✅ rules in plain language; keto surface-preview swap;
  "re-fits the plan as v2, nothing is write-once". ⚠ High-Protein card's
  macro dots have no C/F values (placeholder look).
- **m5 Quiz** ✅ mid-swipe demo; pile odometer; hold-for-why-we-ask;
  "8 swipes here, not 40 (R-S6)". · the mid-swipe card is cropped
  mid-word, which reads accidental; top half of the frame is empty.
- **m6 Schedule** ✅ **the pinned invariant done right**: "5 × 1,560 +
  2 × 2,400 = 12,600 = 1,800 × 7"; floor refused, not clamped; sibling
  compensation in real time. · bar value labels are dim annotation-style.
- **m7 Milestones + Start** ✅ range-only dates while DEVELOPING; renameable
  rungs; plan-v1 receipt; notification prompt deferred to the first log.
  · ladder bands overlap (honest but reads unsorted); "Browse the Studio
  first" ghost clips at the frame bottom.

## 3. Improvement plan

> **### Resolution — fix round 1 (2026-09-11, same day): every item below is
> fixed and re-verified in the browser.** Notes and corrections:
>
> - **P0-1 "ghost numerals" — real, root cause found deeper than the review
>   guessed:** `.ring-big` was defined only in flow 01's local `<style>`, so
>   the ring collapsed to 0×0 in flows 02/03 and its absolutely-centered
>   numerals escaped over the card text. Fixed by promoting `.ring-big` to
>   `_wlo.css`. (The original captures were also blurred by smooth-scroll —
>   the review pipeline now disables it.)
> - **P0-2 offline darkness — real, root cause was z-order, not luminance:**
>   `.sheet` painted *under* `.sheet-wrap`'s 60 % black overlay. Fixed with
>   `z-index: 45` on `.sheet`; the screen now renders at full theme
>   brightness.
> - **Corrections to §2 (capture/misread artifacts, code was already
>   correct):** 02 m6's Food Memory CTA already read "Log again · 1 tap";
>   05 m1's "AIry influence" was actually "pantry influence" with a pin
>   sitting on the word. §2 is kept as the historical first-pass record.
> - **Systemic fixes shipped in `_wlo.css`:** navbar pinned absolutely to
>   the frame bottom (kills the floating-nav void on short screens), 96 px
>   screen scroll-clearance, `.delta` chips never wrap mid-token, R-D9
>   Archive glyph primitive (`.ic-arch`), `.sheet` z-order.
> - **Persona/unit coherence:** the main persona is lb across flows 01–06
>   (all kg/wk labels converted, the "92.1" stray value and the kg outlier
>   guard fixed, share card now −9.2 lb); flow 07 is a *deliberately
>   distinct* metric onboarding persona, noted as such in its page meta, and
>   flow 02's meta documents how its noon (the dal-chawal-thali branch)
>   relates to flow 01's.
> - All ~25 pin positions that sat on text were moved to whitespace; per-
>   frame height trims put every frame's key content clear of the nav.

### P0 — correctness (fix before the set is shown to anyone)

1. **Ghost-numeral overlap on ring cards** (02 m6, 03 m2/m4/m5): the
   frozen "mid-animation" states render two text layers simultaneously.
   Give the hero numeral a single source layer; represent mid-count states
   by one number + a caption, not stacked layers.
2. **Offline screen luminance** (02 m5): re-render at full theme
   brightness (offline is a non-state — F02 §9); re-place pins 20–22.
3. **Truncated copy**:
   02 m4 explainer sentence; 05 m3 header ("…forever · targets");
   01 m1 report-ready pill ("r•y"); 04 m4 logbook caption.
4. **One persona, one unit story**: flows 01/02/04/05 run lb
   (179.1 lb), flows 03/06/07 run kg (178.9/92.4 kg). Either commit to one
   persona across all seven flows or give each flow page a one-line
   "persona" note; fix 04 m1's stray "92.1", reconcile flow 02's budget
   (1,277/696 vs 01's 720/240), and localize 04 m3's guard copy to lb.

### P1 — compliance & comprehension

5. **Archive nav icon → R-D9 glyph** in every navbar (all 7 flows) and the
   Archive surfaces; the current plain square contradicts the ratified
   ruling (abstract stacked-outline, never a camera/body glyph).
6. **Check-in card 390 dp density pass** (03 m2): two-column value rows,
   chip truncation rules, no mid-row wraps; resolve the "on pace" collision
   (03 m1/m2). The frozen anatomy's *order* is untouchable; its row
   rendering is not.
7. **Systematic bottom-clip fix**: ~13 frames have content clipped under
   the fixed navbar (01 m1's rail is entirely hidden; also 01 m2, 03 m5/m6,
   04 m4, 05 m3/m4/m6, 06 m2/m3/m6, 07 m7). Add nav underlap fade +
   scroll-padding to `_wlo.css` and audit each frame's content height
   against 844 px; restore the quick-action rail to 01 m1's visible area
   (the first-screen contract requires it).
8. **Copy fixes**: "AIry influence" → "AI influence" (05 m1); "Log again ·
   2 tap" → "Log again · 1 tap" (02 m6); reuse flow 03 m2's ring wording in
   01 m4; label 01 m3's "1,880 kcal" as plan sum.
9. **Move the pantry badge** (05 m5) from the Digestion tab to Plan (or
   drop it; the list itself already communicates count).

### P2 — polish (the "majestic" pass)

10. **Pin discipline**: ~20 pins overlap the content they annotate
    (04 m1 pin 1, 05 m3 pins 13–15, 06 m6 pins 23–28, 07 m4 pins 14–15,
    etc.). Convention: pins live on whitespace or connect via a 1 px leader
    line; never on glyphs/numerals.
11. **Composition balance**: sparse bottom halves in 01 m3/m5 and 02 m5
    vs the density bar — either pull content up or add an intentional
    quiet-zone device (e.g. the diary slice in evening).
12. **Amber budget audit**: 06 m6 carries four amber accents; 04 m5's lock
    glyph should be neutral (amber = held/attention only).
13. **Viewfinder composition** (02 m1): replace the abstract blobs with a
    plate/bowl arrangement so the segmentation mask reads as food.
14. **Widget context** (01 m5): draw a hint of home-screen wallpaper behind
    the 4×2 widget.
15. **Flow 07 nits**: High-Protein macro values; wall card only in the
    pushed state; quiz mid-swipe card fully inside the frame; milestone
    ladder band sort; un-clip the Studio ghost button; chart "shimmer"
    watermark moved to the caption.
16. **04 m8 affordance legend**: explain (or drop) the dashed timeline cell
    and the amber dot.

### What must *not* change

The tone system, provenance-chip discipline, R-U13/R-U15/R-U16 execution,
"celebration is geometry", the trust artifacts (ledger, how-we-got-here,
revert-as-v14, rubric publishing, kind-haptic vocabulary), and each frozen
anatomy's row order are the set's strengths — the fix round is hygiene, not
redesign.
