# F09 — Gut & Poop Tracker — Functional Specification

## Identity

| | |
|---|---|
| **Feature ID** | F09 — Gut & Poop Tracker |
| **Provides** | Sub-10-second Bristol-scale bowel logging with on-device photo-assisted classification, plus a diet-correlation engine that makes the meal plan accountable to the gut. |
| **User problems solved** | • "My doctor asked for a bowel diary and I abandoned three apps that made logging annoying" • "I suspect lentils don't love me back, but I can't prove it" • "I want stool photos nowhere near my gallery, my notifications, or my exports" • "Is this new pattern normal *for me* — or worth a doctor visit?" |
| **AI consent category** | Owns the semantics of F12 category `poop-photo`. Photo classification runs **on-device only by default**; the cloud option is OFF by default, surfaced only inside F12 (never mid-logging), and if enabled requires per-use confirmation naming the provider, with EXIF stripped. The correlation/baseline engine is plain on-device statistics and needs no consent category. |
| **Primary evidence** | `docs/research/poop-trackers.md` (plop's on-device baselines, Poo Keeper speed + photo patterns, PoopCheck's no-correction failure, doctor-export norms, taboo UX) · `docs/objective.md` (local-first, per-capability consent) · `docs/research/synthesis.md` §1.7, §2 Tier B, §6 |

## 1. Purpose & Core Objectives

The gut is the fastest honest readout of a diet change — and the one every competitor
ignores. Every poop tracker correlates stool with *ad-hoc* food notes; none has a
*planned* diet. WLO has planned meals (F03), logged meals with nutrients (F02), and
stool outcomes (F09): a closed loop that exists nowhere else. F09 turns a taboo,
shame-adjacent act into a 10-second, dignity-preserving data point — and the points
into personal science: baselines, triggers, transit, and a calm path to a doctor.

Core objectives (verifiable):

- Manual log: ≤10 s open-to-saved, exactly **one mandatory tap** (Bristol type), zero text.
- Photo-assisted log: ≤15 s; the classifier only *pre-selects*; override is one tap;
  accepted and corrected suggestions are visibly labeled.
- Every baseline, correlation, and warning is computed on-device from the user's own
  history; population averages are never shown as benchmarks.
- Red-flag handling produces a gentle "show a doctor" card — never a diagnosis, never
  urgency theater, never a push notification by default.
- The fiber target's default is authored here (25–30 g, or plan-derived) and written
  into the Targets document at plan creation (R-B3); F02/F03 supply actuals
  automatically, so the correlation X-axis costs the user zero extra input.

## 2. User Moments — when and how it is used

- **Cadence:** **irregular by nature — anywhere from three times a week to
  three times a day. There is no "daily gut moment" and the app never expects
  one** (R-U13): a quiet day renders as a quiet day, never a gap, in every
  view including the Hub. Logging happens in ~10-second bursts whenever it
  happens; stats browsing weekly
  (couch, after the F10 Sunday review); doctor export episodic — the #1 community use case.
- **Physical & emotional context:** the bathroom, phone within reach — occasionally a
  pharmacy queue or a partner's bathroom, which is why discretion is structural, not a
  setting. The UI must normalize, never giggle.
- **The single most common flow:** (1) open the Digestion tile from F10 → (2) optional
  hands-free auto-timer photo → (3) sheet opens with Bristol type already pre-selected
  (classifier result, or last-type prior when no photo) → (4) tap Confirm → sheet
  collapses. Fiber-gap and correlation cards are read later, on the couch.

## 3. How It Works — functional mechanics

**Inputs**

- One tap: Bristol type 1–7. Optional one-tap chips: blood, urgency (3-step), pain
  (0–10), optional photo, optional note (text or on-device voice).
- From F02: fiber grams, FODMAP category tags, and hydration from logged meals/drinks.
- From F03: planned-meal nutrition rollup (the *intended* fiber), and plan-change
  events ("switched to high-fiber plan on May 2") for transition context.
- From F12: consent state. From F13: vault, lock service, export plumbing.

**Processing**

- **Bristol classifier (on-device):** photo → type + confidence, shown as a pre-select
  chip beside the matching illustration; fallback (model unavailable or low confidence)
  is the manual carousel, which is always present anyway. Every correction is stored as
  a local training signal. Cloud classification would need F12 `poop-photo` consent
  (OFF by default) plus per-use confirmation naming the provider; consent-off or
  offline → on-device result, UI never blocks.
- **Personal baseline engine:** trailing-window frequency/day, type distribution, and
  symptom-free share. Alerts are phrased against the personal baseline only — "frequency
  −73 % vs your 1.6/day" — never against a population. Requires ≥7 days of data for a
  frequency baseline.
- **Correlation engine:** lagged association (8–48 h) between fiber grams, FODMAP
  categories, hydration, and Bristol outcomes; each card carries effect size, window,
  and n. Gated behind ≥14 days of data, surfaced as a visible unlock progress ring so
  plop's silent 2–3 week cold start feels like leveling up, not a dead app.
- **Transit pairing [v1.x]:** probabilistic meal→stool matching builds the "yesterday's
  lentil curry → this morning" timeline once ≥10 confident pairs exist.
- **Red-flag rules:** blood chip; ≥4 consecutive days Type 1; ≥3 consecutive days
  Type 6–7; or sustained ≥60 % deviation from the user's own baseline for 7+ days. Each
  triggers one calm doctor card per episode.

**Outputs & artifacts:** entry records, regularity score 0–100, calendar heatmap,
correlation cards, flare early-warnings, fiber-gap view, doctor PDF/CSV via F13, and
gut-status annotations attached to F06 weight readings.

**State owned by F09:** entries + optional photos (vault), the fiber-target *default*
(the single target itself lives in Targets — R-B3), baseline
and correlation caches, red-flag episode states, photo-concealment and section-naming
settings.

## 4. User Interaction Model

**Entry points:** F10 Daily Hub quick tile ("Digestion"); a notification only when the
gap since the last entry exceeds the personal baseline; a follow-up chip on F02 meal
cards ("how did yesterday sit?"); F11 stats deep links.

**Primary flows**

- *Happy path:* open → type pre-selected → Confirm. Two taps total.
- *Fallback 1 — classifier wrong or absent:* the 7-type carousel is on the same sheet;
  one tap corrects the pre-select; a toast confirms the correction registered. The
  manual path is never a buried "advanced" mode (PoopCheck's fatal omission, inverted).
- *Fallback 2 — forgotten entry:* backfill with a date/time stepper from the hub tile;
  the **gap reminder (default off, R-U13)** fires at most once per 24 h and only when the
  gap exceeds the personal baseline — for a 3/week person a two-day gap *is* the
  baseline, so it never fires ("your log has a gap" — never "you missed").

**Input minimization:** mandatory input is exactly one tap; all chips (blood, urgency,
pain, photo, note) are optional, collapsed, and remembered. The keyboard never appears
unless the user opens a note; on-device voice notes are the second rung of the input
ladder [v1.x].

**Micro-interactions**

- Bristol carousel: 7 watercolor illustrations (no photorealism, no faces, no emoji);
  the center card scales to 1.15 with a haptic detent per snap; the ideal zone (3–4)
  wears a faint green halo — education by placement, not lecturing.
- Confirm: button morphs into a checkmark; sheet collapses in 220 ms with a soft settle
  haptic. No sound, by default — bathroom context.
- Photo flow: hands-free 3 s auto-timer with countdown rings (Poo Keeper's trick); the
  chip slides in — "Type 4 · 82 %" beside the illustration; tapping it fans out all 7
  types for one-tap override.
- Correction toast: "Noted — tunes your on-device model", 1.2 s, never questioning the
  user's judgment.
- Calendar heatmap: day cells ink-bloom with a 120 ms stagger, colored by regularity
  score; press-and-hold a cell for a private summary bubble (auto-redacted when photo
  concealment is on).
- Fiber ring on the hub: fills with a spring overshoot as meals land; under-target days
  breathe with a slow pulse — an invitation, not an alarm.
- Early-warning card: calm sand gradient, weather-forecast framing ("your gut tends to
  get noisy ~36 h after weeks like this one"). Never red, never a siren.

**Data-quality gating:** no baselines/warnings before 7 days; no correlations before
14 days (a progress ring shows "Trigger report unlocks in 5 days"); regularity score
hidden below 14 days; transit timeline below 10 confident pairs; every correlation card
shows n and window.

## 5. What the User Gets Out

- Numbers: frequency/day vs personal baseline ("0.4/day vs your 1.6 — a quiet week, not
  a crisis"), type distribution, regularity score 0–100 (frequency variance + Bristol
  stability + symptom-free share; formula sheet linked), fiber actuals vs target, and
  hydration-vs-type context.
- **Provenance rule:** every Bristol type is labeled *user-set*, *AI-suggested,
  accepted*, or *AI-suggested, corrected*; baselines are *derived* with their trailing
  window shown; correlations are *derived* with n, window, and a "how we got here"
  sheet; classifier confidence is shown at capture and stored on the entry.
- **Visualizations owned here:** regularity-colored calendar heatmap (Poop Map proved
  users love the yearly view — ours carries health meaning); type-over-time line;
  frequency chart with the personal-baseline band; correlation cards with effect sizes;
  fiber-gap strip; transit timeline [v1.x] with meal icons flowing into outcome icons
  across an 8–48 h axis.

## 6. Motivation & Psychology

- The itch: *proof* ("lentils really are my trigger") and *reassurance* ("this is normal
  for me"). There is also a quiet bond with the scale many users discover on their own —
  the post-bathroom re-weigh that finally "shows" the loss; F09 serves that moment
  honestly: the [future] F06 weight-noise annotation (opt-in, R-U8) explains what the
  scale just did — water and content, not fat — without taking the win away. Day-one
  value comes from micro-education on the type tap: one warm line per
  type ("Type 1 usually just means slower transit — water and fiber help; dinner
  tonight already carries 14 g"). Day-one value is the fix for the category's 60 %
  abandonment.
- Gamification (with F11): the regularity score feeds the weekly report card; photo-free
  share cards ("90 days of gut data, fully local") are generated by F11. No streak
  guilt — gaps render as faint dots and logging resumes without commentary.
- Tone rules: warm-clinical, never crude (Poo Log's 12+ humor branding is the
  anti-pattern), never shameful. Banned in all copy: "overdue", "failed", "abnormal"
  (use "unusual for you"). The nav label is the euphemism; warm directness lives inside
  the feature, where the user opted into the context.

## 7. Relations to Other Features

- **Consumes from:** F02 — logged-meal fiber grams, FODMAP category tags, hydration
  (the correlation X-axis, with zero user re-entry). F03 — planned-meal nutrition
  rollup and plan-change events. F12 — `poop-photo` consent. F13 — vault, lock, export.
  F10 — tile and reminder slots. F05 — optional exercise factor [future].
- **Feeds into:** F03 — fiber-target gaps and swap suggestions ("+12 g via oats and kiwi
  at breakfast"; F03 owns acceptance and meal edits). F06 — gut-status annotation on
  weight readings (a Type 1 backlog can explain a +1.2 kg jump; the scale panic-killer).
  F10 — check-in tile and gap reminders. F11 — regularity score and share cards. F13 —
  doctor export and storage rules.
- **Shared concepts:** Consent (F12), Provenance, Logs, Targets (fiber default
authored here; the single target lives in Targets — R-B3, with plan divergence
rendering in F03 as a plan-fit signal),
Vault and Lock (F13).
- **Boundary notes:** F09 owns the fiber *target* and all gut-side analysis; F03 owns
  meal composition and merely receives suggestions; F02 owns meal-logging truth and F09
  never edits meals. Clinical scoring (Rome questionnaires), diagnosis, and treatment
  are out of scope — the red-flag card's only action is "show a doctor".

## 8. Blue Sky Ideas

- **[v1]** One-tap Bristol sheet with per-type micro-education; classifier pre-select
  with visible confidence and one-tap override; corrections stored as local training
  signal — PoopCheck's missing correction path, built in from day one.
- **[v1]** Red-flag cards (blood, persistent change) with "add to doctor report" and a
  "discussed" state; gentle, once per episode, never a push by default.
- **[v1]** Fiber-target dashboard: default authored here, target read from the Targets document (R-B3), actuals auto-fed by F02/F03 rollups.
- **[v1.x]** Trigger report as a leveling system: correlations unlock at 14 days behind a
  visible progress ring; each card names effect size, window, and n — data quality as
  progression, not gatekeeping.
- **[v1.x]** Transit timeline: "yesterday's lentil curry → this morning, 19 h" —
  meal→stool pairing on an 8–48 h axis, with a personal median-transit estimate once
  enough pairs exist.
- **[v1.x]** Plan-transition reassurance: when an F03 plan change shifts stool types, a
  card explains the expected biology ("fiber went 14→31 g; softer types for ~a week is
  normal") — scary transitions become reassured ones.
- **[v1.x]** Doctor-visit PDF/CSV via F13: Bristol-illustrated table, heatmap snapshot,
  fiber chart, red-flag history; photos are a per-export opt-in; password-protectable PDF.
- **[future]** Stool forecast: "at yesterday's fiber and water, tomorrow leans Type 1",
  with a one-tap meal-swap proposal into F03 — probabilistic framing, never prescription.
- **[future]** Weight-noise annotator for F06: flagged days ("+1.4 kg with a Type 1
  stretch and low hydration — likely not fat"), strictly opt-in.
- **[moonshot]** Guided transit self-test: blue-dye/corn-style marker protocol with an
  in-app timer and a personal transit-time distribution — a measurable home experiment.
- **[moonshot]** Personal FODMAP budget model: per-category tolerance thresholds learned
  from the user's own history (categories from F03 tags), shown as dials with
  uncertainty bands — Monash-grade insight without Monash's data.

## 9. Guardrails, Privacy & Sensitivity

- **Naming:** the section is labeled "Digestion" (user-renamable); the icon is a neutral
  leaf/wave mark. Nothing in navigation, notifications, settings, or widgets says
  "poop" — the word appears only inside the feature, where the user chose the context.
- **Photos:** **not retained by default** (R-U14) — the photo is processed
  on-device and discarded at save unless stool-photo retention is explicitly
  enabled (with an auto-shred-after-N-days option). When retained: only in the
  F13 vault partition (`gut/`), AES-encrypted, opaque
  filenames; never in MediaStore, the system gallery, or any photo picker. `FLAG_SECURE`
  on any screen showing a photo (app-switcher thumbnails show nothing). History lists
  blur photos by default with a hold-to-reveal — the "conceal when showing someone"
  toggle (Poo Keeper pattern) is one tap away.
- **Lock:** optional biometric gate on section entry via the F13 lock service;
  notifications never contain photos and never contain taboo words ("Digestion check-in
  ready"); deep links land behind the lock when enabled.
- **Exports:** doctor PDF/CSV generated locally via F13. CSV never contains photos or
  photo references; PDF includes photos only via an explicit per-export checkbox
  (default off). The share sheet is the only egress; nothing leaves without that tap.
- **Cloud:** `poop-photo` consent is OFF by default and never surfaced during logging —
  the point-of-use UI simply shows the on-device result. If enabled: per-use
  confirmation naming the provider, EXIF stripped, on-device result always computed
  first; offline degrades silently to on-device.
- **Hard rules:** never diagnose, score clinical severity, or recommend medication —
  the advice ceiling is fiber/water context plus "show a doctor". No location capture,
  ever (anti Poop Map). No ads, no third-party SDK, no account; the doctor export is
  free forever (anti plop's paywalled report). No Health Connect sync of stool entries
  in v1 (other apps' read permissions are a leak surface); no photos in notifications
  or widgets, ever; deleting an entry shreds photo and rows irreversibly.

## 10. Open Questions

- Default fiber target source: a fixed 25–30 g suggestion vs derived from the active F03
  plan — and on clash, F09's displayed number wins (as proposed) or F03's?
  *(Resolved: R-B3 — one target in Targets; F09 supplies the default at F01's
  plan-creation; divergence renders in F03 as a plan-fit signal.)*
- FODMAP category vocabulary: how F03 tags recipes (full Monash-style categories vs a
  simplified 6-tag set) and any licensing constraints — needs a master-doc decision.
  *(Resolved: R-S8 — simplified 6-tag open set in v1; Monash-grade [future].)*
- Classifier personalization: per-user on-device fine-tuning vs a correction-cache prior
  on top of a frozen model — feasibility differs sharply; needs an F12 platform ruling.
  *(Resolved for v1: R-B6 — correction-cache prior over a frozen on-device model;
  fine-tuning stays [future] pending an F12 platform ruling.)*
- Pain input granularity: 0–10 slider vs 4-step chips (logging speed vs resolution).
- Caregiver / multi-profile logging (logging for a child or partner) — deferred; touches
  F13's profile model.
- Whether the F06 weight-noise annotation should be automatic or strictly opt-in — some
  users will read it as presumptuous. *(Resolved: R-U8 — opt-in, default off.)*
