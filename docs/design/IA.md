# WLO Information Architecture

*Phase B deliverable (`docs/design/KICKOFF.md`). Companion:
[DESIGN-SYSTEM.md](DESIGN-SYSTEM.md) (tokens & components). Authority:
[FEATURES §2.0](../features/FEATURES.md) (weight-first release contract), then
§2.2–2.3 (Day/Week loops), R-U7 (naming), R-U12 (one widget framework), and
R-U17 (hero-slot promotion).*

---

## 1. Navigation model

**Four functioning release destinations + flow-over-context capture layers**
(R-D2 release amendment, WLO-0096): Weight · Hub · Plan · More. Insights
returns when it has a usable landing screen; legacy Insights links explain
that deferral on More. Compact windows use a Material 3 navigation bar; windows at 600 dp
or wider use a Material 3 navigation rail. Nested destinations hide that
top-level chrome and use a Material 3 top app bar with Up navigation.

```
┌────────────────────────────────────────────────┐
│                                                │
│   < surface >                                  │
│                                                │
├────────────────────────────────────────────────┤
│ Weight         Hub          Plan          More │
└────────────────────────────────────────────────┘
```

| Tab | Feature | Contents |
|---|---|---|
| **Weight** | F06 + F07 | **Default and primary surface**; trend/raw chart, goal state, weigh-in, history, forecast, and logbook |
| **Hub** | F10 | Optional broader day surface; Adaptive Day Model card stack and quick-action rail |
| **Plan** | F03 + F04 | Segmented: **Plan** (week grid) · **Recipes** · **List** · **Pantry** — the plan→shop→stock pipeline in one place, because the Week loop crosses it end-to-end |
| **More** | cross-feature | Your profile and Settings. Archive, Digestion and Exercise join when implemented |

**Decisions and rationale:**

- **Why Weight is first:** Release 1 promises a complete weight loop, so launch,
  first-run completion, app icon, widget, and weight notifications resolve to
  Weight. Hub remains a top-level day surface but is not an onboarding gate.
- **Why More:** five stable destinations fit Material navigation without
  hiding the release promise. Archive and Digestion remain plainly named,
  user-renamable surfaces inside More; placement is sequencing, not shame.
- **Food capture is not a tab.** F02's shutter is the Hub's lead action (and
  may be a FAB on relevant broader-suite surfaces), while weigh-in is Weight's
  primary action. Food capture opens
  tab surfaces), and opens **full-screen over whatever context invoked it**
 — logging is a flow, not a destination. Entry points (all spec'd): Hub
  rail, today's-plan card ("log as planned" prefills from F03), "correct"
  on any diary entry, notification quick-capture, Food Memory one-tap.
- **F06/F07 own Weight.** Weigh-in, trend, goal progress, forecast, history,
  and logbook form one primary destination. Advanced body metrics stay below
  the core weight loop and follow the release matrix.
- **F01 (Diet plan)** lives in Plan → Diet plan, plus its flow
  roles: first-run wizard, F07 check-in "adjust plan" deep link (pre-filled
  diff), F10 30-day review nudge, post-import CTA.
- **F12 (AI Studio)** = Settings → AI. **F13 (Data Vault, Scales, backup)**
  = Settings → Data & backup / Health Connect. Neither is a tab: consent and plumbing
  are visited deliberately, not browsed (F12's point-of-use sheets carry
  consent to where the data is instead).
- **Settings** is in More and remains reachable from every top-level surface's
  overflow. No drawer — WLO has no surface that earns a second navigation axis.
- **Back as a loop-closer:** capture and check-in flows commit-or-cancel
  back to the invoking context (the Hub ring animating on return is part of
  the save arc, F02).

### 1.1 Badge / attention model

Tab badges are *never* nag counters. The only badge states: a check-in-ready
indicator on Weight, report-card-ready pill
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
| F04 list widget | [v1.x] | Per-aisle count bars; tap opens list; check pulses count down; rides the same Glance stack |
| Wear OS glance | [future] | Trend + calories remaining; weigh-in/workout one-taps |

Implementation note (from platform research): Glance forbids `Canvas` —
sparklines pre-render to bitmaps; quick actions are broadcast round-trips,
so the widget stays coarse (deep-links + ≤2 actions).

---

## 5. First-run (F01): reach a useful Weight surface first

Release 1 shape (zero account, zero network): one welcome card → app-wide
body-mass unit (`kg`/`lb`) → optional loss/maintenance/gain goal → choose
Health Connect/file import or manual first weigh-in → **Weight**. If goal or
measurement is skipped, Weight renders an honest empty state with the relevant
single action. Diet-plan setup is offered later from Hub/Plan and is
never required to complete first run.

Rules: every step after unit is skippable — skipped fields are absent rather
than invented; derived cold-start values are flagged `estimated`,
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
- **Sensitive-surface protection:** Archive lock gate, F09 hold-to-reveal and
  conceal toggle. FLAG_SECURE on all F08 screens and any F09 screen showing a
  photo. Weight heroes remain immediately readable; the proposed
  F06 tap-to-reveal mode was rejected as unnecessary complexity (2026-09-16).
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

No navigation drawer, no second navigation axis, no per-feature bottom sheets
stacked on tabs, no notification-center-style in-app inbox (nudges live as
cards on the Hub), no settings search (Settings uses grouped preferences and focused detail screens), no social layer of any kind. If a future feature needs
discoverability, it earns a Hub card through the Nudge Contract — not a new
navigation surface.


### WLO-0119 — settings hierarchy amendment (17 September 2026)

More contains Your profile and Settings. Goals remain in Weight; Diet plan is reached from Plan. AI and Data & backup have one administrative home under Settings. Legacy destinations remain registered; placeholder features are omitted from More until usable.

Settings groups Everyday use (weight unit dialog, reminder), Privacy (app lock, AI, diagnostics), and Data (Data & backup, Health Connect). Native M3 list rows show current simple preference values; filled surfaces group related rows without individual outlined cards. Health Connect is independent from file operations. Backup owns scheduling; Storage names attachment deletion explicitly and requires confirmation. Cloud permissions remain independent per capability and distinct from installed local models.
