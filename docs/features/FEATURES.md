# WLO — Master Feature Document

*The map of the app: what the features are, how they interlock, and the frozen
rulings that resolve every cross-feature question the 13 specs deferred. Each
feature's full spec lives beside this file (`F01`–`F13`); the template they
follow is [`TEMPLATE.md`](TEMPLATE.md). Companion docs:
[`../objective.md`](../objective.md) · [`../research/synthesis.md`](../research/synthesis.md).*

---

## 1. The feature map

| ID | Feature | Doc | Provides (one line) | AI consent |
|----|---------|-----|---------------------|------------|
| F01 | Onboarding, Goals & Diet Plan Studio | [F01](F01-onboarding-diet-plans.md) | Zero-account path to a versioned, user-owned Diet Plan | consumes `meal-planning` |
| F02 | Food Logging & AI Nutrient Estimation | [F02](F02-food-logging.md) | Any meal → trusted nutrient record in seconds, photo-first | consumes `food-photo`, `voice-input` |
| F03 | Meal Planning & Recipes | [F03](F03-meal-planning.md) | Template → week plan → pre-logs; planned-vs-actual adherence | consumes `meal-planning` |
| F04 | Shopping List & Pantry | [F04](F04-shopping-pantry.md) | Auto list from the plan; pantry ground truth; edit-proof checks | none in v1 (see R-C3) |
| F05 | Exercise Planning & Tracking | [F05](F05-exercise.md) | Movement tracker: strength logging loop + first-class cardio (live GPS/HR sessions); explainable adaptation; expenditure context | none (see R-C2) |
| F06 | Weight & Body Metrics | [F06](F06-weight-body-metrics.md) | The honest measurement layer: trend weight, provenance, EAV metrics | none — local math only |
| F07 | Energy & Metabolism Engine | [F07](F07-energy-engine.md) | On-device adaptive TDEE, 3-band decelerating forecast, weekly check-in | none — the privacy flagship |
| F08 | Silhouette Tracker | [F08](F08-silhouette.md) | Transient guided camera capture → vector outlines only; no body-photo retention | owns `silhouette` (near-never) |
| F09 | Gut & Poop Tracker | [F09](F09-gut-tracker.md) | 10-second Bristol logging + the plan↔gut correlation moat | owns `poop-photo` |
| F10 | Daily Hub & Nudges | [F10](F10-daily-hub.md) | One home surface; the app-wide respectful notification policy | none (nudge copy: `insights-chat`, opt-in) |
| F11 | Insights, Statistics & Gamification | [F11](F11-insights-gamification.md) | Stats hub, report cards, forgiving streak economy, share cards | consumes `insights-chat` (prose only) |
| F12 | AI Platform: On-device Models, BYOK & Consent | [F12](F12-ai-platform.md) | Model zoo, BYOK layer, consent matrix, receipt log, redaction | owns all six categories |
| F13 | Data Vault & Integrations | [F13](F13-data-vault.md) | Schema of record, export/backup/restore, Health Connect, scales | none — enforces the network gate |

**Layering:** F13 (foundation) → F12 (AI infrastructure) → domain features
F01–F09 → F10/F11 (composition & motivation). F07 sits at the center of the
domain ring: it is the consumer of F02/F03/F05/F06 and the authority that makes
them one system instead of five trackers.

---

## 2. How it works together

### 2.0 Weight-first release contract (WLO-0068)

This section is authoritative when older feature specs, research checklists,
or prototypes use `v1` more broadly.

**Promise.** Release 1 is a complete, trustworthy raw weight → weight trend →
goal loop. It supports **loss, maintenance, and gain** as first-class goal
modes. Raw events remain inspectable; derived values name their method and
provenance; forecasts are ranges with visible data-quality states.

**Launch journey.** First launch asks for (1) the app-wide body-mass unit,
`kg` or `lb`, (2) an optional goal mode
and target/range, and (3) import from Health Connect/file or a manual first
weigh-in. Every step after unit selection is skippable. Saving or importing a
measurement lands on **Weight**, the default and primary destination. Diet,
nutrition, exercise, silhouette, and digestion setup are optional later
journeys; none blocks a usable weight tracker.

**Goal safety contract.** Raw weight tracking never requires a goal. Generic
automated targets and forecast dates use the single WLO-0080 eligibility
result documented in
[`../research/weight-goal-safety-contract.md`](../research/weight-goal-safety-contract.md).
The goal engine is adult-only; unanswered screening, pregnancy/breastfeeding,
eating-disorder concern, medically influenced weight, unsupported pace, or a
sub-floor plan holds targets and dates without disabling the weight record.
This is a product-support boundary, not medical advice or diagnosis.

The post-onboarding Goals editor also supports an explicit weight-only first
Targets version. It does not require the diet wizard and does not invent a
calorie budget: `energy.budgetKcal` remains null unless the user supplies and
safely validates one. Downstream consumers render an absent budget as absent,
not zero or a computed recommendation.

| Horizon | Included capability |
|---|---|
| **Release 1 — weight core** | correct kg/lb entry and display; event log and edit/delete; honest trend/chart states with a neutral selected-period change; accessible entry; post-save trend result; lifecycle/error reliability; Health Connect weight/body-fat import with durable source identity; benchmarked daily-scalar policy; calibrated forecast ranges; loss/maintenance/gain safety and direction-correct forecasting; goal editing and progress |
| **Release 2 — weight experience** | No committed additions; promote only from dogfood evidence. |
| **Deferred / advanced** | user-tunable smoothing and smoother comparison; custom metrics; elaborate milestone celebrations; Bluetooth-scale drivers; multi-profile vault partitions; broader food, planning, exercise, silhouette, digestion, and AI suite release work |

Release 1 stores one profile while keeping `profileId` in every row. Body mass
has one app-wide display setting; multi-profile may promote it to per-profile
without changing canonical kg storage. Length uses `cm`/`in` under the same
metric/imperial preference. Health Connect identity is source record ID + data
origin + recording method + version/update metadata; duplicate deliveries
update the same local event. A daily scalar is a separate, versioned derived
view and never destroys or merges source events.

### 2.1 The shared data spine

Five concepts are defined once and consumed everywhere:

- **Day record** — the app's *rendering* unit: one composed view per profile
  per date (intake entries owned by **F02** incl. day status
  `Logged/Skipped/Fasted`, planned-meal slots owned by **F03**, measurements
  **F06**, exercise **F05**, gut entries **F09**, captures **F08** — vector
  silhouette records, never photos per R-U16).
  **Storage is event-level, not day-level** (R-B8): every timestamped data
  point is kept verbatim — multiple weigh-ins in one day are normal data.
  *Rulings R-B1, R-B8.*
- **Targets document** — versioned; `{calorie budget + schedule, macro splits,
  fiber, water, workout cadence, pace, floor}`. Write authority: **F01 creates,
  F07 adapts (Apply-only)**; every other feature reads. *R-B2.* Water intake
  is logged by **F02** (drink entries + a one-tap water quick-add); F09
  consumes the actuals for its hydration correlation.
- **Provenance** — every derived number is `measured` / `estimated` / `derived`
  (AI outputs add model + consent state) and opens a "how we got here" sheet.
  A number rendered without its chip is a spec bug.
- **Data-quality states** — `developing / updating / held` (Zolt/MacroFactor
  pattern), generalized: F07 gates its engine, F11 gates grades, F03 gates
  adherence, F04 gates cost stats, F06 gates trend rendering. Weak data holds
  and says why; it never guesses.
- **Consent + Receipt** — F12's six categories gate cloud AI; every call,
  cloud or local, writes a hash-chained receipt; F13's network gate is the
  single egress choke point. Day-to-day dispatch runs through **F12's
  NetworkDispatcher**; **F13 owns the architectural enforcement** (feature
  code cannot reach a socket except through the dispatcher) and the
  user-facing audit surface.

Supporting cast: **Trend weight** (computed in F06 only — one smoother, one
owner), **Food Memory**, **Fresh Start marker** (hide-not-delete, reversible),
**Vault partitions** (sensitive attachments in F13's encrypted, gallery-invisible
store), **Nudge Contract** (F10's app-wide notification policy).

### 2.2 The Day loop (daily rhythm, orchestrated by F10)

Morning: Health Connect import or simply typing 92.1. F06 answers with the trend card ("Trend
179.1, down 0.6") and the Hub's hero number; a second weigh-in later in the
day is kept as its own data point. Meals: F03's planned card → one-tap
"log as planned" or F02's photo ladder (≤10 s, corrections optional).
Movement: F05 session with prefilled sets → expenditure handed to F07 as
context. Gut: whenever it actually happens — for many people a few times a
week, not daily — F09's two-tap Bristol entry, correlated automatically
against F02's fiber and F03's plan; no day is ever flagged "gut not logged"
(R-U13). Evening: day status one-tap, day-closure recap [v1.x]. The Hub
reorders itself by time of day; nothing in the loop ever requires a keyboard.

### 2.3 The Week loop (the ritual that binds the system)

Sunday: **F07 check-in** (60–90 s) → status chip, trend, adherence, measured
TDEE, proposed targets → **Apply / Keep / Discuss**. Applied targets write a new
F01 plan version (diff shown) → **F03 "Generate week"** against the fresh budget
→ **F04 list** builds from the plan in <1 s → shop → sweep to pantry. F11's
report card stamps grades from the same week's data. This is the loop no
competitor closes: *measure → decide → plan → shop → log → measure.*

### 2.4 Three signature flows (the moats, as user stories)

1. **The honest forecast.** Onboarding goal dials → F07 renders 3-band forecast
   (cold-start formula mode, labeled `ESTIMATED`) → by week 3 the bands sharpen
   around the *measured* TDEE → near goal the deceleration model bends the cone
   instead of lying linearly. *(F01+F07+F06; the open gap in all 18 researched apps.)*
2. **The stall that isn't.** Scale flat 14 days → F08's quiet-scale card shows
   "−2.3 cm in 6 weeks" → F07 describes approximate intake/burn balance and
   names a TDEE change only when two quality-passing uncertainty intervals do
   not overlap → F09 annotates the water-weight noise. Four features turn the
   category's #1 quit-moment into the app's best moment.
3. **The proof.** "Lentils don't love me back": F03 planned the curry, F02
   logged the fiber, F09 pairs the outcome 19 h later → trigger report unlocks
   at day 14 → doctor PDF exports the whole story. *(The plan↔poop loop exists
   in zero competitors.)*

### 2.5 The consent matrix (frozen)

| Category | Consumers | Default |
|---|---|---|
| `food-photo` | F02 (F04 cloud-assist if ever shipped — R-C3) | off, on-device first |
| `voice-input` | F02 (F05/F06/F10 voice quick-logs; raw-audio sub-toggle off) | off, on-device STT |
| `meal-planning` | F01, F03 | off, deterministic engine |
| `silhouette` | F08 — **no cloud code path exists in v1** | off, "not recommended" |
| `poop-photo` | F09 | off, on-device classifier |
| `insights-chat` | F11 (prose), F10 (nudge copy, opt-in; default local templates) | off, local rules |

Future consumers already sanctioned by existing categories (no taxonomy
change; the matrix above lists v1 traffic only): F03 photo-to-recipe →
`food-photo` [future] · F03 voice planning → `voice-input` [v1.x] · F05
natural-language program discussion → `insights-chat` [future].

Non-AI network (governed by F13, audited in F12's connection log): OFF/USDA
food lookups — toggle default **on** (payload is barcode/search string only, no
personal data), aggressive cache, one-tap off (R-C4). Everything else a packet
analyzer sees: nothing. F05/F06/F07 make zero AI calls, ever (R-C5).

---

## 3. Rulings — frozen decisions on deferred questions

Each spec deferred cross-cutting calls to this document. Ruled as follows;
these are binding until amended *here* (feature docs must not re-litigate them).

### Consent & network

- **R-C1 — Taxonomy is six, frozen.** New cloud capabilities enter only by
  master-doc amendment. `exercise-plan` is pre-approved as the seventh *if and
  when* F05 cloud generation ships ([future]); until then F05 is on-device only.
- **R-C2 — F05 v1:** deterministic rule engine only; no cloud path, no consent.
- **R-C3 — F04 vision (receipt scan, pantry stock-take):** on-device OCR/vision
  only in v1/v1.x, consent-free; a future cloud assist would consume
  `food-photo` (making F04 its third consumer).
- **R-C4 — Food-DB lookups:** F13 integration toggle (not an F12 AI category),
  default on, cached, per-lookup audit trail.
- **R-C5 — F05/F06/F07 are AI-free by design** — stated on the consent matrix
  as "local math only"; this is a marketing asset, not an absence.
- **R-C6 — Raw-audio cloud STT** is a sub-toggle inside `voice-input`, default
  off (transcript-only is the default payload).
- **R-C7 — F07 "Discuss" with `insights-chat` off** renders the local explainer
  plus one informational line; it never prompts to enable cloud.

### Data spine & authority

- **R-B1 — Day record:** F02 owns the food diary and day-status semantics;
  F03 owns the planned-slot state machine (`planned/confirmed/swapped/skipped/
  replaced`) that projects into it; a `replaced` slot links to the actual F02
  entry, which owns the nutrition; F03 keeps slot labeling. F07/F10 consume.
- **R-B2 — Targets:** single versioned document; F01 writes v1, F07 writes
  adaptive adjustments **only via explicit Apply** (ledgered, reversible);
  all other features read-only. Exercise expenditure is structurally incapable
  of raising eating targets (enforced in the write path).
- **R-B3 — Fiber target** lives in Targets; F09 supplies the default
  (25–30 g, or plan-derived) at F01's plan-creation; divergence between the
  active F03 plan and the fiber target renders in F03 as a plan-fit signal —
  there is exactly one target, never two competing numbers.
- **R-B4 — Adherence metrics:** F03 computes (plan coverage, energy fidelity,
  swap gravity, cook realism); F11 consumes read-only. One definition per number.
- **R-B5 — F06→F07 series contract** frozen: versioned daily scalars + trend +
  residual σ + coverage % + provenance flags. Release 1 uses
  `consistent-window-v1`: for each stored day bucket select the valid
  canonical-kg reading in the half-open 05:30–09:30 profile-policy-timezone
  window nearest 07:00 (tie-break local minute, capture instant, stable event
  ID); when the window is empty use the day's median, averaging and attributing
  both middle events for an even count. Empty days remain absent. The profile
  timezone is captured once and travels with backup data, so travel cannot
  silently rewrite history.
- **R-B6 — F09 classifier personalization:** correction-cache prior over a
  frozen on-device model in v1 (shared mechanism with F02's dish priors);
  on-device fine-tuning is [future], pending an F12 platform ruling.
- **R-B7 — Fresh Start ledger:** badges/milestones retained; streaks reset;
  charts shaded at the marker; everything reversible. (F01 ritual, F11 economy,
  F13 mechanics.) **Release disposition (WLO-0095): not implemented.** Until a
  reviewed workflow can satisfy every reversible-ledger requirement, release UI
  and direct routing expose no Fresh Start action. Export, restore, and explicit
  record deletion remain available; the legacy hide/profile-retirement helper is
  not a compliant implementation and must not be invoked from release UI.
- **R-B8 — Event-level storage, day-level rendering (owner ruling).** Every
  measurement is stored as a timestamped event; **multiple weigh-ins per day —
  including the post-bathroom "now I get my win" re-weigh — are kept verbatim**,
  never collapsed, overwritten, or judged. "One value per day" semantics
  (a versioned daily policy for weight, last-in for girths) are **derived views** consumed
  by trend math, F07, and exports; the raw points stay queryable and ship in
  exports. *Amendment (owner ruling,
  2026-09-14, WLO-0035):* the verbatim rule governs ingestion and automatic
  processing — a **user-initiated delete of their own entry is an explicit
  act, not silent collapsing**: the store exposes a hard delete (no tombstone
  audit trail), and deleted events drop out of derived views by construction.
- **R-B9 — Profiles: single-profile v1, partition-ready schema (owner
  ruling).** Every domain row carries `profileId` (default profile created
  at onboarding); per-profile vault partitions, biometric locks, and backup
  bundles arrive with multi-profile [v1.x]. Health Connect is single-profile
  per device: the sole profile owns the connection in v1;
  weight-signature household routing stays [future] (F06).

### Algorithm constants (published on F07's Algorithms page)

- **R-A1 —** 7,700 kcal/kg (3,500/lb). **R-A2 —** EWMA α default 0.15. Owner amendment WLO-0104 (2026-09-16):
  tuning and math explanations live in chart settings, not the overview.
  Default EWMA is seeded once from the full recorded daily-scalar history;
  chart ranges crop that series, never restart it. Persisted/live trend values
  share this initialization. Overview change uses first/last available trend
  values in the selected period and names their actual dates. MA7 uses seven
  calendar days of available scalars, never seven arbitrary observations. **R-A3 —** v1 engine: Transparent (closed-form) only; Adaptive
  spline ships v1.x opt-in, becomes default only after benchmarking.
- **R-A4 —** v1 nutrients: kcal, macros, fiber, sugar, sodium; micronutrient
  panels v1.x.
- **R-A5 —** F07 cold-start forecast mode is **[v1] mandatory** (F01's
  onboarding preview depends on it): formula-BMR-based, wide bands, `ESTIMATED`
  chip, "will sharpen as you log".
- **R-A6 — Forecast energy partitioning: partition-free in v1.** The 3-band
  forecast uses the single 7,700 kcal/kg constant (R-A1) with the published
  caveat near goal; composition-aware fat-fraction rates (informed by F05
  training context) ship [v1.x] with their own published assumptions. The gain
  path is direction-specific rather than sign-reversed loss: its expenditure
  feedback uses the published adult linearized value of 22 kcal/day/kg gained,
  while retaining the same partition-free product energy-density ruling.
- **R-A7 — Check-in cadence: weekly default, opt-in daily in v1 (owner
  call).** The Sunday ritual stays the default and the weekly report card
  (R-U2) is unchanged; power users may opt into a daily check-in cadence at
  v1 — daily proposals remain Apply-only.

### UX policy

- **R-U1 — Nudge Contract (app-wide):** default cap 1/day (user 0–3), quiet
  hours 21:30–07:30, all reminders — including F08 capture-due and F09 gap
  nudges — draw from this single budget with F10 arbitrating priority; F07's
  check-in is one soft notification on check-in day; dismissed = never re-fired
  for the same gap.
- **R-U2 — Report card:** Sunday default, user-selectable; F07 check-in and F11
  grades share the day.
- **R-U3 — Consistency Score:** dormant tile until ≥4 weeks of data, then
  opt-in activation; hideable permanently.
- **R-U4 — Wrapped:** install-anniversary reveal (calendar-year toggle optional).
- **R-U5 — Backups:** encrypted by default (user passphrase, lockout warning
  shown at setup); plaintext is an explicit per-export choice; rotation default 7.
- **R-U6 — F08 capture:** 2 mandatory angles (front + side), back optional;
  comparisons annotate which angles are present; the 60 s ritual is preserved.
- **R-U7 — Section naming:** F08 = "Archive", F09 = "Digestion" (renamable).
  Words like "body photo" / "poop" never appear in nav, notifications, or widgets.
- **R-U8 — F06 weight-noise annotations from F09:** opt-in, default off.
- **R-U9 — F10 day-closure recap:** in-app card first [v1.x]; as a notification
  only by explicit opt-in.
- **R-U10 — Streaks:** multi-oracle (weigh-in OR meal-log OR workout by default,
  user-tunable); `held` days count as neutral — they neither advance nor break.
- **R-U11 — F03 month view** is read-only overview in v1 (week-level editing).
- **R-U12 — Widgets:** one widget framework owned by F10; the F04 list widget
  rides it [v1.x]. No parallel widget stacks.
- **R-U13 — No daily expectation for irregular rhythms (owner ruling).** Gut
  entries and silhouette captures are never part of any "expected logs" /
  day-completeness model; nothing renders as a gap, a miss, or an empty streak.
  F09 gap reminders are baseline-relative (a 2-day gap for a 3/week person *is*
  the baseline — it never fires) and **default off**; F08 capture reminders are
  cadence-relative the same way.
- **R-U14 — Photo retention is opt-in, default off (owner ruling).** Storage
  is a real constraint with no Google-Photos cloud overflow to bail it out.
  Food photos (F02) and stool photos (F09) are processed on-device and
  **discarded at save by default**; compressed-thumbnail mode and full-quality
  retention are explicit opt-ins (with a per-capture "keep this one"). F13
  therefore ships a **storage dashboard [v1]** (per-category usage,
  shrink-to-thumbnails, age-based purge, per-category wipe) and a
  **user-folder photo offload [v1.x]** (copy originals to the SAF backup
  folder to reclaim device space — the local-first stand-in for cloud
  overflow); private Google-Drive offload is [future] (complex,
  account-bound). Silhouette is *not* an exception to opt into — see R-U16.
- **R-U16 — Silhouette: vector outlines only, never photographs (owner
  ruling).** The F08 capture processes camera frames **in memory only**, then
  derives a **vector outline** + width profile + estimated measurements and
  **discards the frame at save — unrecoverable by architecture, not a
  setting**. WLO never stores a body photo anywhere, in any form, encrypted
  or otherwise: not in the vault, not in backups, not in exports, not in
  share cards. What the timeline, compare studio, and share artifacts use are
  vector records. Rationale: we do not preserve intimate images of people —
  there is nothing to leak from a lost phone, a hostile import, or a
  compromised cloud backup, because the artifact does not exist.
- **R-U15 — Assists never gate (owner ruling).** Every capture assist —
  photo recognition, stool classifier, barcode — has an
  equal-status manual path one tap away, offered but never forced, with a
  graceful landing on manual when recognition fails. Sometimes typing 92.1 is
  genuinely the fastest input, and the app must honor that.
- **R-U17 — Check-in hero-slot promotion (F10):** on the user's check-in day
  (Sunday default) the F07 check-in card is promoted into the Daily Hub's hero
  slot for that day, displacing the trend card; the trend returns the next day.
- **R-U18 — Backup bundles exclude photo attachments by default**
  (explicit per-bundle opt-in), matching R-U14's photos-default-off posture;
  the SAF-folder attachment offload remains the [v1.x] space-reclaim path.

### Content & scope

- **R-S1 — License: Apache-2.0.** *(Amended 2026-09-11 by the owner, via
  ticket WLO-0014 — originally GPLv3.)* Rationale: a permissive license
  permits bundling free proprietary **on-device** SDKs where they beat open
  ones (R-S13 policy); the cost is that openScale's GPLv3 scale drivers can
  no longer be reused — Bluetooth-scale support becomes clean-room
  implementations of popular protocols. Models, datasets and recipe content
  carry their own documented licenses.
- **R-S13 — Proprietary-SDK policy.** *(Owner ruling 2026-09-11, ticket
  WLO-0014.)* The code is open source (Apache-2.0); proprietary SDKs/services
  are acceptable when they are **free** and **materially better** than the
  open alternative, subject to three conditions: (a) **on-device only** — or,
  if the SDK transmits anything, it is gated behind an explicit consent
  toggle and issues receipts like any cloud call; (b) every third-party SDK
  gets an in-repo **data-flow audit card** (what it collects, when, where it
  goes); (c) F12's single-egress `NetworkDispatcher` discipline still applies
  to everything that touches the network. **F-Droid is not a distribution
  requirement** (Play Store + GitHub APKs are; F-Droid may be added later via
  a clean-stack audit); the **"WLO Pure" no-INTERNET flavor is dropped**
  (F12 §3.8 amended accordingly).
- **R-S14 — Model-zoo distribution: download-on-first-use only.** *(Owner
  call 2026-09-11, ticket WLO-0014.)* The APK ships **no neural models**:
  every F12 zoo model downloads on first use of its capability from
  hash-pinned public URLs (F12 §3.2), with size shown before download and
  one-tap reclaim. Airplane-mode parity applies once a model is present;
  until then the capability degrades to its R-U15 manual path, disclosed in
  onboarding. Supersedes the "bundled" wording in F02 §3 and F12 §3.2 for
  zoo models; small vendor-SDK built-in models (e.g. inside ML Kit
  libraries) are a tracked exception pending the tech phase's size audit.
- **R-S2 — Exercise library:** authored minimal set (CC0) + community additions;
  no proprietary bundled database; the two-level muscle schema is the keystone.
- **R-S3 — Seed recipes:** ~50 open-licensed starter recipes + import + AI
  drafting; a larger curated library is v1.x content work.
- **R-S4 — Import converters:** generic CSV/JSON at v1; MFP/Lose It!/Paprika/
  Mealime converters v1.x.
- **R-S5 — Pantry partial-stock deduction:** default off globally; one-time
  prompt at first generation with remember-choice.
- **R-S6 — F01 preference quiz:** 8-card core in v1; the long tail lives in the
  Studio and F03 filters. Cadence default: weekly (daily supported).
- **R-S7 — F03 fit-badge tolerance:** ±5 % kcal, aligned with F07's proposal
  semantics so a "fit" plan never argues with a `held` week.
- **R-S8 — F09 FODMAP vocabulary:** simplified 6-tag open set in v1;
  Monash-grade category coverage is [future] (licensing-dependent).
- **R-S9 — Health Connect nutrition writes (F13):** kcal/macros at v1;
  micronutrients when the food DB supports them.
- **R-S10 — `meal-planning` cloud generation ships [v1.x]:** v1 is
  deterministic-only for both F01 template refinement and F03 plan/recipe
  generation; the consent + BYOK path lands with the first cloud-capable
  release. (Owner call, ticket WLO-0007.)
- **R-S11 — F05 cardio scope in v1:** minimal native entries
  (type/duration/effort/distance) + Health Connect import via F13; native
  pacing/zone charts are later work. **Amended (owner ruling, round 9,
  2026-09-12): a general sport tracker is non-negotiable — cardio is
  first-class and co-equal with strength.** Native live sessions (timer,
  GPS distance computed on-device, HR zones via F13) with pace/split/zone
  summaries are v1; the minimal manual entry and Health Connect import
  remain as fallback and complement.
- **R-S12 — Model training-data policy:** every model in F12's zoo is
  trained on open-licensed (OSI/CC-class) datasets only, each named on its
  model card; no proprietary training data anywhere in the zoo.

### Design

*From the UI/UX design phase (ticket WLO-0011; deliverables under
`docs/design/`, rationale in `docs/design/research/synthesis.md` §4).
Ratified 2026-09-11.*

- **R-D1 — Theming: dark-first, fixed semantics, dynamic color
  surfaces-only.** The base theme is dark-first (the 6 a.m. weigh-in
  happens in a dark bathroom) with a fixed semantic palette — no alarm-red
  token exists; `held` = amber. Material You dynamic color is opt-in and
  applies to surfaces/accents only; semantic states and data-viz series
  are never wallpaper-sourced.
- **R-D2 — Navigation: five-tab target model; four destinations in this release.**
  The release shell shows Weight · Hub · Plan · More, in that order, because
  those destinations have functioning landing surfaces. Insights is omitted
  until its landing screen ships; legacy Insights links land on More with an
  availability explanation. The future target remains Hub · Plan · Insights ·
  Archive · Digestion. F02 capture is a flow-over-context, never a tab;
  F06 is reached from the Hub's numbers; F01/F12/F13 live under Settings.
  Archive and Digestion stay visible tabs — hiding discretion-gated
  flagship features behind a "More" sheet reads as shame, which R-U7
  exists to prevent.
- **R-D3 — Typography: Inter, single family.** Inter (OFL-1.1,
  Apache-2.0-compatible per R-S1) is the only UI family. Tabular figures
  (`tnum`) are mandatory wherever numbers change or align; display optical
  sizing for hero numerals. No second typeface. WLO-0114 exception for the
  F06 page-overview hero: use regular Inter Medium with proportional lining
  figures to match the owner-requested WLO-0103 mockup. This does not apply
  to tabular rows, axes, deltas or odometers.
- **R-D4 — Charting split.** Vico (Apache-2.0) for standard line/bar
  charts; custom Compose Canvas for the four signature objects (forecast
  cone, rings, odometer numerals); one shared
  month-heatmap implementation reused by F05/F09/F11. YCharts rejected as
  dormant.
- **R-D5 — Delta colors: neutral default, curated accents, red never.**
  Resolves F06's user-selectable gain/loss palette clause against the
  no-alarm-red bar (research collision C1): the default delta pair is
  CVD-safe neutral (down = teal accent, up = neutral grey); users may pick
  accents from a curated Okabe-Ito-derived set. Red is never a WLO default
  and never used by WLO's own urgency/anomaly rendering — urgency is
  position + copy, never hue.
- **R-D6 — Kind haptics.** Every data-outcome haptic is Confirm-class
  (short, light) regardless of whether the news is good. Reject-weight
  haptics are reserved for true blocking errors (invalid BYOK key, corrupt
  import, F01's calorie-floor wall) and never used for data results.
  Haptics remain the feedback channel when reduced-motion is on.
- **R-D7 — Badge visual direction (resolves §6 item 7):** minimal
  geometric marks with the number foregrounded — numbers-first, no
  illustrations, no mascots (F11 badge gallery).
- **R-D8 — F09 pain input (resolves the AMBIGUOUS flag in F09 §4):**
  4-step chips (none / mild / moderate / severe) at capture, expandable to
  a 0–10 slider in the entry detail.
- **R-D9 — F08 "Archive" icon:** an abstract stacked-outline glyph reading
  as "records" — deliberately not a camera or body glyph, so it stays
  neutral in any context.
- **R-D10 — Units: metric default, imperial a user setting (owner ruling).**
  kg (and metric throughout) is the default and fallback; `lb` is the imperial
  body-mass choice and pace follows that choice. Stone is not supported.
  Release 1 has one app-wide preference because it has one profile; a later
  multi-profile release may make it profile-scoped. Every mass/length render is settings-driven —
  no unit is ever hardcoded in copy. The prototypes demo the default (kg)
  unless a page's meta declares otherwise; an entry suffix/label ("kg")
  reflects the active setting but is not a switch. Unit changes live only in
  Settings so an accidental tap cannot reinterpret input.
- **R-D11 — Copy discipline: information earns its place (owner ruling).**
  User-facing copy never contains: motion/haptic parameters (ms values,
  haptic names, easing/spring vocabulary), ruling IDs (R-\*) or feature IDs
  (F\*\*, §refs), engine/recognizer/model versions or algorithm parameters
  (EWMA α, window sizes), or design-doc phrases ("offline is a non-state",
  "celebration is geometry"). Those live in the frame annotations and the
  ⓘ explainers only. Provenance chips surface a user-word + ⓘ
  ("derived ⓘ", "AI-estimated ⓘ", inline "of 1,900 kcal ⓘ"); the technical parameters
  sit one tap away in the how-we-got-here explainer — the §2.1 provenance
  guarantee is preserved, its vocabulary is not. Each card carries at most
  one helper line, phrased as the next action, never as mechanism; nothing
  on a screen may duplicate information another card already shows; states
  explain themselves when they occur and are never pre-explained ("fallback:
  provisional when held" is the banned pattern).

- **R-D12 — "Weight trend" naming + unit glyphs (owner ruling, round 3).**
  The engine trend is weight-specific and UI labels say so: cards, rows and
  screens read "Weight trend" (F06/F07/F10), never a bare "Trend" — calories
  surface as the ring and "left", burn as "Measured burn", silhouette as cm
  deltas. Every hero number and delta renders a settings-driven unit glyph
  ("81.2 kg", "↓ 0.3 kg") per R-D10 — a unitless body number is a defect.
  The trend card's sparkline renders in every Day-loop state (continuity
  over height economy), and on check-in day the check-in card carries the
  number once while the trend card carries the series only (R-D11 dedup).
  Chip words name user facts, not mechanisms. **Amended (owner ruling,
  round 8):** a card header names the card and never carries a number's
  provenance — the "updates weekly · <date>" header chip is retired. A
  derived number wears its provenance inline instead: the ring target reads
  "of 1,900 kcal ⓘ" (held: "of 1,950 kcal · provisional ⓘ"), and the ⓘ
  tap-through explainer holds the mechanism ("adaptive — recalculated at
  each weekly check-in from your measured burn and weight trend · last
  updated <date>").

- **R-D13 — Day-loop card names are literal (owner ruling, round 5).**
  Hub cards name their content: "Meals · today" / "Meals · tomorrow" — the
  word "plan" is reserved for the F03 week plan (Plan tab, plan vN). The
  meals card is forward-looking only: one row for the next open meal
  (planned kcal + provenance) beside its CTA, and a header count for the
  day's coverage; confirmed meals appear exactly once, in the diary (R-D11).
  A future sport schedule (F05) earns its own surface — it never shares the
  meals card; until then exercise stays "context, never credit" (R-B2) in
  the Close-the-day row.

- **R-D14 — Day-loop cards are content-rendered (owner ruling, round 6).**
  A card renders because its content exists, never as a fixture: the diary
  appears with the first entry, "plan vs. actual" with a plan, and the meals
  cards only while an open planned slot exists. Absence is silent — a user
  who never plans meals (or lets a plan lapse) sees no meals card, no empty
  state, no planner upsell: F01's budget powers the ring for everyone, and
  F03 is discovered in its tab, onboarding, and the check-in hand-off, never
  nagged from the day loop; the ~19:00 "plan tomorrow" surface is likewise
  plan-conditional. When a planned slot does render, its full decision set —
  confirm · ate something else (→ F02) · swap (→ F03) · not having it — sits
  one tap behind the row, with the happy-path button as the sole visible
  affordance; skips and replaces are neutral decisions that shrink the header
  count, and a slot still open past its day is never penalized.

- **R-D15 — No self-congratulation (owner ruling, round 7).** Screen copy
  never narrates the app's own performance: no speed claims ("logged in
  6 s", "saves instantly", "under 1 s"), no ease counts ("one tap", "two
  taps to done", "8 quick swipes"), no pre-emptive capability pitches
  ("works offline" before the fact). The app proves itself by being useful —
  success is shown by the updated state (the ring sweeps, the entry
  appears), affordances are stated as instructions ("tap for history",
  "tap to reopen"), and a capability is disclosed only as a post-hoc fact
  at the moment it happened ("worked offline — nothing left your phone").
  Data-practice disclosures at the trust decision (onboarding's no-account /
  computed-on-this-device lines) are facts, not boasts, and stay. Timing and
  effort numbers belong to annotations for the design reader, never to the
  screen.

---

## 4. Review notes (all 13 specs, read in full)

| Doc | Verdict | Highlights | Watch-outs |
|----|---------|-----------|------------|
| F01 | **Pass** | Plan-as-versioned-document with diffs; calorie-floor "haptic wall" refusal; zero-account, <3 min | Cold-start forecast dependency → resolved by R-A5; quiz length → R-S6 |
| F02 | **Pass** | Correction loop as hero flow; sanity rails ("anti-27M-kcal guarantee"); personal accuracy stat | Diary ownership → R-B1; OFF cache budget needs a sizing decision at impl. |
| F03 | **Pass** | Reconciliation-not-regeneration treaty with F04; adherence defined honestly; leftovers charged to cook day | Seed library → R-S3; `replaced` semantics → R-B1 |
| F04 | **Pass** | Unit-aware consolidation incl. the "don't fake cross-unit" honesty rule; delta-chip reconciliation | Consent gap → R-C3; servings-change reconciliation UX → prototype |
| F05 | **Pass** | "Readable rule engine" (anti-Fitbod as a feature); expenditure "context, never credit" enforced in the write path | Recovery constants need publishing; library licensing → R-S2 |
| F06 | **Pass** | Trend-first ritual with kind haptics; method-registry body fat; EAV store powers the whole app | Zero-phase smoother spec → implementation decision w/ documented UX; α → R-A2 |
| F07 | **Pass — flagship** | Deceleration forecast (the 19-app gap); dual-engine transparency; Apply-only write authority; decision ledger | Targets contract → R-B2; constants → R-A1/A3; engine telemetry page is [future], resist promoting early |
| F08 | **Pass** | No-cloud-by-construction privacy; named-guardrail pose gating; quiet-scale payoff card | Angle set → R-U6; retention → **superseded by R-U16 (vector-only, no photos ever)** |
| F09 | **Pass** | Plan↔poop correlation moat; unlock-progress-ring fixes cold-start abandonment; flawless discretion spec | FODMAP vocabulary → R-S8; fiber target → R-B3 |
| F10 | **Pass** | 5-second doctrine as a *measured* invariant; Nudge Contract enforced in code review; owns composition only | Nudge cap → R-U1; check-in hero-slot promotion → R-U17 |
| F11 | **Pass** | Forgiving economy with no purchase path "structurally impossible"; `held` gating generalized; Consistency Score's risks spec'd honestly | Report day → R-U2; score default → R-U3; streak neutral days → R-U10 |
| F12 | **Pass — constitution** | Hash-chained receipts; payload-preview consent sheets; "WLO Pure" no-INTERNET flavor | Category boundaries → R-C1–C7; local-LLM runtime is v1.x, don't block v1 on it |
| F13 | **Pass** | Staged-and-validated restores ("corrupt import can't destroy data"); egress list as a user-facing screen | License → R-S1; Health Connect nutrition granularity → R-S9 |

**Cross-cutting review findings (consistency checks that passed):** consent
taxonomy referenced identically across 9 docs; write-authority for Targets
stated compatibly in F01/F02/F05/F07; provenance and `held`-gating patterns are
uniform; tone rules (banned-words lists) appear in F02/F03/F05/F06/F08/F09/F10/F11
in mutually consistent forms. **Fixed in this doc rather than by editing specs:**
all R-* rulings above (the specs explicitly deferred them here). A 2026-09-11
alignment pass re-synced spec body text that had drifted behind rulings —
F01 (import scope, Fresh Start wording), F04 (consent wording, consolidation
math), F07 (engine version tags), F08 (angle set), F09 (fiber target), F11
(Wrapped date), F13 (converter tags); no spec contradicts a ruling as written.

---

## 5. Long-range feature-complete scope (formerly the v1 roll-up)

This is the full-suite target represented by historical `[v1]` tags. It does
not override the sequenced Release 1 matrix in §2.0: zero-account onboarding + plan studio
(F01) · full input ladder with correction loop + sanity rails (F02) · planner +
generation + swaps (F03) · generated list, pantry, staples, reconciliation
(F04) · logging loop, rule engine, heatmap (F05) · trend weight, smoothers,
measurements and EAV (F06) · Transparent engine, check-in, 3-band
decelerating forecast, Algorithms page (F07) · capture ritual, vector-silhouette Archive,
timeline/compare (F08) · Bristol logging, on-device classifier pre-select, fiber
target, red flags, doctor export (F09) · Hub, Adaptive Day Model, Nudge
Contract, widget (F10) · stats hub, report card, forgiving streaks, share cards
(F11) · model zoo, BYOK, consent matrix, receipts (F12) · vault, versioned
export, SAF backup, biometric lock, Health Connect, Bluetooth scales, storage dashboard (F13).

Deliberately **not** v1: adaptive spline engine, cook mode, recipe-URL/photo
importers, competitor converters, cost tracking, correlations, Wrapped,
Consistency Score, Wear OS, cloud paths beyond BYOK-consented ones.

## 6. Still genuinely open (needs prototypes, data, or an owner call)

1. Zero-phase smoother implementation + its "recent values revise" UX (F06).
2. Recovery-model constants for F05 (must be WLO's own, published).
3. ARCore-less device tier: portion fallback strategy (F02).
4. OFF offline cache sizing vs lazy cache (F02/F13).
5. Exercise library content pipeline (author vs curate) — follows R-S2.
6. Classifier architecture for F09 when personalization lands (F12 platform).
7. ~~Badge visual direction~~ — resolved: R-D7 (minimal geometric,
   numbers-first).
8. F04 servings-change reconciliation pattern (prototype with F03).

---

## Appendix A — The Targets schema (ratified, WLO-0006)

*The contract every target-consuming feature reads. Authority is frozen by
R-B2; this appendix freezes the object itself.*

### A.1 Document shape

`Targets` is a versioned document, one active instance per profile (R-B9:
v1 is single-profile; the `profileId` field ships from day one). Versions
are immutable: every write creates `vN+1`; "revert" is a new version that
copies an older one, never a history rewrite. Each version carries the
envelope `{schemaVersion, version, parentVersion, createdAt, createdBy}`
(`createdBy` ∈ `studio@F01` | `apply@F07`) plus:

- **`goal`** — `{targetWeightKg, pacePctPerWeek, targetDate?}`; `targetDate`
  is advisory and always rendered as its implied pace (F01 §3).
- **`energy`** — `{cadence: daily|weekly, budgetKcal | weeklyBudgetKcal,
  schedule[7], floorKcal}`. Schedule entries are kcal per weekday and, when
  cadence is weekly, must sum exactly to the weekly budget (F01's pinned
  invariant). Floor defaults 1,200 F / 1,500 M (F07 §3); override requires
  a persistent acknowledgment and can never weaken validation.
- **`macros`** — a preset name or custom `{proteinG|proteinPct, carbPct,
  fatPct}` plus optional rings `proteinFloorG`, `carbCapG` (keto),
  eating-window rules for IF variants — one schema, no special cases
  (F01 §3).
- **`fiber`** — `{targetG}`; default authored by F09 at plan creation
  (R-B3). Exactly one number; plan divergence renders in F03, never here.
- **`water`** — `{targetMl}`; intake is logged by F02 (one-tap quick-add +
  drink entries).
- **`workout`** — `{cadencePerWeek}` (context for F05; read-only there).
- **`surfaces`** — which rings/timers/cards pin where (from the F01
  template; consumed by F02/F10).

### A.2 Write API

Writers are exactly two, enforced by the write path, not convention:

1. **F01** — plan creation and Studio edits; every save is a new version
   with a human-readable diff and one-tap revert.
2. **F07** — adaptive adjustments, **only via explicit Apply** at check-in:
   atomic, ledgered in the check-in decision ledger (input snapshot,
   estimate, decision), reversible through the same ledger.

Validation invariants — rejections are hard, never silent clamps:

1. **Floor** — automated targets may not budget below `floorKcal`.
   Owner amendment, 2026-09-18 (WLO-0126): explicit manual food-intake values
   may be saved below this floor, at maintenance, or opposite the weight-goal
   direction. `energy.manuallyEntered` records this distinction in Targets
   schema v2; v1 documents retain floor enforcement. Manual values must be
   finite and non-negative; schedule consistency still applies. This is a
   user choice, not a recommendation or a clinical eligibility attestation.
   F07 clears manual provenance and revalidates the floor on every Apply.
2. **No eat-back** — exercise expenditure is structurally incapable of
   raising eating targets; F05/F13 inputs are read-only context.
3. **Schedule sum** — weekly-cadence schedules must sum to the weekly
   budget.
4. **Pace and eligibility** — automated recommendations and forecasts must first pass the shared
   WLO-0080 eligibility result. Pace is rejected, never silently clamped: loss
   uses the lower of 1.0% body weight/week and 0.9 kg/week; maintenance uses
   zero; gain uses WLO's conservative 0.5%/week product cap. See the cited
   [safety contract](../research/weight-goal-safety-contract.md); these are app
   support boundaries, not individualized medical advice. Manual intake edits
   preserve the weight goal and do not infer or require a chosen pace; a held
   forecast never blocks saving that manual intake.
   Owner amendment, 2026-09-18 (WLO-0126): the food-intake editor additionally
   provides an **intake scenario**, distinct from a recommended goal plan.
   This adaptive-horizon path is driven by energy balance, not the goal's
   direction; it includes maintenance, movement away from the goal and
   continuation past a target crossing. Recommendation floors/pace limits
   do not suppress this hypothetical manual-input scenario. Adult model applicability, known unsupported health context, held-data rules
   and finite/physically meaningful model output remain applicable. Unanswered
   health context alone does not hide an exploratory scenario. Health answers
   are owned by Profile, not weight/intake forms; recommendations retain their
   explicit eligibility checks. Invalid/unavailable states retain the full chart space.
   Goal-directed scenarios extend through the modeled arrival range, with
   padding; a goal changes framing, not the energy equation. Plateau/no-crossing
   is distinct from a display cutoff. Maintenance and away-from-goal scenarios
   retain a six-month viewing window. F07's automated-target gates are unchanged.

AI proposals (template refinement, [v1.x] per R-S10) are drafts against
this API: apply/discard, never auto-commit.

### A.3 Read API: the day projection

Consumers never parse the schedule themselves; they read the derived **day
projection** — `{date → kcal, macros g, fiber g, water ml}` resolved from
cadence + schedule + applied F07 deltas, cached, and provenance-chipped
("adaptive · check-in Sep 8"). F02 renders it as the budget ring, F03
generates against it, F10 quotes it, F11 grades against it. All other
features depend only on the projection, never on Targets internals.

### Weight overview owner amendment — WLO-0104 (2026-09-16)

The approved page-based layout replaces the overview's stacked cards. Show individual raw weigh-in dots behind the trend, an always-in-domain yellow goal line, and date/weight-only point tooltips. Remove inline debug details, legend prose, persistent tuning/math controls and opaque policy jargon. Settings keeps accessible algorithm explanations and preview reset; goal/history remain navigable. Derived provenance is available on demand rather than requiring a badge on every overview number. The weigh-in action has reserved space and must not cover content. This supersedes earlier fixed30-day overview-change and visible-tuner requirements; weekly/canonical metrics used elsewhere retain their named horizons.

### Owner refinement — WLO-0117: reference target editing

A user-entered target weight is a personal reference, distinct from automated
pace, calorie-plan or forecast recommendations. Weight's goal sheet accepts a
positive finite target without requiring pace, a desired date, mode selection,
calorie budget or screening. This supersedes A.2 #4's eligibility prerequisite
only for this target-only operation. All forecast/recommendation eligibility
checks remain in force. Do not infer health answers or enable forecasts from
saving a reference target.

Target-only writes use the existing Studio writer and immutable Targets
versions. Existing plan fields (including legacy pace/date) are preserved;
first reference targets have pace zero, no date and no calorie budget. Saving
a target never silently adjusts calorie intake. Above/below/at-target copy is
computed from the current trend; it is not an automatic plan change. The
routine sheet has one target input, live comparison, Done/Save, and dismiss to
discard. Milestones, planning and forecast setup are absent from this surface.


#### Owner navigation amendment — WLO-0119 (17 September 2026)

App preferences and data administration live under More → Settings: AI, diagnostics, Data & backup, and Health Connect. Personal profile belongs directly under More; Diet plan belongs under Plan; target weight is edited from Weight. This supersedes earlier Settings placement for these product workflows. Unimplemented secondary features are omitted from the More directory while existing routes remain compatible. Per-capability AI consent and data transaction semantics are unchanged.
