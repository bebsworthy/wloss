# WLO Design System

*Phase B deliverable (`docs/design/KICKOFF.md`). Companion docs:
[IA.md](IA.md) (navigation & surfaces) · [research/synthesis.md](research/synthesis.md)
(pattern sources) · [../features/FEATURES.md](../features/FEATURES.md) §3 (binding
rulings). Design tokens, the consolidated motion/haptics table, and the
component inventory swept against all 13 specs. Implementation is Compose (a
later phase); values here are the contract prototypes and code both follow.*

---

## 0. Principles (the bar, operationalized)

1. **The number is sacred.** Tabular figures, provenance-chipped, never
   judged. A derived number without its chip is a spec bug (FEATURES §2.1).
2. **Density is a feature.** Whitespace is spent deliberately: tight stat
   rows, generous only at the hero and the moment of decision.
3. **The moment is crafted.** Capture, weigh-in, check-in: sub-10-second
   rituals, one signature motion each, kind haptics.
4. **Kind, not soft.** Zero guilt/shame copy; deltas described, never judged;
   errors are data (a 2-frame shake, never red).
5. **Every exit lands somewhere.** Every assist lands on manual (R-U15),
   every offline state is a non-state (F10), every weak number is `held`
   with a reason, never a guess.
6. **Discretion by default where it matters.** R-U7 naming, FLAG_SECURE
   surfaces, silent capture in bathroom contexts, redacted lock-screen.

---

## 1. Theming & color

**Dark-first, fixed semantic palette.** The weigh-in happens at 6 a.m. in a
dark bathroom; dark is the canonical theme, light is the derived one. Dynamic
color (Material You) is **opt-in and surfaces-only** — semantic and data-viz
roles are never wallpaper-sourced (R-D1; rationale in research
synthesis §3/C1, §2.2).

The release app still explicitly selects the dark scheme in `MainActivity`.
The light scheme and previews are maintained and contrast-tested so a future
theme policy can enable them; this foundation does not add a preference,
follow the system, or enable dynamic color.

### 1.1 Neutral core (dark values; light theme inverts tones, keeps roles)

| Token | Dark | Role |
|---|---|---|
| `bg` | `#0E1116` | App background |
| `surface` | `#151A21` | Cards |
| `surface-raised` | `#1B222B` | Sheets, elevated cards, viewfinder chrome |
| `surface-sunken` | `#0A0D11` | Wells: charts, number pads, code/receipt blocks |
| `outline` | `#84909D` | Essential component boundaries (≥ 3:1) |
| `outline-variant` | `#46515E` | Decorative hairlines and dividers |
| `text-primary` | `#E8ECF1` | Body & numerals (≥ 13:1 on `bg`) |
| `text-secondary` | `#9AA6B5` | Labels, axis text (≥ 4.5:1) |
| `text-tertiary` | `#808C9A` | Secondary receipts and faint gaps (R-U13 dots); 4.68:1 minimum on dark surfaces |

Light theme: `bg #F7F8FA`, `surface #FFFFFF`, `surface-sunken #EEF1F4`,
`text-primary #171C23`, `text-secondary #46525F`, `text-tertiary #4D5966`,
and `outline #66717D`. Light semantic foregrounds are deliberately darker:
`held #725500`, `developing #365F7D`, `info #1E5F8A`. Both themes: normal
text ≥ 4.5:1, large text ≥ 3:1, and essential component boundaries ≥ 3:1.
Tests enumerate effective role pairs, including the selected-chip alpha
composite. Decorative `outline-variant` dividers are not treated as controls.

### 1.2 Semantic states — no moral valence

| Token | Dark | Meaning | Never |
|---|---|---|---|
| `accent` (WLO teal) | `#3DD6A5` | Primary actions, trend-down, "on track", confidence ≥ 0.7, fiber ring fill | — |
| `accent-dim` | `#1F6B54` | Accent fills at 30–40% tonal weight (rings, bands) | — |
| `neutral-delta` | `#8A93A6` | Informational weight direction; goal mode determines meaning, never moral color | alarm, red |
| `held` (amber) | `#E8B34B` | `held` data states, rough-guess strip, provider errors | pulsing more than once |
| `developing` | `#7FA6C9` | `developing`/`updating` states, ESTIMATED chip, formula-estimate notes | — |
| `info` | `#6FB7FF` | Purely informational callouts, offline glyph | urgency |
| `ideal-halo` | `#3DD6A5` @ 18% | Bristol 3–4 zone halo, target bands — education by placement | — |

**Red does not exist in WLO's default theme.** There is no red token to
reach for; `held` (amber) is the strongest state color. Urgency is
communicated by position, order, and copy — never hue (F07: "red is reserved
for nothing"). Confidence colors are the one spec-frozen triad: green
≥ 0.7 = `accent`, amber 0.4–0.7 = `held`, grey < 0.4 = `text-secondary`
(F02 §4).

User-selectable delta accents (F06's Happy Scale clause; resolution of
collision C1, R-D5): a curated, CVD-safe picker of accent pairs
(Okabe-Ito derived: teal/bluish-green, blue, orange, purple); **red is never
offered as a WLO default and never used by WLO's own urgency/anomaly
rendering**. Default pair: `accent` / `neutral-delta`.

### 1.3 Data-viz series (fixed, never dynamic-colored)

Categorical series from Okabe-Ito (color-blind safe): `series-1 #56B4E9`,
`series-2 #E69F00`, `series-3 #CC79A7`, `series-4 #009E73`, `series-5
#999999`. Sequential scales (heatmaps, cone fills): viridis/magma-family
ramps, 4–5 lightness steps, monotonic lightness so they survive grayscale.
Forecast cone: center line `text-primary`, bands `accent-dim` stepping
opacity outward (darkest at center — fan-chart grammar). Chart chrome is
`text-tertiary`; every chart element ≥ 3:1 against its surface.

### 1.4 Elevation & shape

Dark elevation is expressed as surface tint steps (`bg → surface →
surface-raised`) plus 1 dp `outline` hairlines — **no drop shadows in dark
theme** (they read as dirt at low luminance); light theme may use soft
shadows. Radii: cards 20 dp, static status stamps 8 dp, sheets 28 dp top,
full-round for pills and the shutter FAB. Buttons, interactive chips, dialogs,
and other controls use their native Material 3 shape roles. Cards are bordered
(`outline` hairline) rather than shadowed in dark mode.

---

## 2. Typography

**Single family: Inter (OFL-1.1, GPLv3-compatible per FSF).** One variable
file; `opsz` axis separates text sizes from display numerals. Proposed as
R-D3. No second typeface — hierarchy comes from size, weight, and tabular
width, not from font changes.

**Numbers are the hero — the OpenType contract:**
- `tnum` (tabular figures) is **mandatory wherever numbers change or
  align**: hero numerals, odometers, stat rows, delta chips, receipt lines,
  chart axes, logbook tables. Proportional figures in running prose; the
  F06 page-overview hero is the narrow WLO-0114/R-D3 exception for matching
  the approved mockup (regular Inter Medium, proportional lining figures).
- `lnum` always (no oldstyle figures in stats contexts); `zero` (slashed
  zero) in weight/log contexts where 0/O confusion matters; `frac` enabled
  in recipe/serving contexts (F03/F04).

| Style | Spec | Use |
|---|---|---|
| `hero` | Inter, opsz display, 56–64 sp, wght 600–650, `tnum` | One per screen max: F06/F10 trend hero, check-in TDEE, milestone moment |
| `stat-l` | 28 sp, wght 600, `tnum` | Card-level stat (budget ring center, measured TDEE) |
| `stat-m` | 20 sp, wght 550, `tnum` | Row-level stat (trend rows, deltas) |
| `stat-s` / `receipt` | 13–15 sp, wght 450–500, `tnum` | Dense stat lines ("785 cal · 52g P · 96g C · 21g F"), table cells |
| `title` | 16–18 sp, wght 600 | Card titles, nav labels |
| `title-l` | 24 sp, wght 600 | Screen, sheet and dialog hierarchy |
| `body` | 14–15 sp, wght 400 | Prose, explainers |
| `label` | 11–12 sp, wght 500, +2% tracking, sentence case | Chips, axis labels, provenance chips |

Provenance chips render in `label`, lowercase, inside a 1 dp hairline pill
(`developing`/`held` tint when state-bearing). Delta chips render `stat-m`
sign-forward ("−0.6", "+50"), colored per §1.2, never suffixed with verdict
words.

---

## 3. Spacing, density & touch

4 dp base grid; M3's numbered density scale is applied deliberately — M3's
defaults are roomier than WLO's density bar allows (documented deviation,
research §2.1):

| Token | Value | Use |
|---|---|---|
| `gap-screen` | 16 dp | Screen margins, between-card spacing |
| `gap-card` | 8 dp | Intra-card stack spacing (dense by default) |
| `gap-tight` | 4 dp | Stat row pairs, chip clusters |
| `pad-card` | 14–16 dp | Card padding |
| `row-min` | 40 dp | Display-only dense list rows (density −2) |
| `row-interactive` | **48 dp** | Interactive list and preference rows |
| `touch-primary` | **48 dp** | All primary daily-use targets: shutter, weigh-in confirm, Apply/Keep, check-off, Bristol confirm, quick-action rail |

Floors: never below 24 dp (WCAG 2.2 §2.5.8 AA); flow-critical controls in
bathroom (F06/F09) and gym (F05) and in-aisle (F04) contexts stay at 48 dp —
sweaty/wet/one-handed contexts never get density concessions. One-handed
reach: primary actions live in the bottom third; destructive/config actions
top-right or behind long-press. RTL: layouts mirror; charts do **not**
mirror time axes; tabular numerals stay LTR (Unicode bidi isolation on
number runs). Locale: aisle taxonomy, units (kg/lb, cm/in, kcal/kJ), date formats
  and week-start day are settings (F04 aisles editable wholesale). Release 1
  body mass supports kg/lb as one app-wide sticky choice; per-profile unit
  preferences arrive with multi-profile.

---

## 4. Motion — system tokens

Two easing curves cover everything: **enter/settle = EmphasizedDecelerate
(0.05, 0.7, 0.1, 1.0)**; **exit/dismiss = EmphasizedAccelerate (0.3, 0,
0.8, 0.15)**. Springs: `soft` (damping 0.8, stiffness 380) for count-ups,
ring fills, card entrances; `snap` (damping 1.0, stiffness 800+) for
detents, check-offs, confirm morphs. Durations snap to the M3 scale.
Reduced motion (animator scale 0 / "remove animations"): every animation
collapses to an instant settle or 100 ms crossfade; **haptics are unaffected
and become the primary feedback channel**; TalkBack announces final values
only (research §2.3).

### 4.1 Consolidated motion table (mined from all 13 specs)

Weight trend exploration follows this explicit interaction policy (WLO-0098).
Native Material ripples and sheet motion inherit the system animator scale; the
chart itself has no entrance animation and never interpolates numeric values.

| Trigger | Visual transition | Duration policy | Haptic | Reduced motion |
|---|---|---|---|---|
| Chart sample selection | Move the selection ring only | Immediate in v1; 150–200 ms maximum if tokenized later | None while exploring | Final position immediately |
| Weight period/section change | Replace content without moving the numeric hero | Native state change; 150–200 ms maximum | None | Replace immediately |
| Milestone disclosure | Bounded size/content reveal without focus or scroll jump | 150–200 ms | Optional preference-gated confirmation | Reveal immediately |
| Capture/save | Native sheet transition; values never count up | Material default | One after committed write (WLO-0089) | Final saved state immediately |
| Delete/Undo | Standard row placement; recovery timer is independent | Material default | Preference-gated action feedback | Placement changes immediately |

Every value below is verbatim from a spec; the token column maps it onto the
scale. Where a spec gave a range or no value, the token is the design
decision. († = easing/spring unspecified in spec — token supplies it.)

| Moment | Spec | Motion (spec values) | Haptic (see §5) | Token |
|---|---|---|---|---|
| Forecast bands bloom (onboarding/Studio) | F01 | outward bloom 600 ms, optimistic→pessimistic order; middle-band slow shimmer | — | XL1 decel † |
| `ESTIMATED` chip types itself | F01 | type-in + caret blink | — | M2 † |
| Template card press | F01 | spring scale 0.94× + selection ripple | — | spring(snap) |
| Template surface icons re-arrange | F01 | icon reshuffle on card face | — | M2 † |
| Quiz swipe-left (reject) | F01 | fling + double-tick | double-tick | S4 accel |
| Quiz swipe-right (accept) | F01 | single snap landing | single tick | S4 decel |
| Quiz pile counter | F01 | rolling odometer | — | odometer roll |
| Schedule bar drag | F01 | sibling bars compensate in real time; weekly total pinned | — | continuous † |
| Milestone rungs drop | F01 | 80 ms stagger; three-note pentatonic (< 0.5 s total) | rising motif | spring(soft), staggered |
| Fresh Start page-turn | F01 | page-turn shade dims history in place | — | L2 † |
| Import receipt tally | F01 | count-up per category; unmapped rows slide to side tray | — | count-up |
| Shutter tap | F02 | — | 10 ms tick | tick |
| Viewfinder segmentation | F02 | non-food desaturates briefly; depth mesh shimmers on | — | M2 † |
| Analysis shimmer ("AI deals the foods") | F02 | 600–900 ms shimmer; chips spring in one by one (damping 0.7), soft tick per landing | soft tick ×n | 900 ms + spring(soft, d≈0.7) |
| Confidence ring draw | F02 | clockwise 400 ms; stroke irregular at low confidence | — | M3 decel |
| Portion slider detents | F02 | every 5 g, 5 ms ticks | frequent ticks | detent tick |
| Clamp-rail crossing | F02 | double-sided bump; number flashes rail glyph | bump | snap + bump |
| Hint re-estimate | F02 | numbers morph 250 ms digit-roll; changed items re-spring | — | M2 odometer |
| Save arc | F02 | card folds to 3 px chip, arcs into diary; budget ring sweeps + count-up | one low "settled" | L2 + settle |
| Error (any F02 failure) | F02 | 2-frame 8 px horizontal shake, **no red** | — | shake 80 ms † |
| Clamp tooltip (once per food class) | F02 | 900 ms tooltip, never repeats | — | L1 |
| Generate deal-in | F03 | cards deal into grid 40 ms stagger, soft tick per card | soft tick ×n | staggered spring |
| Fit badges flip | F03 | split-flap to values | — | M2 split-flap † |
| Swap | F03 | old slides out left, new slides in; delta chips fly to day ring; ring re-fills | — | M3 + spring |
| "Ate this" | F03 | tap 1 arms (ring traces check), tap 2 commits | double-tick | two-beat + springy tick |
| Adherence strip extends | F03 | zipper motion | — | zipper † |
| Drag-to-replan | F03 | lift shadow; 8 notch points crossing slots; settle bounce on land | notch ×8 + settle | drag detents |
| Leftover connector | F03 | stitched line draws in one stroke; tap pans calendar | — | draw † |
| Variety heatmap long-press | F03 | repeated group lifts, rubber-band stretch | — | spring |
| Recipe import | F03 | ingredients slide in; nutrition counts up; AI-drafted badge stamps with soft thud | soft thud | count-up + stamp |
| Menu apply | F03 | deal-in at 25 ms stagger; connector lines one stroke | — | faster deal † |
| Generation faders | F03 | live re-run ≤ 100 ms debounced; only changed slots re-deal | fine detents | S2 continuous |
| Serving dial | F03 | plate icons multiply 60 ms stagger; list-impact preview line | detent | staggered |
| Check-off (list row) | F04 | strikethrough draws L→R 150 ms; row sinks with settle bounce | single soft tick | S3 + spring(snap) |
| Aisle completes | F04 | header collapses, zipper motion | deeper tick | zipper |
| List completion | F04 | checked section pulses once in sequence, 300 ms — "confetti-free wave" | — | 300 ms wave |
| Sweep to pantry | F04 | items pour into pantry tab (shared element); count odometer rolls up | — | shared element |
| Expiry dots | F04 | breathe, 2 s period, only in "use soon" band | — | ambient loop |
| Reconciliation delta chip | F04 | 200 ms scale-in + faint outline pulse | — | M1 pop |
| Aisle drag-teach | F04 | notch haptics between groups; confirm double-tick on drop; one-time toast | notch ×n + double-tick | drag detents |
| Receipt OCR reveal | F04 | line-by-line snap-in, micro-tick per resolved row | micro-tick ×n | staggered |
| Set complete | F05 | checkbox morphs to check, spring squash; row dims + contracts 4 dp | single sharp tick (CLOCK_TICK-class) | spring(snap) |
| Rest timer | F05 | ring drains; T−3 s soft tick per second; zero = triple-pulse + gentle chime; ±15 s ripple + numeral micro-scroll | ticks → triple-pulse | S4 ×n |
| PR banner | F05 | slides up from set row, 400 ms | deep single impact | M3 + impact |
| Plate calculator | F05 | plates drop onto bar glyph, heaviest first, soft clicks | soft clicks | staggered drop |
| Session finish | F05 | tonnage counts up 800 ms; muscle glyphs pop in sequence | completion | count-up |
| Superset queue | F05 | queue walks A → B → A | — | M2 † |
| Weigh-in confirmation | F06 | card animates in; trend arrow draws downward with spring settle; gain = identical motion, neutral color | single soft tick (both cases) | M3 + spring |
| Odometer (hero/trend) | F06 | rolls digit-by-digit, 400 ms | — | odometer 400 ms |
| Back-fill recompute | F06 | zero-phase recent values re-settle, visible 300 ms ease | — | M2 |
| Milestone moment | F06 | giant numeral count-up | two-note celebration | count-up |
| Check-in reveal | F07 | 500 ms sequence: status chip stamps → trend odometer-rolls → expenditure line draws L→R → proposal row rises | — | choreographed 500 ms |
| Apply commit | F07 | crisp tick-tick; numerals count old → new; thin ledger line items itself beneath | double-tick | commit |
| Keep | F07 | proposal row folds away ("rolling over") | single soft tick | fold |
| Held state | F07 | amber pulse **once**, 600 ms; estimate sits at reduced opacity | — | S4 ×1 |
| Forecast cone first render | F07 | expands outward from trend line, 700 ms ease-out; "the widening is itself displayed" | — | XL1 |
| Band/date switching | F07 | each date highlights with a tick | tick | detent |
| Plateau moment | F07 | Adjustment term glows once and ticks upward ("quiet applause") | — | glow † |
| Skeleton alignment | F08 | 12 segments tint red→amber→green, 180 ms spring per segment; soft tick per green snap; all-green = double-pulse + shutter blooms | ticks ×n → ready double-pulse | spring ×12 |
| Countdown (F08/F09 photo) | F08/F09 | 3·2·1 expanding rings, 400 ms each; F09 auto-timer 3 s total | — | 3 × M3 |
| Shutter (F08) | F08 | **silent always** | — | none (discretion) |
| Unlock "develop" | F08/F13 | thumbnails resolve blurred→sharp, 250 ms L→R stagger | — | staggered fade |
| Compare wipe | F08 | drag divider, magnetic snap to centerline; synchronized two-pane scrub, one detent per capture pair | detent per pair | drag detents |
| Delta chips (silhouette) | F08 | count up 400 ms ease-out; "within noise" renders grey + chip instead of a number | — | odometer |
| Quiet-scale card | F08 | slow radial pulse behind "−2.3 cm in 6 weeks"; no confetti | — | ambient pulse |
| Bristol carousel | F09 | center card scales 1.15 per snap | detent per snap | spring(snap) |
| Bristol confirm | F09 | button morphs to check; sheet collapses 220 ms; **no sound by default** | soft settle | S3 + morph |
| Classifier chip | F09 | slides in ("Type 4 · 82 %"); tap fans out all 7 types | — | slide † |
| Correction toast | F09 | "Noted — tunes your on-device model", 1.2 s | — | toast |
| Gut heatmap cells | F09 | ink-bloom, 120 ms stagger | — | staggered fade |
| Fiber ring | F09 | spring overshoot fill as meals land; under-target slow breathe (invitation) | — | spring(soft) |
| Hero count-up | F10 | spring count-up ~600 ms on change | — | odometer soft |
| Delta chip pop | F10 | 150 ms scale pop, crossfade to delta color (never alarm-red) | — | S3 |
| Budget ring sweep | F10 | 400 ms ease-out per log; soft tick at sweep end; in-band evening landing = double micro-bounce + success haptic | tick / success | M3 |
| Quick-action rail | F10 | 32 ms press-scale + tick per icon | tick | S1 |
| Card collapse | F10 | swipe-up folds to one-line chip; undo snackbar 5 s | — | fold |
| Notification dismiss | F10 | animated collapse, never a vanish | — | collapse † |
| Grade stamps | F11 | letterpress press-in 250 ms per domain card, cards flip in sequence | tick, heavier for an A | stamp ×n |
| Stat hero count-up | F11 | 500–700 ms spring | — | odometer |
| Chart scrub | F11 | magnetic snap to data points | per-point tick | detent |
| Correlation scatter | F11 | points land staggered 20 ms, soft pops | soft pops | staggered |
| PR banner (insights) | F11 | slides in, particle shimmer — deliberately **not confetti** | impact | M3 |
| Share render | F11 | ~200 ms card render | — | S4 |
| Consent toggle | F12 | shield visibly closes, 250 ms spring | firm double-tick | spring + double-tick |
| Connection test | F12 | sonar pulse ring; round-trip ms counts up | — | pulse loop |
| Model download | F12 | segmented progress ring + MB counter; completion "click" | click | progress |
| Receipt filing | F12 | ledger lines slide device→cloud, rolodex 180 ms each | subtle tick ×n | staggered |
| Kill switch | F12 | all six status glyphs flip to "local", staggered 60 ms wave | firm confirm | wave |
| Backup success | F13 | vault glyph morphs to check, 800 ms, **silent** (no toast spam) | — | morph |
| Restore validation | F13 | staged progress, per-stage ticks, "validated" stamp | stage ticks | staged |
| Biometric unlock | F13 | fast 200 ms fade, no splash theater | system | S4 |
| Export materialize | F13 | file icon springs in, offers share sheet | — | spring |
| Scale pairing | F13 | live capture, haptic tick per arriving metric | tick ×n | per-event |

**Ambient loops** (the only permitted infinite animations, all slow and
low-contrast, all suppressed under reduced motion): expiry-dot breathe (2 s),
fiber-ring under-target breathe, quiet-scale radial pulse, ESTIMATED-band
shimmer, depth-mesh shimmer. Everything else is finite. **Celebration
budget:** confetti does not exist in WLO; celebration = geometry (rings,
ribbons, count-ups, stamps) + haptics. F11 owns celebrations; logging flows
never celebrate (F02 rule).

---

## 5. Haptics — the "kind haptics" vocabulary

**Rule (R-D6):** data outcomes always feel the same regardless of
whether the news is good — Confirm-class, short and light. `Reject` weight is
reserved for blocking errors (invalid BYOK key, corrupt import, the F01
calorie-floor wall) — never for a data result. Haptics bypass reduced-motion
and are the fallback feedback channel when animation is off.

| Spec intent (verbatim words) | Maps to (Compose / platform) | Used by |
|---|---|---|
| "tick", "soft tick", "micro-tick" | `Confirm` (short) — falls back `CLOCK_TICK` | F02 chip land; F03 deal-in; F04 check-off; F10 rail; F11 stamps |
| "single sharp tick (CLOCK_TICK-class)" | view-level `CLOCK_TICK` / `SegmentTick` | F05 set complete |
| "double-tick", "crisp tick-tick" | `Confirm` ×2 (double-click pattern) | F01 quiz reject; F03 ate-this; F04 aisle drop; F07 Apply; F12 shield close |
| "detent", "notch", "per-point tick", "one haptic per pair" | `SegmentTick` (sparse) / `SegmentFrequentTick` (dense: sliders, scrub) | F02 portion slider; F03 drag; F07 band switch; F08 scrub; F09 carousel; F11 scrub |
| "triple-pulse + gentle chime" | composition: `PRIMITIVE_TICK` ×3 (+ optional short tone; F05 gym context only — sound stays off in bathroom contexts per F09) | F05 rest zero |
| "deep single impact" | `PRIMITIVE_THUD` / `EFFECT_HEAVY_CLICK` | F05/F11 PR banner |
| "thock" (dial snap), "soft thud" (badge stamp) | `PRIMITIVE_LOW_TICK` / `PRIMITIVE_THUD` @ low scale | F01 dials; F03 import stamp |
| "one low 'settled'", "soft settle" | `GestureEnd` / `PRIMITIVE_QUICK_FALL` | F02 save; F09 confirm; F03 drag land |
| "haptic wall" (calorie floor) | `Reject` — the one sanctionedReject-for-a-setting | F01 pace slider |
| "two-note celebration", rising motif | composition: `PRIMITIVE_QUICK_RISE` ×2 | F01 milestones; F06 milestone moment |
| "heavier for an A" (grades) | `Confirm` at scale 1.0 vs 0.7 (primitive `scale`) | F11 stamps |
| "ready double-pulse" | `Confirm` ×2 @ low scale | F08 all-green |
| "tick per arriving metric" | `SegmentFrequentTick` | F13 scale pairing |
| firm confirm (kill switch) | `Confirm` @ full scale | F12 |

All fallbacks below API 30/34 degrade to `LongPress`/`VirtualKey` (Compose
gates internally; no-ops when unsupported). No haptic on F08 shutter, F09
flows' ambient state, or notification arrival (silent channel, one vibration
pulse max per the Nudge Contract).

---

## 6. Charts & data objects

Adopted split (R-D4; rationale research §2.7): **Vico** for
standard line/bar (energy-balance combo, stats-hub charts, sparkline hosts);
**custom Canvas** for the four signature objects below (no library models
them honestly); one shared month-heatmap implementation (kizitonwose
Calendar or ~150-line grid) reused by F05/F09/F11.

Signature chart objects: **forecast cone** (fan chart: center + 3 bands,
decelerating, widening displayed, 700 ms bloom), trend chart
(area between trend and N-days-ago line, breathe on new data), **budget
ring** (arc + count-up + provenance chip center), **odometer numerals**
(400 ms hero roll, tabular digits so nothing jitters). Chart rules: every
element tappable → "how we got here" (P31); contentDescriptions state
purpose+result; sparklines carry min/max marks, no axes; heatmaps paginate
by month; bands render as geometry, never as red/green valence.

---

## 7. Component inventory (swept against all 13 specs)

Every named UI element in every spec. Columns: component · owner · states /
variants · tag · binding UI rules (provenance / tone / discretion). Shared
atoms first, then per-spec residents. (Tag = version scope; untagged specs
items are treated [v1].)

### 7.1 Shared atoms

| Component | Owner | States / variants | Tag | Binding rules |
|---|---|---|---|---|
| Provenance chip | FEATURES §2.1, R-D11 | Anatomy: **user-word + ⓘ** — `measured` / `derived ⓘ` / `estimated ⓘ` / `DB-verified ⓘ (source on tap)` / `AI-estimated ⓘ` / `user-entered` / `unknown` / `provisional`. **Inline form (R-D12, round 8):** when a card header stays clean, the ⓘ binds directly to the number or word it describes — `of 1,900 kcal ⓘ`, `targets ⓘ` — and card headers never carry number provenance. Parameters (smoother + α, window, model version, source IDs, update cadence + last-change date, constants) live only in the ⓘ tap-through ("how we got here"), never in the label | [v1] | Mandatory on every derived number; tap → "how we got here" sheet; label-style pill, or inline ⓘ on the number where a pill would crowd the card |
| "How we got here" sheet | each owner feature | formula + inputs + constants + exclusions + state | [v1] | First-class atom; formula version shown; receipts shortcut (F12) |
| Data-quality status chip | F07 (pattern), generalized | `developing` / `updating` / `held` (+ reason) | [v1] | Amber + single 600 ms pulse when held; reason chip names the fix, one tap |
| Day-status marker | F02 | `Logged` / `Skipped` / `Fasted` | [v1] | One tap in day header; inline fix offer on F07 adherence row; never a gap-shame |
| Delta chip | F03/F04/F06/F07/F08/F10 | sign-forward, count-up/pop; "within noise" grey variant | [v1] | Neutral or accent hues, never red; describes, never judges |
| Confidence ring | F02 (F09 chip variant) | green ≥ 0.7 / amber 0.4–0.7 / grey < 0.4; irregular stroke at low conf. | [v1] | Frozen color boundaries; "rough guess — adjust what's wrong" amber strip < 0.5 |
| Odometer numerals | F06 (canonical) | hero/stat sizes | [v1] | 400 ms; tabular digits; roll once per change |
| Consent sheet (point-of-use) | F12 | "just this once" (10 min) / "always for <category>" / "keep it on-device" (default-highlighted) / "never ask" | [v1] | Exact payload preview with byte count + cost; max once/feature/day; never a Settings redirect |
| Month heatmap | F05/F09/F11 | regularity / volume / grade tints; ink-bloom stagger | [v1] | 4–5 sequential steps; month-paginated; press-and-hold private summary (F09, auto-redacted) |
| Sparkline | F04/F06/F08/F10/F11 | line + min/max marks, band variants | [v1] | Axis-free; behind delta numbers |
| Share-card renderer | F11 (shared by F05/F06/F08/F09/F10) | per-metric templates; privacy-state persisted | [v1] | Fixed aspect, oversized numeral, wordmark; silhouette = faceless vector strip only (R-U16); poop content opt-in per share |
| Biometric lock gate | F13 | locked / unlocked; deep links land on gate, never content | [v1] | Required for Archive entry; PIN fallback; 200 ms fade |
| Streak chip | F11 (displayed F10) | multi-oracle; `held` days neutral | [v1] | Ticks on log; no streak-loss notifications ever |
| Empty state (teaching) | each feature | teaches next action; optional demo-data entry [v1.x] | [v1] | Teaches the next action (F02/F06 rule); no blank failures |
| Swipe-to-reveal trigger row | F06 (WLO-0050; custom `anchoredDrag` per the official swipe-to-dismiss guidance's custom-behavior path) | settled / dragging (action revealed in the vacated space; armed past the trigger) / released | [v1] | The item slides as one opaque piece in the card's own color — never an overlap, never a color fill; the action is an error-tinted glyph + label at the trailing edge. The action fires ONLY on release with the drag held past the trigger distance (default 90 % of the reveal width) — velocity is ignored, so a flick never acts. Crossing the trigger arms the affordance before the lift: the glyph/label morph muted → error and a single tick haptic marks it; short releases spring back. The undo renders inline where the row was as the fact + one Undo action with a visible 2 dp countdown hairline (≈4.5 s) — never a dialog, never a page-level banner, no Dismiss button; when the hairline empties the notice slides away (shrink + fade). TalkBack gets a custom row action |

### 7.2 F01 — Onboarding & Plan Studio

Welcome & privacy card · Goal dials (rotary, kg ticks, 5 kg "thock" snap, delta count-up) · Pace slider (haptic wall at floor) · Counter-offer card ("fastest we'll suggest… isn't a promise we'll make") · 3-band forecast preview (600 ms bloom, ESTIMATED chip type-in, middle-band shimmer) · Template gallery cards (7 seeds; 0.94× press; accent ripple; surface-icon reshuffle) · Ketosis education card · Mediterranean fish hint · Carb-limit ring · Protein-floor ring · Macro-split preset sliders · Preference quiz swipe deck (8-card core R-S6; "why we ask" long-press) · Pile-counter odometer · Household-size / cooking-frequency / budget chips · Activity picture-card · Schedule bars (7-bar chart, pinned weekly total) · Milestone ladder (4–8 rungs, 80 ms stagger, pentatonic motif, projected date *ranges*) · Date wheel · Page-turn confirmation (Fresh Start) · Fresh Start footnote ("12 months preserved") · Streak-freeze note ("badges kept; streak starts clean" R-B7) · Diff summary card · Diff ribbon · Version badge · Plan version history (human-readable diffs, one-tap revert) · Targets card · Forecast chart embed (F07-owned) · Import report / "migration receipt" (count-up, unmapped tray, `unknown` placeholders) · Formula-estimate note · Surface toggles · Rules chips · Macro sliders · IF timing windows · Signed-template share [v1.x] · Diet DNA overlay [v1.x] · Quiz re-run summary [v1.x] · Onboarding replay [v1.x] · Point-of-use consent sheet embed [v1.x] · "Refine with AI" [v1.x] · Plan simulator sandbox [moonshot] · Multi-phase plans [future] · Household harmonization [future].

### 7.3 F02 — Food Logging

Shutter FAB (long-press = re-log last meal; long-press-again = kcal-only quick add on Hub) · Viewfinder + segmentation mask (desaturate non-food, depth mesh) · Result card (~1 s; items, macros, confidence ring; Save live) · Item chips (swap / portion slider / "not this"; two least-confident pre-expanded on low scans) · Portion slider (5 g detents; clamp-rail bump) · Hint field (no word limit; 250 ms re-estimate) · Confidence ring · Amber rough-guess strip (< 0.5) · Offline glyph · Sanity-rail marker (rails named; confidence downgrade) · Clamp tooltip (900 ms, once per class) · Food Memory cards ("same as Tuesday?") · Food diary (meal groups, thumbs, badges, chips) · Day-status marker · Water quick-add · Budget ring (F10-owned surface) · Micronutrient panels [v1.x] (hidden for unverified AI items) · Personal accuracy stat ("±14%, best on breakfast") · F02 visualizations (day/week timeline, per-meal macro bars, weekly logged-meals heatmap, seconds-to-log distribution, corrections-per-food) · Keto carb cap display · Barcode/label capture (OFF+USDA, cache) · Screenshot import [future] · Menu mode [future] · Hands-free cook mode [future] · Spoken-weight calibration [v1.x] · Leftover diff [v1.x] · Personalization-loop stat [v1.x] · Food X-ray [moonshot] · Calibrator badge line (proposed to F11).

### 7.4 F03 — Meal Planning

Planner tab · Week view 7-day grid · Month calendar (read-only v1 R-U11; variety heatmap tinting) · Meal cards (plate icons scale with servings; drag-to-replan) · Fit badges ("kcal ✓ · P −6 g"; ±5 % R-S7; never pass/fail) · Day nutrition rings · Swap suggestions (top-3) · "Ate this" two-beat control · Adherence strip (7-segment zipper; "not yet meaningful" < 3 days) · "Build list" action · "Save as menu" + name chips · "Add anything" card (reason, never blank) · "Why this plan" panel · Variety heatmap (long-press diversify) · Planned-vs-actual deltas (paired bars/scatter) · Leftover connector (stitched tupperware line; shelf-life flag not block) · Serving dial (0.5 steps, arbitrary ×) · Generation settings faders (mixing-desk, ≤ 100 ms live) · Recipe library (versioned; AI-drafted visually distinct) · "Library fit" gauge · AI-drafted provenance badge [v1.x drafts; semantics v1] · Allergen flag (top-of-recipe, hard block) · `unknown` marker (suppresses per-serving nutrition) · "Missing: 3 ingredients" chip → F04 · Ingredient-overlap / waste-avoided stats · Rebalance dial [v1.x] · Pantry-aware generation toggle [v1.x] · Voice planning chips [v1.x] · Adherence forensics [v1.x] · Cook mode [future] · Photo-to-recipe [future] · Leftover graph solver [moonshot] · Adherence metrics set (F03 computes, R-B4).

### 7.5 F04 — Shopping & Pantry

Aisle-ordered list (user-order respected forever; headers collapse) · Check-off row (150 ms strikethrough, ghost-undo) · Checked section (completion wave) · "To buy" filter · Reconciliation delta chips ("+2"; left-swipe shows cause) · Reconciliation banner ("Your checks are safe") · Removed-item strikethrough (recoverable) · Sweep-to-pantry banner (poured items, odometer) · Expiry chips (+3 d / +7 d / wheel) · Expiry tinted dots (2 s breathe, "use soon" only) · Pantry inventory (expiry timeline banded; "do we have feta?" search) · Pantry turnover ring · Multi-list switcher (default list never deletable; merged overlap flags) · Consolidate toggle · Recipe-provenance chip ("for: Thai curry ×4") · Aisle chip + "learned" checkmark · "WLO will remember this" toast · Staples auto-deduction (R-S5 prompt) · Barcode/label check-in (timing open) · Receipt-scan confirm screen [v1.x] (line-by-line) · Cost panel [v1.x] (no price shaming) · "Use it or plan it" cards (one-tap resolve) · Waste & reuse stats (self-reported, neutral) · "Buy 2 fewer eggs" suggestion chip · List widget [v1.x] (R-U12; per-aisle counts; pulse-down) · Expiry timeline strip / weekly cost bars / staple sparklines [v1.x] · Run-out projection (states basis or absent) · Shopping-rhythm forecast [v1.x] · Expiry triage board [v1.x] · List export/share ("a format, not a partnership") · Pantry photo stock-take [future] · Household sync [moonshot] · Remnant recipes [moonshot].

### 7.6 F05 — Exercise

"Today's session" card · Set row (prefilled; warm-up lighter style; dims + contracts 4 dp) · Rest timer chip (ring drain; ±15 s; per-movement override) · Plate calculator (above keyboard; plates drop; "Closest possible" honesty chip) · PR banner (toggleable; no confetti) · Session summary (5-second: tonnage count-up, PRs, muscles) · Session "receipt" (share image) · Substitutions sheet (muscle ∩ equipment ranked) · Warm-up ramp calculator · Row badges (failure/drop) · AMRAP prompt (only when differing) · RPE 5-chip row (optional) · Superset/circuit grouping (queue walks A→B→A) · Progression rules (editable sentences; session preview "If you hit 3×8 @ 60 → 62.5") · Auto-adjust confirm [v1.x] · Muscle heatmap (7/30/90 d; volume-weighted) · Recovery body-map [v1.x] (0–100 %, decay curves drawn, constants shown) · e1RM/volume lines + PR timeline (Epley shown; ≥ 3 sets gate) · Logbook table (all history, never capped) · Sets-per-muscle-per-week · Monthly training recap · Plateau dialogue [v1.x] (options as cards, reasoning shown) · "Update bodyweight" (explicit write to F06) · Cardio live session (elapsed-time hero, GPS distance ⓘ, current pace, HR + zone band, route trace, auto-pause) · Activity chips (Walk · Run · Ride · Row) · Manual cardio fallback (3 fields) · Health-Connect-imported cards ("via Health Connect") · Splits / pace curve / zone minutes (formulas ⓘ) · Cardio quick-entry (3 taps, R-S11 as amended) · Notification resurrector · Expenditure context record ("±30 % honestly"; never eat-back) · Hevy/Strong import bridge [v1.x] · Wear OS companion [future] · HR zones [future] · Form check [moonshot] · Fatigue-budget autoregulation [moonshot].

### 7.7 F06 — Weight & Body

**Release 1:** trend confirmation card (`Weight trend`, raw small beneath;
identical neutral motion in either direction) · accessible entry in kg/lb ·
honest raw+trend chart with neutral 30-day change · logbook and import identity · loss/maintenance/gain
goal state · goal editor/progress · Health Connect weight/body-fat import.
**Release 2:**
**Deferred:** smoother
comparison/tuning · body-fat/girth/EAV custom metrics · milestone celebration ·
Bluetooth scale drivers · multi-profile lock · advanced correlations.

### 7.8 F07 — Energy Engine

Release 1 goal engine: loss/maintenance/gain mode parity · quality-state chip ·
cold-start formula ranges · calibrated forecast cone · goal editor/progress ·
safety counter-proposals · local explainers and provenance. Broader nutrition
check-in anatomy, measured-TDEE adjustment, dual-engine overlay, scenario
simulator, diet-break choreography, and engine telemetry follow their owning
tickets and release gates.

### 7.9 F08 — Silhouette ("Archive")

Lock gate (biometric; auto-relock; deep links land on gate) · Capture checklist (static chips) · Live skeleton overlay (12 segments red→amber→green; failing segment names fix: "left ankle out of frame — step back 30 cm") · Shutter (blooms on all-green; **silent always**) · Countdown rings (400 ms ×3; flash suppressed outside gate) · Delta card (5-second post-save vs reference) · Timeline grid (vector-outline thumbs; partial/flagged markers; gaps = faint dots, R-U13; long-press = set as reference) · Compare studio (wipe divider, centerline snap; synchronized scrub; onion-skin; angle annotations) · Delta chips (400 ms count-up; "within noise" grey) · Quiet-scale card ("−2.3 cm in 6 weeks"; slow radial pulse; calm) · "Collecting" state (< 3 captures / < 14 days → never a conclusion) · Capture-due card (generic copy "Weekly check-in is ready · ~30 s") · "Not this week" control (silent reschedule) · Width-profile sparklines · Delta-over-time line (noise band) · Silhouette-vs-tape scatter (trust chart) · Faceless silhouette strip (only shareable artifact) · Comparability engine [v1.x] (names why two captures aren't fair to diff) · Angle coach memory [v1.x] · Estimated circumferences [future] · Journey book [future] · FutureMe slider [moonshot] (conservative clamp, "projection — not a prediction" watermark) · 3D mannequin [moonshot] · Capture-cadence stat ("11 of the last 12 weeks" — coverage, not duty).

### 7.10 F09 — Gut ("Digestion")

Digestion quick tile (renamable; neutral leaf/wave icon) · Bristol sheet + 7-type carousel (watercolor illustrations, no faces/emoji; center 1.15 snap; ideal-zone halo) · Classifier pre-select chip ("Type 4 · 82 %"; fan-out override) · Optional chips (blood, urgency, pain, photo, note — collapsed, remembered) · Confirm button (morph; 220 ms collapse; no sound) · Correction toast (1.2 s) · Calendar heatmap (regularity-tinted; ink-bloom; hold-cell private bubble, auto-redacted) · Fiber ring (spring overshoot; under-target breathe) · Fiber-target dashboard / fiber-gap strip (R-B3 single target) · Early-warning card (sand gradient, weather framing, never red) · Red-flag doctor card (one per episode; only action "show a doctor"; never a push by default) · Correlation cards [v1.x] (effect size, window, n) · Unlock progress ring [v1.x] ("unlocks in 5 days") · Transit timeline [v1.x] (8–48 h; meal→outcome icons) · Plan-transition reassurance card [v1.x] · Doctor PDF/CSV [v1.x] (photos per-export opt-in; password-protectable) · Regularity score (hidden < 14 days; formula linked) · Frequency chart (personal-baseline band; population averages never shown) · Type-over-time line · Follow-up chip on F02 meal cards ("how did yesterday sit?") · Weight-noise annotator [future] (R-U8 default off) · Stool forecast [future] · Transit self-test [moonshot] · Personal FODMAP budget model [moonshot] · Voice notes [v1.x].

### 7.11 F10 — Daily Hub

Hero number card (trend-first; flips post-weigh-in; yields to check-in card on check-in day R-U17; night = minimal) · Delta chip (150 ms pop; never alarm-red) · Calories ring (title "Calories"; macro dots wrap; target wears an inline ⓘ → explainer; "provisional" inline when held; "the ring shows *state*, not *sin*") · Meals card (content-rendered, R-D14 — exists only while an open planned slot exists, silent absence otherwise; "Meals · today / tomorrow"; next-open-meal row + header count; row tap = meal sheet — confirm / ate something else / swap / not having it — behind the happy-path button; resolved meals live only in the diary, R-D13; muted-by-choice) · Quick-action rail (photo/weigh/gut/workout; badge-dots; 32 ms press; long-press camera = kcal-only; re-ranked by Adaptive Day Model) · Weigh-in card (leads mornings until logged) · F07 check-in card slot (promoted to hero on check-in day, R-U17) · "Close the day" / "Plan tomorrow" cards (~19:00; "copy today" preset; Plan-tomorrow is plan-conditional, R-D14) · Streak chip · Report-card-ready pill · Backup-health dot · Collapsed-card chip (swipe-up fold; 5 s undo) · Day-closure recap [v1.x] ("Day closed"; 3 stat chips + share; R-U9 in-app first) · Nudge notification (silent channel; ≤ 2 action buttons; animated collapse; "Why am I seeing this?" + inline tuning; overflow "Pause nudges for a week") · Wear OS glance [future].

### 7.12 F11 — Insights

Weekly report card (6 domains: Energy, Nutrition, Movement, Body Trend, Gut, Capture; flips in sequence) · Grade stamp (letterpress 250 ms; heavier haptic for an A; `held` + reason + "what would unlock it") · "How we graded this" panel (rubric version + inputs + quality score) · Stats hub (any metric × any range ≤ 3 taps; presets 7/30/90/YTD/all; overlays) · Metric picker (incl. EAV) · Insights feed (one-sentence templated findings, provenance-linked, expand in place) · Badge/milestone/PR gallery (dates + underlying numbers; visual direction = owner question) · PR banner (particle shimmer, not confetti) · Streak chip (R-U10 multi-oracle) · Freeze-token UI ("Freeze used — streak intact, 2 left this month"; free forever; "repaired" notes) · Milestone ladder (projections per rung) · Share-card renderer + Share composer (privacy checklist; defaults weight-trend/energy only; per-domain "never include" switch) · Weekday heatmaps · Correlation views [v1.x] (n ≥ 21 gate; "n = 9 — not enough data to claim anything") · Consistency Score tile [v1.x] (dormant < 4 weeks R-U3; disables in held weeks; formula published) · Annual Wrapped [v1.x] (R-U4; chaptered scroll; "your year in Bristol" opt-in) · Log-efficiency flex [v1.x] ("median seconds-to-log") · Report-card notification (R-U2 day).

### 7.13 F12 — AI Platform

AI Studio (Settings → AI; six frozen category rows; status glyphs on-device/cloud/off; amber on provider error) · Global kill switch ("Cloud: OFF — nothing can leave the device"; 60 ms glyph wave; never disables on-device) · Consent row (toggle, model-card link, provider/model pickers, "preview payload", receipts filter) · Consent sheet (point-of-use; payload preview with byte count + "≈ $0.004" band) · Payload preview / dry run ("the real bytes, rendered") · Model card (purpose, benchmark + methodology, license, version, hash, training data R-S12) · Model manager (hash-pinned downloads; storage accounting; one-tap reclaim; version eval deltas) · Connection test (sonar pulse + ms count-up) · Model listing (live, searchable) · Receipt log (hash-chained ledger; summaries only; filterable; monthly summary: "38 KB left your phone this month") · Cost dashboard (per-category actuals; editable price table, community JSON import) · F12 charts (cost bars, egress timeline, consent history) · Accuracy page (in-app + repo) · Live egress monitor (debug builds) · Egress attestation widget [future] · Consent schedules / routing [v1.x] · Community model channel [future].

### 7.14 F13 — Data Vault

Storage dashboard (per-category tiles; shrink/purge/age-based cleanup; R-U14) · Network audit page [v1.x] ("every egress path, consent state, last-used") · Biometric app lock (PIN fallback; timeout) · Backup-health dot (F10) · Restore prompt ("Restore found a backup") · Validation report / staging ("nothing was changed" failure state) · Column-mapping wizard (remembers) · Export flow (versioned JSON; CSV; keys blanked; plaintext explicit R-U5; attachments per-bundle opt-in R-U18) · PDF reports (journey + F09 doctor export, on-device) · Deletion flow (type-to-confirm with counts: "3,412 entries · 187 photos") · User-folder photo offload [v1.x] · Encrypted backups (R-U5) · Health Connect consent UI (per-datatype; visible paused state) · Scale pairing dialog (tick per metric; openScale credits) · Consent ledger · Attachments registry (sensitivity classes) · Hidden gallery / app-switcher protection · EAV custom metrics UI · Fresh Start hand-off · Multi-device sync / signed exports / plain-SQLite mode [future] · Journaled vault [moonshot].

---

## 8. Copy & tone system (consolidated banned lists)

App-wide banned words (union of all spec lists + design bar): **failed,
cheat, missed (as verdict), overdue, abnormal, bad, guilt, "you should",
"off plan", "trouble spots", "welcome back, you gained"** — and any string
scoring the user against the plan must pass the "no verdict" test: deltas
are described, never judged (F01 §9). Per-spec additions: F02 — the app
never comments on food choices ("that's a lot of calories" is banned);
errors carry no red. F03 — no food is labeled "cheat"/"bad"/"guilty"; waste
language forward-looking. F05 — no "you skipped leg day"; recovery framing.
F06 — "over" never applied to a body number; no BMI lectures; no
comparisons to other humans. F07 — the body is never "stubborn"; no "over
budget"; metabolic language descriptive. F08 — language photographic
(compare, reference, develop), never judgmental. F09 — "overdue"/"failed"/
"abnormal" banned ("unusual for you"); population averages never benchmarks.
F10 — nudge copy: invitations, never verdicts ("Lunch is still open —
want to log it?"); no streak-loss notifications. F11 — "failed/
cheat/bad/guilt" enforced on all copy templates; "describe the data, never
the person." F12 — proud, never scaremongering; no consent bundling, no
pre-checked consents, no nag loops.

Mandated voice patterns: dry, numbers-forward stat lines ("3 cook events ·
4 leftovers · €31 est."; receipt style §2); warmth reserved for moments of
change; forecast dates hedged once ("an estimate, not a promise");
privacy statements made once, on the surface itself ("Computed on your
device"), never repeated as nagging.

**Sound:** default silent everywhere; the only sanctioned sound is F05's
gentle rest-timer chime (gym context). F09 explicitly no-sound by default;
F08 shutter silent always.

**System leakage (banned in user copy, R-D11):** ms values and durations
("250 ms digit-roll"), haptic names ("soft tick", "double micro-bounce",
"Confirm ×2"), motion vocabulary as labels ("letterpress", "ink-bloom",
"spring overshoot", "settled"), ruling and feature IDs ("R-U13", "F03
§6", "screens 3–4"), engine/recognizer/model versions ("EWMA α 0.15",
"recognizer v1.4", "engine transparent-v1"), algorithm parameters in
chips, design-doc phrases ("offline is a non-state", "celebration is
geometry"), and state-machine pre-explanations ("leads until logged",
"fallback: provisional when held", "it disables itself during held
weeks"). These belong in the prototype annotations and the ⓘ explainer —
the UI states what is, and offers the next action. Companion rules: at
most one helper line per card, phrased as the next action; no piece of
information may appear twice on one screen (the second occurrence is
removed, not restyled); when a state occurs it names itself
("provisional") — it is never announced in advance.

**Self-congratulation (banned in user copy, R-D15):** the app never
narrates its own performance — no speed claims ("logged in 6 s", "saves
instantly", "under 1 s, nothing to wait for"), no ease counts ("one tap",
"two taps to done", "8 quick swipes"), no pre-emptive capability pitches
("works offline" before the fact). The app proves itself by being useful:
success is shown by the updated state (the ring sweeps, the entry appears),
affordances are stated as instructions ("tap for history", "tap to
reopen"), and a capability is disclosed only as a post-hoc fact at the
moment it happened ("worked offline — nothing left your phone").
Data-practice disclosures at the trust decision (onboarding's "No account,
no server, no ads — every number is computed and kept on this device") are
facts, not boasts, and stay. Timing and effort numbers live in the
annotations for the design reader, never on the screen.

---

## 9. Prototypes

Phase C flows live in [`flows/`](flows/) — each flow ships wireframe →
annotated mock → self-review against this document and its spec. The shared
phone-frame CSS (`flows/_wlo.css`) implements §1–§5 tokens as CSS variables
so mocks and code diverge only in medium, not in values.

## Weight overview amendment — WLO-0104

Owner-approved page composition supersedes the prior card/tuner overview: current trend, selected-period change and goal, range selector, raw-weight dots plus trend, and a history navigation row. Goal line is always included in the same linear axis domain. `chartGoal` is a dedicated semantic role: dark #F4D35E, light #756000; dashed line and direct label distinguish it without color alone. It is not a held/warning state. Point inspection is a compact date/weight tooltip, with equivalent keyboard/accessibility navigation. Settings owns smoothing/alpha and rare math help; preview has visible Reset. M3 bottom action reserves space above navigation. No fixed30-day summary independent of the selected range. Full design contract: mockups/WLO-0103/README.md.

### Weight overview typography and geometry (WLO-0114)

The original Inter mockup is the visual reference, evaluated at equal screen width with matching data and visible-glyph anchors. This supersedes the WLO-0108 size reductions, which compensated for screenshot display scaling.

`WeightOverviewTypography`: hero 56/66.08sp medium, −2sp tracking; unit 23/28sp regular; eyebrow 14/20.3sp; full date 13/18.85sp; change 24/34.8sp medium, −0.5sp tracking; goal 17/24.65sp medium; supporting 12/17.4sp; history 15/21.75sp; range labels 14/20.3sp (selected semibold, otherwise regular); action 15/21.75sp semibold. The hero uses proportional lining figures under the narrow R-D3 exception; change and aligned numeric rows retain tabular figures. Explicit line-height trim=None retains intended leading instead of silently trimming single-line boxes. All roles scale with system text size.

The overview uses 24dp content gutters, 5dp hero gaps, 23dp before the summary, 18dp before the divider and 17dp before the native segmented row. Change and goal groups are vertically centered, with a standard M3 text button for the goal. The native selector has a 44dp visible height, preserves its minimum touch target and selected semantics, and omits the checkmark. The action uses a plus character at label size.

Chart geometry follows the reference 360×285 SVG aspect within its extended 372-unit plot container: height = contentWidth × 285/360 × 372/364. Plot top/bottom are 22/285 and 232/285 of height; label row is at 257/285; right plot edge reserves 12.5% plus enough space for scaled axis text. The domain remains data-driven and includes the goal with padding; it is not hardcoded to demo weights. Short windows have three date ticks; quarter windows have calendar-month ticks; larger windows retain year-aware ticks. The latest trend has a filled marker. Sparse windows say when readings start.

Preserve Android system bars: the HTML reference has no Android system navigation bar, so its footer cannot be matched physically by drawing under the OS controls. Main content scrolls and the action stays reachable. Other screens retain their typography roles.
