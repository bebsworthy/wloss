# WLO Information Architecture

*Phase B deliverable (`docs/design/KICKOFF.md`). Companion:
[DESIGN-SYSTEM.md](DESIGN-SYSTEM.md) (tokens & components). Authority:
[F10](../features/F10-daily-hub.md) (Hub is the single home surface),
[FEATURES §2.2–2.3](../features/FEATURES.md) (Day/Week loops), R-U7 (naming),
R-U12 (one widget framework), R-U17 (hero-slot promotion).*

---

## 1. Navigation model

**Five-tab bottom navigation + one flow-over-context capture layer**
(proposed as R-D2):

```
┌────────────────────────────────────────────────┐
│                                                │
│   < surface >                                  │
│                                                │
├────────────────────────────────────────────────┤
│  Hub    Plan    Insights   Archive   Digestion │
└────────────────────────────────────────────────┘
```

| Tab | Feature | Contents |
|---|---|---|
| **Hub** | F10 | Default surface; Adaptive Day Model card stack; quick-action rail; weigh-in entry |
| **Plan** | F03 + F04 | Segmented: **Plan** (week grid) · **Recipes** · **List** · **Pantry** — the plan→shop→stock pipeline in one place, because the Week loop crosses it end-to-end |
| **Insights** | F11 | Report card, stats hub, streaks, badges, share cards |
| **Archive** | F08 | Lock gate → timeline grid, compare studio, capture ritual (name frozen by R-U7; user-renamable) |
| **Digestion** | F09 | Quick-tile Bristol entry, heatmap, fiber target, correlations (name per R-U7; user-renamable) |

**Decisions and rationale:**

- **Why tabs at all:** the specs name tab-level homes — F03 "Planner tab",
  F11 "Bottom-nav Insights tab", F08/F09 "nav section". A pure hub-centric
  stack would bury flagship surfaces behind taps.
- **Why Archive and Digestion are visible tabs, not a "More" sheet:** both
  are differentiator features; both are already discretion-gated (biometric
  lock, R-U7 naming). Hiding them behind "More" would read as the app being
  ashamed of them — the exact instinct R-U7 exists to prevent. Renaming
  remains the user's discretion lever.
- **Capture is not a tab.** F02's shutter is the *primary action of the
  app*, so it lives on the Hub as the lead quick-action (plus FAB on other
  tab surfaces), and opens **full-screen over whatever context invoked it**
 — logging is a flow, not a destination. Entry points (all spec'd): Hub
  rail, today's-plan card ("log as planned" prefills from F03), "correct"
  on any diary entry, notification quick-capture, Food Memory one-tap.
- **F06 (Weight & Body) has no tab.** It is reached in the Day loop: the
  weigh-in card (morning), the hero card tap (trend/history), girth entries,
  logbook. Its full history is one tap from the number the user is already
  looking at.
- **F01 (Plan Studio)** lives in Settings → Plan Studio, plus its flow
  roles: first-run wizard, F07 check-in "adjust plan" deep link (pre-filled
  diff), F10 30-day review nudge, post-import CTA.
- **F12 (AI Studio)** = Settings → AI. **F13 (Data Vault, Scales, backup)**
  = Settings → Data Vault / Scales. Neither is a tab: consent and plumbing
  are visited deliberately, not browsed (F12's point-of-use sheets carry
  consent to where the data is instead).
- **Settings** sits top-right on the Hub header (gear); every tab reaches it
  via its own overflow. No drawer — WLO has no surface that earns a second
  navigation axis.
- **Back as a loop-closer:** capture and check-in flows commit-or-cancel
  back to the invoking context (the Hub ring animating on return is part of
  the save arc, F02).

### 1.1 Badge / attention model

Tab badges are *never* nag counters. The only badge states: check-in card
pending on check-in day (Hub hero, R-U17), report-card-ready pill
(Insights, R-U2), backup-health dot (Hub, F13). Gut/Archive never badge for
"not logged" (R-U13); their capture-due cards appear only on cadence-relative
reminder days (R-U13/R-U1 budget).

---

## 2. Surface map (default states by time of day)

The Adaptive Day Model (F10, rule-based and inspectable) reorders the Hub;
everything else is stable:

| Time | Hub card order |
|---|---|
| Morning (until user-set, default 10:30) | Weigh-in card → hero trend → budget ring → today's plan |
| Midday | Today's plan (next meal) → budget ring → hero → movement |
| Evening (~19:00+) | "Close the day" → "Plan tomorrow" → budget ring → hero |
| Check-in day (Sun default) | **F07 check-in card promoted to hero slot (R-U17)** → everything else shifts down; trend returns next day |
| Night | Minimal layout: hero + ring only |

Other surfaces' default states: Archive opens on its **lock gate**; Plan
opens on the week grid (Monday-forward); Insights opens on the latest report
card (or the stats hub after R-U2 day passes); Digestion opens on the quick
Bristol tile with stats below the fold.

---

## 3. Deep-link registry (aligned with F10 §3)

Every card, action, widget zone and notification button declares
`feature · route · prefill`. The registry (consumers: widget, notifications,
app shortcuts, F11/F07 cross-links):

| Source | Deep link | Target + prefill |
|---|---|---|
| Hub hero card | `wlo://weight` | F06 trend/history |
| Hub weigh-in card / rail weigh | `wlo://weight/log` | F06 entry, prefilled last weight, ±0.1 stepper |
| Hub ring | `wlo://energy` | F07 energy detail |
| Rail camera / shutter | `wlo://log/capture` | F02 full-screen capture |
| Long-press camera | `wlo://log/quick-kcal` | F02 kcal-only quick-add |
| Today's plan "log as planned" | `wlo://log/planned?slot=<id>` | F02 entry prefilled from F03 |
| Rail gut / Digestion tile | `wlo://gut/log` | F09 Bristol sheet (pre-selected type) |
| Rail workout | `wlo://exercise/start` | F05 prefilled session |
| Plan-tomorrow card | `wlo://plan/tomorrow` | F03 tomorrow view, "copy today" preset |
| Sunday check-in card / notification | `wlo://checkin` | F07 check-in card |
| Check-in "adjust plan" | `wlo://studio?proposal=<id>` | F01 Studio with pending diff |
| Report-ready pill / notification | `wlo://insights/report` | F11 report card |
| Streak chip | `wlo://insights/streaks` | F11 streak detail |
| Backup-health dot | `wlo://vault` | F13 vault |
| Widget zones (each) | zone-scoped variants of the above | same targets |
| F02 entry "correct" | `wlo://log/correct?entry=<id>` | F02 result card, editing state |
| F06 quiet-scale card | `wlo://archive/compare` | F08 compare (behind lock gate) |
| F09 meal follow-up chip | `wlo://gut/log?context=meal:<id>` | F09 Bristol sheet |
| F08 capture-due card | `wlo://archive/capture` | F08 ritual (behind lock gate) |
| F07 Algorithms / F12 receipts shortcut | `wlo://algorithms` · `wlo://ai/receipts` | F07 algorithms page · F12 receipt log |

Rule: **a deep link into a discretion-gated surface (Archive, Digestion
with photos enabled, F06 metrics under lock) always lands on the lock
gate, never on content** (F08 §4).

---

## 4. Widgets (R-U12: one framework, F10-owned)

| Widget | Tag | Shape |
|---|---|---|
| F10 hero widget | [v1] | 4×2: trend hero numeral + budget ring + sparkline strip; every zone deep-links; "hide values on lock screen" redacts to dots; refresh event-driven on save + daily schedule (Glance floor 30 min — see design research §2.8) |
| F04 list widget | [v1.x] | Per-aisle count bars; tap opens list; check pulses count down; rides the same Glance stack |
| Wear OS glance | [future] | Trend + calories remaining; weigh-in/workout one-taps |

Implementation note (from platform research): Glance forbids `Canvas` —
sparklines pre-render to bitmaps; quick actions are broadcast round-trips,
so the widget stays coarse (deep-links + ≤2 actions).

---

## 5. First-run (F01): a flow, not a modal gauntlet

Shape (spec §3, < 3 minutes, zero account, zero network): one welcome card
(value + architecture, one sentence each) → goal dials → live forecast
bloom (ESTIMATED chip) → template gallery → 8-card swipe quiz → schedule
bars (skippable) → milestone ladder → **Start** writes Plan v1 and lands on
the Hub.

Rules: every step is skippable — skipped fields are flagged `estimated`,
framed "later", never "incomplete" (F01 §6); no permissions asked during
the wizard — the notification permission is requested *in context, once,
after the first successful log* (F10 §4); "coming from another app?" import
branch available at first launch (F13 wizard); back is free; the forecast
appears within 30 s of entering a goal (F01 objective).

---

## 6. Accessibility & pragmatics

- **Contrast:** WCAG AA everywhere (§1.1 of DESIGN-SYSTEM); chart graphics
  ≥ 3:1; color never the sole carrier of state (chips carry text, ribbons
  carry deltas, halos carry labels).
- **TalkBack:** chart nodes announce purpose+result sentences; odometers
  announce final values only; every provenance chip is one focus stop with
  its state ("Estimated — check-in September 8, opens how we got here").
- **Touch floors:** 48 dp primary (density concessions only on display-only
  rows); bathroom/gym/aisle contexts never conceded (DESIGN-SYSTEM §3).
- **One-handed reach zones:** in-store check-offs and sweep (F04) and
  gym set-logs (F05) keep all flow-critical controls in the bottom third;
  the F05 keyboard pairing puts the plate calculator above the keyboard.
- **Discreet mode:** F06 hero-number hiding (§4), lock-screen widget
  redaction (dots), Archive lock gate, F09 hold-to-reveal + conceal toggle.
  FLAG_SECURE on all F08 screens and any F09 screen showing a photo.
- **Reduced motion:** all animation collapses to instant/100 ms crossfade;
  haptics become the primary channel (DESIGN-SYSTEM §4).
- **RTL:** layouts mirror, time axes and tabular numerals do not; number
  runs bidi-isolated.
- **Locale:** aisle taxonomy editable/renamable wholesale (F04), units and
  week-start are profile settings, dates and 7,700/3,500 constants are
  unit-aware (R-A1 display), first-run copy localized (tone checklist
  applies per locale).
- **Text scaling:** stat rows wrap to two lines rather than truncate
  numbers; hero numerals scale with font size (sp) — density never costs
  data.

---

## 7. What this IA deliberately does not have

No drawer, no second nav axis, no "More" tab, no per-feature bottom sheets
stacked on tabs, no notification-center-style in-app inbox (nudges live as
cards on the Hub), no settings search (Settings is one screen deep + Data
Vault/AI Studio), no social layer of any kind. If a future feature needs
discoverability, it earns a Hub card through the Nudge Contract — not a new
navigation surface.
