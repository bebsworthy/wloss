# F10 — Daily Hub & Nudges — Functional Specification

## Identity

| | |
|---|---|
| **Feature ID** | F10 — Daily Hub & Nudges |
| **Provides** | The app's single home surface — one glance answers "how am I doing, what's left today, what's next" — plus the whole app's respectful notification policy. |
| **User problems solved** | • "I don't want to dig through five screens to see how today is going." • "If logging takes more than a few seconds, I'll stop doing it." • "Diet apps nag and guilt me until I mute them, then I quit." |
| **AI consent category** | none in v1 — F10 composes and deep-links; nudge copy is local templates. The frozen consent matrix lists F10 as an opt-in `insights-chat` consumer for AI-drafted nudge copy [v1.x]; it is never required and never changes what the Hub renders. |
| **Primary evidence** | `docs/research/gyroscope.md` (three-layer pyramid: daily hero card → weekly card → composite; 60fps motion culture), `docs/research/happy-scale.md` (trend-first framing), `docs/research/synthesis.md` §5 (trend-first numbers, card-as-artifact, data-quality gating), `docs/research/foodvisor.md` (guilt-alert anti-pattern), `docs/research/cal-ai.md` (streak-loss rage loops), `docs/research/openscale.md` (home-screen widget, demo data for empty states) |

## 1. Purpose & Core Objectives

The Daily Hub is WLO's front door and its integration surface. It compresses the
state of every tracking feature — F02 food, F05 exercise, F06 weight, F07 energy,
F08 silhouette, F09 gut — into one card stack and one quick-action rail, so the
answer to "how am I doing today?" never requires navigation. It also owns the
notification strategy for the whole app, because nudges are a product-wide voice,
not a per-feature setting. The Hub computes no science: it composes, routes,
and schedules.

Core objectives (verifiable):

- **5-second doctrine.** Every daily log — meal photo, weigh-in, poop entry,
  workout start — starts on the Hub and completes in ≤2 taps, median ≤5 s,
  measured in-app and surfaced as a stat (fed to F11 as "log efficiency").
- The first screen answers three questions without scrolling: trend direction,
  calories remaining, next planned action.
- **Nudge contract:** 100% of notifications are actionable (a tap performs or
  deep-links), default cap ≤1/day, quiet hours on by default, zero guilt-framed
  copy (enforced by a copy checklist in code review), one-tap global pause.
- Fully functional offline; no card layout or state depends on the network.
- Every card and action deep-links to its owning feature; the Hub owns no
  domain data.

## 2. User Moments — when and how it is used

- **Many times a day:** meal logging (kitchen/table, time pressure), poop log
  (bathroom, privacy), workout start (gym, sweaty hands → large touch targets).
- **Daily rituals:** the morning weigh-in (bathroom, before breakfast — the most
  stable window per F06 guidance); the evening couch moment (review the day,
  plan tomorrow).
- **Weekly:** Sunday entry point into the F07 check-in card.
- **Most common flow, end to end:** unlock → glance hero card → tap the camera
  in the quick-action rail → F02 captures and estimates → confirm → the Hub's
  ring animates and the streak chip ticks. Four interactions, ~5 seconds.

## 3. How It Works — functional mechanics

**Inputs.** From F06: trend weight, delta vs. 7/30 days, progress-ribbon data,
last weigh-in timestamp. From F07: adaptive target, consumed/planned energy,
data-quality state (`developing`/`updating`/`held`), check-in due flag. From F03:
today's planned meals and the next meal. From F05: scheduled workout, live
session, PR events. From F09: last entry, regularity context. From F08: next
silhouette capture due. From F11: streak state, "report card ready". From F01:
goal statement, active plan name. From the device: clock, notification
permission, widget bounds.

**Processing (all on-device, deterministic):**

- **Adaptive Day Model** — a rule-based state machine over time-of-day plus
  day-completeness that reorders cards and re-ranks quick actions. Morning
  (until user-set, default 10:30): weigh-in card leads if none yet. Midday:
  next planned meal + photo action lead. Evening (from ~19:00): "close the
  day" and "plan tomorrow" surface. Night: minimal layout, recap only.
- **Day-completeness model** — which expected logs exist (weigh-in, meals vs.
  plan, workout due — **never gut entries or silhouette captures: irregular
  rhythms are normal, R-U13**); drives card states: `done`, `open`,
  `muted-by-choice`.
- **Nudge scheduler** — a single priority queue for the whole app: default
  cap 1/day (0–3 configurable), quiet hours default 21:30–07:30, per-category
  opt-outs (meals, weigh-in, workout, check-in, report card, capture-due (F08),
  digestion-gap (F09) — every reminder type draws from this one budget, R-U1),
  snooze presets
  (1 h / tonight / tomorrow), and dismissal memory — a dismissed nudge never
  re-fires for the same gap, and gaps never escalate in urgency.
- **Deep-link registry** — every card and action declares its owning feature,
  route, and prefill payload (e.g., "log as planned" prefills F02 from F03).

**Outputs.** The rendered card stack; the home-screen widget payload; notification
intents (all deep-linking, all self-dismissing after their action completes);
the day-closure recap artifact [v1.x]; local seconds-to-log counters for F11.

**State owned.** Nudge settings, schedules, and dismissal memory; card order and
layout preferences; widget configuration; Adaptive Day Model transitions; recap
history [v1.x].

## 4. User Interaction Model

**Entry points.** Cold launch (default tab); widget zones (each deep-links);
notification taps and action buttons; Wear OS glance [future]; app shortcuts
(long-press icon).

**Primary flows.**

1. *5-second meal log (happy path):* Hub → rail camera (tap 1) → F02 full-screen
   capture → estimate card → confirm (tap 2) → done, back on Hub with the ring
   updated. *Fallback A — no on-device result / consent off:* F02 manual
   quick-add (calories + optional name) in ≤10 s. *Fallback B — bad photo:*
   retake or "describe instead" (F02 text path). The Hub never blocks on AI.
2. *Morning weigh-in:* the weigh card stays first until logged → tap (1) → F06
   numpad prefilled with last weight, ±0.1 stepper → confirm (2) → the hero card
   flips to trend-first framing ("Trend 179.1, down 0.6") with a count-up.
3. *Evening plan-tomorrow:* card appears after ~19:00 → tap → F03 tomorrow view
   with a "copy today" preset → one confirm.

**Input minimization.** The user never types on the Hub: meal identity comes from
photo/voice/recents (F02), weight from prefill + stepper, poop from a 2-tap
Bristol picker (F09), workouts from last-session prefill (F05). Zero-tap: "log as
planned" replays the F03 meal into F02 without any field entry. Long-press the
camera icon = quick-add calories only.

**Micro-interactions (concrete).**

- Hero numeral: spring count-up ~600 ms on change; the delta chip crossfades to
  green/neutral (never alarm-red) with a 150 ms scale pop.
- Calorie ring: 400 ms ease-out sweep on every log; a single soft haptic tick at
  sweep end; landing inside the target band in the evening = a double
  micro-bounce + success haptic.
- Quick-action rail: 32 ms press-scale + tick haptic per icon; long-press to
  pin/reorder with a lift shadow.
- Card collapse: swipe-up folds a card into a one-line chip; undo snackbar 5 s.
- Notification feel: silent channel, no sound, one vibration pulse; expanded view
  carries up to two action buttons ("Log breakfast" / "Snooze 1 h") that
  deep-link and self-dismiss with an animated collapse — never a vanish.
- Day-closure recap [v1.x]: at the quiet-hours edge, if anything was logged, one
  expandable "Day closed" card/notification with three stat chips and a share
  glyph.

**Data-quality gating.** No trend chip until ≥3 weigh-ins exist (mirrors F06);
no forecast ETA while F07 is `held` — the ring falls back to the static F01
target labeled with a "provisional" provenance chip that taps through to the
explainer; "plan vs. actual" only renders when a plan exists.

## 5. What the User Gets Out

- **Hero number card** — trend-first weight framing ("Trend 179.1, down 0.6")
  with the progress-ribbon strip from F06; tap → F06.
- **Calories-remaining ring** vs. the adaptive target from F07, with macro dots
  and a provenance chip ("adaptive · check-in Sep 8"); tap → F07.
- **Meals · today** — the next F03 meal as a card with one-tap "log as planned" (R-D13: forward-looking only; confirmed meals live solely in the diary; "plan" is the week plan's word).
- **Quick-action rail** — photo-log (F02), weigh-in (F06), poop (F09), workout
  (F05); icons badge-dot when their owner has news (e.g., a PR from F05).
- **Streak chip and report-card-ready pill** from F11 (display only).
- **Backup-health dot** from F13 (last successful auto-backup).
- **Provenance rule:** every number on the Hub is derived elsewhere; the Hub
  renders the owner's provenance chip and deep-links to its "how we got here".

## 6. Motivation & Psychology

The Hub earns the return visit by being the lowest-effort place to keep the
streak alive and watch the trend tick — progress is ambient, not demanded. It
consumes gamification from F11 and never computes it. Tone is adherence-neutral
(MacroFactor pattern): invitations, not verdicts — "Breakfast is still open —
want the 5-second photo path?" never "You missed breakfast." Nothing is ever
color-coded as failure; the ring shows *state*, not *sin*. The category's most
cited churn trigger is guilt notifications (Foodvisor's alerts are the documented
anti-pattern) and streak-loss rage (Cal AI's restore fee): WLO never sends
either. The escape hatch is always one tap away — "Pause nudges for a week"
sits in the Hub's overflow menu and on every notification.

## 7. Relations to Other Features

- **Consumes from:** F01 (goal framing, plan name) · F02 (quick-log capture, log
  state) · F03 (today's plan, "log as planned" prefill) · F05 (sessions, PR
  events) · F06 (trend, weigh-in state, ribbon) · F07 (adaptive target, ring
  values, quality states, check-in due) · F08 (capture due) · F09 (log state) ·
  F11 (streak chip, report-ready) · F13 (backup-health dot, widget persistence).
- **Feeds into:** the same features (deep-link traffic with prefills); F11
  (day-completeness events, seconds-to-log stats); F13 (recap artifacts, Hub
  settings included in backups).
- **Shared concepts:** Targets, Logs, Provenance, Trend weight, Consent (F12
  toggles gate which capture paths the rail even offers, e.g., cloud-estimate
  chips), Data-quality states.
- **Boundary:** F10 owns composition and notification policy only. Domain
  numbers and log flows belong to their owners; gamification rules belong to
  F11; the Hub is their stage, not their engine.

## 8. Blue Sky Ideas

- **[v1] Adaptive Day Model** — time-of-day reordering and day-completeness
  card states, fully rule-based and inspectable.
- **[v1] Nudge transparency** — long-press any notification → "Why am I seeing
  this?" plus inline tuning (frequency, categories, quiet hours) and a live
  preview of the next scheduled nudge.
- **[v1] Home-screen widget** — 4×2 hero number + ring; every zone deep-links.
- **[v1.x] Day-closure micro-recap** — the "Day closed" artifact with three
  chips (trend delta, meals, movement), rendered as a shareable mini-card in
  the Gyroscope card-as-artifact style.
- **[v1.x] Interactive widget actions** — quick-log buttons inside the widget
  (within Android RemoteViews constraints; graceful fallback to deep-links).
- **[future] Wear OS glance + tile** — trend and calories-remaining on the
  wrist; one-tap weigh-in and workout start; complication for the ring.
- **[future] On-device nudge-timing learner** — a private model learns the
  minutes of day *this* user actually logs and schedules nudges then; never
  more insistent, only better timed. No cloud, ever.
- **[moonshot] Zero-open day** — a fully logged day without opening the app:
  widget + notification actions + Wear cover the entire 5-second doctrine from
  any surface; the Hub becomes a state machine you can traverse remotely, and
  the app celebrates it with a "you never had to open me" recap.

## 9. Guardrails, Privacy & Sensitivity

- Notifications are opt-in at the Android-13 runtime level; the permission is
  requested *in context* — once, after the first successful log, with a preview
  of exactly what WLO's notifications look like. Rejection is never re-prompted
  manipulatively.
- Hard rules: no guilt or shame framing anywhere in nudge copy; no streak-loss
  notifications, ever; every notification actionable; quiet hours absolute
  (nothing scheduled inside them except a user-chosen recap at their edge);
  default cap ≤1/day.
- The widget can show sensitive numbers on a shared lock screen; a "hide values
  on lock screen" toggle redacts them to dots.
- The Hub is fully offline; it renders no spinner that implies a server.
- Objective invariants hold: free forever, no account, no SaaS, no telemetry in
  any Hub surface.

## 10. Open Questions

- Default nudge cap: 1/day (proposed) vs. 2/day for the first two weeks.
  *(Resolved: R-U1 — default 1/day, user 0–3.)*
- Is the day-closure recap a notification, an in-app card, or both at v1.x?
  *(Resolved: R-U9 — in-app card first [v1.x]; notification only by explicit opt-in.)*
- Widget interactivity ceiling per Android version — where is the fallback line
  between RemoteViews buttons and plain deep-link zones?
- Wear OS scope: glance-only at [future], or include logging actions?
- Should the Sunday F07 check-in card be promoted into the Hub's hero slot on
  check-in day, or stay a subordinate card?
  *(Resolved: R-U17 — promoted into the hero slot on check-in day.)*
