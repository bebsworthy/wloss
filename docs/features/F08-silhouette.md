# F08 — Silhouette Tracker — Functional Specification

## Identity

| | |
|---|---|
| **Feature ID** | F08 — Silhouette Tracker |
| **Provides** | A guided body-scan capture ritual that derives **vector silhouettes — never photographs** — plus an on-device timeline and comparison studio that shows shape change the scale hides. |
| **User problems solved** | • "The scale hasn't moved in three weeks — is any of this working?" (it shows the centimetres) • "I will not keep body photos in my gallery, my cloud camera roll, or anywhere at all — the app itself must not keep them either" • "Progress-photo apps paywall my own history or quietly phone home" • "Weekly tape measurements are a ritual I abandon" |
| **AI consent category** | Owns the semantics of F12 category `silhouette` — a deliberate near-never toggle. All processing (pose guidance, silhouette derivation) runs on-device; **v1 ships no cloud silhouette code path at all**, so the toggle exists only as a switch for hypothetical future derivations, defaults OFF, and is framed as not-recommended inside F12. **No pixel is ever persisted (R-U16).** |
| **Primary evidence** | `docs/research/methreesixty.md` (capture ritual, green skeleton, reference scan, FutureMe, SDK + history-paywall anti-patterns) · `docs/research/hevy.md` (private progress photos, side-by-side compare) · `docs/research/gyroscope.md` (card-as-artifact sharing) · `docs/research/synthesis.md` §1.6, §2 Tier B |

## 1. Purpose & Core Objectives

Body-shape change is the strongest antidote to scale despair: the same fat loss that
reads as +0.0 kg on a noisy scale reads unambiguously in a waist silhouette. F08 is the
capture ritual and the visual memory of the journey — built so that a privacy-conscious
user never has to choose between "seeing it" and "keeping it safe". It exists to serve
the user directly (proof, motivation, comparison) and the app indirectly: it gives F06
a second, independent signal of progress and gives F10/F11 emotionally powerful moments
that no chart can produce.

Core objectives (verifiable):

- A full capture session (front + side — the two mandatory angles — plus
  optional back, R-U6) completes in ≤60 s from app unlock to
  saved, with **zero text input**.
- 100 % of pixel processing happens in memory, on-device — **no pixel is ever
  stored**: the timeline keeps vector outlines and derived numbers, never
  photographs (R-U16). No network call exists anywhere in
  the silhouette code path — the openScale "provable privacy" posture, applied per-module.
- Pose-gated capture: a capture enters the timeline only when skeleton alignment is
  valid; every rejection names the failing guardrail *and* its fix.
- Comparison claims (deltas, "progress") require ≥3 comparable captures spanning ≥14 days;
  anything less renders as "collecting", never as a conclusion.
- Every derived number (width delta, estimated circumference) carries
  measured / estimated / derived provenance and a "how we got here" sheet.

## 2. User Moments — when and how it is used

- **Cadence:** capture is a *weekly ritual* (default; user picks day + time window at
  first capture). Browsing and comparison are couch-context, typically right after a
  weigh-in in F06 or when motivation dips. Sharing is episodic, at milestones.
- **Physical & emotional context:** bathroom or bedroom, phone handheld at arm's length
  or propped against a shelf. This is a vulnerable moment — the UI must be fast,
  clinical-warm, and never linger or comment on the body.
- **The single most common flow:** (1) F10 card "Weekly check-in is ready" → (2)
  biometric unlock of the Archive → (3) capture checklist pre-clears (lighting, plain
  wall, distance; clothing/hair reminders shown as static chips) → (4) guided
  poses (front, side, optional back — R-U6), skeleton snapping green per segment → (5) save → a 5-second delta card vs the
  user's reference capture. Done.

## 3. How It Works — functional mechanics

**Inputs**

- Camera frames per angle: front → side are mandatory, back is optional (R-U6);
  comparisons annotate which angles are present. Device orientation and framing
  heuristics (subject pixel height, tilt).
- From F06: trend weight and measured circumferences (correlation input).
- From F01: goal weight and journey start date (framing and projections).
- From F12: consent state; from F13: vault partition and biometric lock service.

**Processing — all on-device, always**

- Pose detection with per-segment alignment scoring (MediaPipe/ML Kit-class model) drives
  the live skeleton overlay and gates the shutter.
- Capture-quality metrics stored with every capture: subject pixel height (distance
  proxy), exposure histogram, background uniformity, blur score. These later decide
  *comparability* — a geek-readable reason whenever two captures can't be fairly diffed.
- Silhouette derivation (the only persisted output): foreground segmentation →
  **vector outline** (Bézier-simplified contour per angle) + compact width
  profile (64 normalized widths per angle) + derived measurements. The camera
  frame exists **in memory only and is discarded at save — unrecoverable by
  architecture, not a setting** (owner ruling R-U16): WLO never stores a body
  photo — not encrypted, not opt-in, not anywhere. There is no
  "derived-data-only mode": derived-only *is* the product.
- Estimated circumferences from width profile + height ([future]): written to F06 as a
  separate *estimated* series with confidence bands — never merged with measured tape
  values.
- **Cloud: none.** If a cloud derivation is ever added it would require the F12
  `silhouette` consent toggle plus per-use confirmation naming the provider; today there
  is nothing to degrade — offline or consent-off, the feature is fully functional by
  construction.

**Outputs & artifacts:** capture records, timeline grid, compare views, width-profile
series, delta stats, consented share payloads for F11.

**State owned by F08:** capture records + vector outlines/width profiles in the
  vault (never photographs — R-U16), the
  user-chosen reference-capture pointer, reminder schedule, and
  capture-quality metadata.

## 4. User Interaction Model

**Entry points:** F10 Daily Hub capture-due card (reminder days only); a link in F06
("see the shape behind the numbers"); F11 correlation cards deep-linking into compare;
the app navigation section "Archive".

**Primary flows**

- *Happy path:* as in §2 — unlock, checklist, three poses, save, delta card.
- *Fallback 1 — pose won't align:* the failing segment names itself ("left ankle out of
  frame — step back 30 cm"). Guidance persists as long as needed; "Skip this angle" saves
  the session as *partial* (marked, excluded from angle-complete comparisons). A failed
  capture never loses the angles already captured (MeThreeSixty's top complaint, fixed).
- *Fallback 2 — poor capture quality (blur/exposure):* flagged pre-save with a named
  reason; the user retakes or keeps it as *flagged*. Flagged captures never enter delta
  claims and are shown with a small marker in the grid.

**Input minimization:** nothing is ever typed. Angle order is fixed; cadence is a
3-option picker; the reference is set by long-pressing any timeline capture ("set as
reference"). Settings fit on one screen.

**Micro-interactions**

- Skeleton: 12 body segments tint red → amber → green with a 180 ms spring per segment;
  each green snap fires a soft tick haptic; all-green triggers a double-pulse "ready"
  haptic and the shutter button blooms. Silent shutter always — camera sound is a
  discretion leak.
- Countdown: 3·2·1 expanding rings, 400 ms each; preview flash suppressed outside the
  lock gate so a glance never catches content.
- Unlock: on biometric success, grid thumbnails "develop" — blurred placeholders resolve
  with a 250 ms left-to-right stagger. The darkroom metaphor is the tone.
- Compare: a drag-wipe divider between reference and current silhouettes, magnetically
  snapping to the body centerline; **synchronized scrubbing** — drag time on either pane
  and both advance in lockstep, one haptic detent per capture pair.
- Delta chips count up over 400 ms ease-out; deltas inside the noise band render grey
  with a "within noise" chip instead of a fake number.
- Quiet-scale card: when F06's trend weight is flat ≥14 days while the waist width falls,
  a card surfaces with a slow radial pulse behind "−2.3 cm in 6 weeks". No confetti —
  the moment is calm, not carnival.

**Data-quality gating:** delta claims require pose-valid captures with matching
comparability (subject pixel height within 10 %, blur pass). Correlation views require
≥3 captures spanning ≥14 days. Estimated circumferences are hidden entirely below two
valid captures.

## 5. What the User Gets Out

- The timeline grid by week/month (vector-outline thumbnails); side-by-side compare
  against the user-chosen reference in outline / wipe modes; synchronized scrubbing;
  onion-skin outline overlay.
- Numbers: width-profile deltas per band (shoulders / waist / hips / thighs), capture
  cadence stats, comparability flags, and the F06-paired view — "the outlines agree with
  the tape" trust chart (silhouette waist width vs F06 measured waist).
- **Provenance rule:** deltas are labeled *derived* (from width profiles, method sheet
  linked); estimated circumferences are *estimated* with confidence bands and formula
  sheet; F06 tape values shown beside them are *measured*. Tapping any number opens its
  inputs.
- **Visualizations owned here:** capture grid; compare canvas; per-band width-profile
  sparklines; delta-over-time line with noise band; silhouette-vs-tape scatter.

## 6. Motivation & Psychology

- Why the user returns: the quiet-scale payoff — MeThreeSixty's most-cited emotional
  moment ("it proves it!") is losing size while the scale stalls — plus reference-diff
  curiosity and milestone sharing.
- Gamification (with F11): F08 contributes a capture-cadence stat ("11 of the last 12
  weeks captured"); F11 owns medals and the weekly report card. Gaps render as faint
  dots and are never mentioned again — a *coverage* streak, not a duty streak.
- Tone rules: never "you missed", never body-negative descriptors ("trouble spots" is a
  banned phrase). Language is photographic — compare, reference, develop, capture — not
  judgmental. Reminders are invitations with a one-tap "Not this week" that reschedules
  silently, with no counter and no copy about it.

## 7. Relations to Other Features

- **Consumes from:** F06 — trend weight + measured circumferences for correlation and
  quiet-scale detection. F01 — goal weight for projection framing. F12 — the near-never
  `silhouette` consent toggle and consent state. F13 — vault partition, biometric lock,
  export plumbing. F10 — the capture-due nudge slot.
- **Feeds into:** F06 — estimated circumference series (separate, labeled estimated).
  F10 — capture-due card and the post-capture reassurance moment. F11 — delta stats and
  per-card-consented share artifacts. F13 — the rules for what exports may include.
- **Shared concepts:** Consent (F12), Provenance, Targets, Trend (F06's trend weight),
  Vault and Lock (F13).
- **Boundary notes:** F06 owns all *measurements* as data, whether tape or estimated;
  F08 owns *captures* and visual comparison, and never writes measured values. F06 never
  stores photos. Weight forecasting belongs to F06/F07; F08 only mirrors trend lines for
  correlation display.

## 8. Blue Sky Ideas

- **[v1]** Pose-gated capture ritual with per-segment green skeleton and named
  guardrails — MeThreeSixty's best UX with its failure modes fixed (every rejection
  names cause + fix; partial sessions survive).
- **[v1]** Quiet-scale payoff card, automatic when F06 trend stalls while width falls.
- **[v1]** Weekly capture reminder with guilt-free "Not this week" rescheduling.
- **[v1.x]** Comparability engine: on-device lighting/color normalization between
  captures plus distance-mismatch annotation, so side-by-sides are fair and the app can
  *say* why two captures aren't comparable.
- **[v1]** Faceless silhouette strip (vector outlines) as an F11 share artifact —
  with R-U16 it is the *only* artifact that can exist: shape progress without a
  single stored photograph, by architecture rather than policy.
- **[v1.x]** "Angle coach memory": store the distance/height that worked last time and
  pre-apply framing hints ("you used the hallway mirror, phone at chest height").
- **[future]** Estimated circumferences + body-fat *range* from width profiles (published
  formula, uncertainty bands) filling F06's estimated series; tape-vs-scan two-series
  trust view. (The former "derived-data-only mode" is now simply the product: R-U16
  made discard-after-derivation the only behavior.)
- **[future]** "Journey book" chapter: a monthly on-device PDF spread combining the
  silhouette strip, weight trend, and measurement deltas (Gyroscope's printed-report
  pattern, rendered locally with F13/F11).
- **[moonshot]** FutureMe goal-body slider: drag from current to goal weight and watch
  your own faceless silhouette outline morph using your personal width-profile change
  rates. Rendered from vector outlines only — with R-U16 there are no stored photos,
  so the uncanny valley of morphed body *pictures* is structurally impossible; morphs
  conservatively clamped; permanently watermarked "projection — not a prediction."
  MeThreeSixty's wrinkly-limb artifacts are the named failure mode.
- **[moonshot]** Rotatable 3D mannequin by lathe-revolve of front+side width profiles —
  a body you can spin, with no mesh reconstruction and no face.

## 9. Guardrails, Privacy & Sensitivity

- **Storage:** **no photo exists to store.** Frames are processed in memory and
  discarded at save; the vault partition (`archive/`, AES-encrypted at rest,
  opaque filenames `arc_<random>.bin`) holds vector outlines, width profiles,
  quality metadata and derived numbers — records, never images. Nothing
  intimate can leak from a lost phone, a hostile import, a cloud backup, or a
  forensic image, because the artifact does not exist (R-U16). Never written to
  cache or backup-exposed directories.
- **Naming:** the section is "Archive" with a neutral icon. Nothing in navigation,
  settings, notifications, or exports says "body", "photo", or "silhouette".
- **Lock:** every Archive entry requires biometric unlock (app-PIN fallback); auto-relock
  on backgrounding; `FLAG_SECURE` on all silhouette screens so app-switcher thumbnails
  and screen recordings show nothing.
- **Notifications:** reminder copy is generic ("Weekly check-in is ready · ~30 s") with
  the app icon only; the deep link lands on the lock gate, never on content; no photo in
  any notification, ever.
- **Cloud:** none exists in v1. The F12 `silhouette` toggle is OFF by default and
  framed as "not recommended" inside F12 (the frozen matrix's wording); if a cloud
  path ever ships, it requires per-use
  confirmation naming the provider, and on-device results are always computed first and
  never blocked by network state.
- **Sharing:** only via F11 share cards with explicit per-card consent listing exactly
  which stats and outlines leave the app; a pixel path does not exist — the only
  artifacts are vector strips and numbers.
- **Hard rules:** history is never gated, blurred, or capped by any tier (anti
  MeThreeSixty history-paywall, anti Hevy graph cap). No third-party SDK anywhere in
  this module (anti Facebook-SDK). Old app versions keep reading local captures (no
  forced-update kill switch). Deleting a capture shreds the outline + width profile +
  derived rows irreversibly — there is no photo to delete, by design. Exports contain
  no photographs — none exist; vector strips are included per user choice (via F13).

## 10. Open Questions

- ~~Default photo retention~~ — **resolved by owner ruling R-U16**: vector
  outlines only; photographs are never stored, in any form, under any setting.
- Does the capture-due nudge consume the F10 daily nudge budget or ride outside it?
  (Answered in the master doc: R-U13 — inside the budget, baseline-relative.)
- Section naming: "Archive" vs "Vault" vs "Atlas" — answered in the master doc:
  R-U7 freezes "Archive"; icon still needs a discretion review.
- Angle set: answered in the master doc — R-U6 freezes 2 mandatory angles
  (front/side) + optional back.
- Multi-profile (F13/F01): per-profile archives and independent locks need a final
  access-model decision. *(Scope ruled: R-B9 — single-profile v1 with a
  partition-ready schema; the access model lands with multi-profile [v1.x].)*
- Vector-outline rendering: capture-time derivation budget (target < 2 s/angle on
  mid-range hardware) and the contour-simplification tolerance — implementation
  decisions that affect how faithful the stored outline feels in compare view.
