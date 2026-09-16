# F11 — Insights, Statistics & Gamification — Functional Specification

## Identity

| | |
|---|---|
| **Feature ID** | F11 — Insights, Statistics & Gamification |
| **Provides** | The analytical and motivational engine: every metric over any date range, a weekly letter-grade report card gated by data quality, a forgiving gamification economy, shareable single-stat cards, and an annual Wrapped-style report. |
| **User problems solved** | • "My tracker only shows fixed ranges — and locks old history behind a paywall" (Hevy's #1 complaint). • "My streak broke from a bug and I had to pay to restore it" (Cal AI's reviled $0.99 IAP). • "I want to actually test whether protein moves my trend, not just stare at charts" (Gyroscope Labs gap). |
| **AI consent category** | consumes F12 `insights-chat` — optionally, for richer narrative prose only; all grades, scores, statistics, and correlations are computed deterministically on-device. |
| **Primary evidence** | `docs/research/gyroscope.md` (report cards, Health Score, shareable cards, Labs, motion principles), `docs/research/macrofactor.md` (adherence-neutral tone, Annual Report), `docs/research/cal-ai.md` (streak anti-patterns), `docs/research/hevy.md` (fixed-ranges complaint, PR banners, stats surface area), `docs/research/happy-scale.md` (milestones, honest-math documentation), `docs/research/synthesis.md` §2.13, §5 (gamification for geeks, no-judgment tone, card-as-artifact) |

## 1. Purpose & Core Objectives

F11 turns logged data into understanding and motivation without judgment. It
owns the gamification **system** for the whole app: domain features emit events
(a PR from F05, a weigh-in from F06), F11 owns the rules, ledger, and economy.
It also owns the stats hub — the place the numbers-geek lives — and the weekly/
annual rituals that give the data a heartbeat. Its North Star: *describe the
data, never the person.*

Core objectives (verifiable):

- Any metric chartable over any date range in ≤3 taps; history is never capped
  or paywalled — ever.
- A streak can never be destroyed by a bug; freezes and restores are free,
  forever, with no purchase path in the binary.
- No grade, score, or correlation is ever claimed from thin data: below the
  data threshold, a `held` state with a stated reason is shown instead (the
  F07/Zolt pattern generalized).
- Share cards leak only what is on the card: silhouette and poop content are
  opt-in per share, never default.
- 100% of statistics compute offline; cloud appears only as optional prose via
  F12 `insights-chat`.

## 2. User Moments — when and how it is used

- **Daily (seconds):** glance the streak chip on F10; receive a PR banner
  styled by this feature's economy.
- **Weekly (the ritual):** Sunday report-card reveal — a fixed cadence, the
  Gyroscope lesson ("refreshed weekly" turns computation into an anticipated
  event). Two focused minutes.
- **Monthly:** recap card; period-over-period comparisons.
- **Annually:** the Wrapped report — an install-anniversary reveal (calendar-year
  toggle optional, R-U4) with chapters and share
  cards (MacroFactor's one celebratory flourish, made bigger).
- **Episodically:** deep stats dives, correlation exploration, composing a
  share card after a good weigh-in or workout.

## 3. How It Works — functional mechanics

**Inputs.** Event streams from F02 (meals, scan confidence), F03 (plan
adherence), F05 (sessions, PRs, volume), F06 (weigh-ins, trend), F07
(check-in results, TDEE estimates, quality states), F08 (silhouette captures),
F09 (Bristol entries, regularity), F10 (day-completeness, seconds-to-log).
Time series and custom EAV metrics from the F13 vault, with provenance records.

**Processing (on-device, deterministic):**

- **Stats engine** — aggregations, rolling windows, selectable moving averages,
  linear regression, distributions, weekday heatmaps; honors F06's
  one-value-per-day semantics for weight.
- **Grading engine** — per-domain rubrics (Energy, Nutrition, Movement, Body
  Trend, Gut, Capture) mapping measured statistics to letter bands. Every grade
  carries its inputs, the rubric version, and a quality score. Below data
  thresholds (e.g., <4 weigh-ins this week) the grade is `held` with a stated
  reason and what would unlock it.
- **Gamification engine** — multi-oracle streaks (a day counts via weigh-in OR
  meal-log OR workout; user-tunable); auto-granted freeze tokens (default 2/
  month, unused roll over to a cap of 5); *bug-repair restores are always free
  and automatic* via ledger diff; badges and first-time milestones; PR records
  (delegated detection: F05 for lifts, F06 for new trend lows); trend-based
  milestone ladders with per-milestone projections (Happy Scale pattern).
- **Correlation engine [v1.x]** — curated pairs first (fiber ↔ Bristol, steps ↔
  trend delta, eating window ↔ intake): Pearson/Spearman with lag scan, a
  minimum-n gate (n ≥ 21 days), and honest effect-size language.
- **Share-card renderer** — fixed-aspect PNG, oversized numeral, gradient
  chart, wordmark: designed to survive screenshotting (Gyroscope's growth loop).

**Outputs.** Weekly report-card artifact; insights feed (templated natural-
language sentences, each linking its provenance explainer); badges/milestones/
PR events; share cards; annual Wrapped chapters; optional F12 prose polish.

**State owned.** The gamification ledger (streaks, freezes, badges, milestones,
PRs); versioned rubric definitions; insight read-state; share templates and
their privacy flags; correlation configurations; experiment registry [moonshot];
Wrapped history.

## 4. User Interaction Model

**Entry points.** Bottom-nav Insights tab; chips on F10; the actionable "Your
weekly report card is ready" notification; share-sheet targets from any chart.

**Primary flows.**

1. *Weekly ritual:* open → domain cards flip in sequence, each letter stamping
   with a haptic (heavier for an A) → tap a grade → rubric, exact inputs, and
   quality panel, "how we graded this". Happy path ~2 min. *Fallback — held
   week:* cards show `held` + reason; no shame state exists to fall into.
2. *Stats dive:* metric picker → range via pinch/zoom (free range) plus presets
   (7/30/90/YTD/all — all always present, never capped) → overlay a second
   metric or a previous period. *Fallback:* metric not yet tracked → link to
   its owning feature.
3. *Share composer:* long-press any card/chart → composer with a privacy
   checklist (sensitive domains visible only behind explicit per-share toggles)
   → render (~200 ms) → native share sheet. The card, not the database, is
   what leaves the phone.
4. *Streak freeze moment:* open after a missed day → "Freeze used — streak
   intact, 2 left this month." No purchase path exists; a bug-caused loss
   self-heals with a visible "repaired" note.

**Input minimization.** Exploration is pick-not-type: everything is derived;
the user only chooses what to view or share. Range selection is gesture-first.
Insight sentences expand in place; no forms anywhere in F11.

**Micro-interactions (concrete).** Grade stamp: letterpress press-in 250 ms +
haptic tick; hero numerals count up (500–700 ms, spring); chart scrub with
magnetic snap to data points and a per-point tick haptic; correlation scatter
points land staggered 20 ms with soft pops; PR banner slides in with a particle
shimmer — deliberately *not* confetti (geek tone); Wrapped: full-screen
cinematic vertical scroll, 60 fps, chapter transitions synced to haptic pulses,
a share card at each chapter.

**Data-quality gating.** `held` grades with sample-size reasons; correlations
render "n = 9 — not enough data to claim anything" instead of a weak r; the
composite score (below) disables itself during `held` weeks.

## 5. What the User Gets Out

- **Weekly report card** — letter grades per body system (Gyroscope pattern),
  trend annotations, and the raw inputs behind every grade.
- **Stats hub** — every metric, including F13 custom EAV metrics, over any
  range: lines, moving averages, regression overlays, distributions, weekday
  heatmaps, period-over-period compare.
- **Insights feed** — one-sentence templated findings ("Fiber averaged 28 g on
  Bristol-4 weeks vs. 19 g on Bristol-3 weeks"), each with a provenance link.
- **Badge / milestone / PR gallery** with dates and the underlying numbers.
- **Share cards** per metric, with per-card privacy state persisted.
- **Annual Wrapped** — days logged, km walked, kg of food logged, biggest PR,
  trend journey animation, "your year in Bristol" (opt-in chapter).
- **Consistency Score** [v1.x — spec'd with care]: a composite that measures
  *observability and self-set adherence* — capture consistency and adherence to
  the user's own plan — **never** body worth or health itself. Dormant tile
  until ≥4 weeks of data, then opt-in activation; permanently hideable (R-U3)
  — formula published and versioned in-app, quality-gated, never compared
  across users, no lifespan or health-span claims (explicitly refusing
  Gyroscope's "estimated lifespan" marketing). Known risks spec'd here:
  single numbers invite Goodharting (optimizing the score over the goal), shame
  loops, and false health authority; the reframe to consistency, the opt-in
  default, and published math are the mitigations.

## 6. Motivation & Psychology

Returns are driven by fixed-cadence reveals (weekly/annual), the itch to test
hypotheses about oneself, and streaks that are *forgiving by design* — a rest
day shouldn't kill a month of work, and a bug should never cost money (Cal AI's
$0.99 restore is the reviled anti-pattern). Gamification is number-native only:
streaks, PRs, grades, milestones, Wrapped — never a virtual pet (Foodvisor's
unskippable-mascot pattern is a documented category miss for adults). Tone
rules: grades describe data, not virtue ("B, from 4 weigh-ins" is honest, not
shameful); a banned-words list ("failed", "cheat", "bad", "guilt") is enforced
on all copy templates; every negative finding ships with a neutral next action
or none at all.

## 7. Relations to Other Features

- **Consumes from:** F02/F03/F05/F06/F07/F08/F09/F10 (event and series data as
  above) · F13 (query layer over the vault, custom metrics, provenance
  records) · F12 (`insights-chat`, optional prose; calls redact identifiers) ·
  F01 (goal and milestone definitions; Fresh Start resets the ledger).
- **Feeds into:** F10 (streak chip, report-ready pill, PR badge-dots) · F01
  (Fresh Start decision: carry or clear gamification history) · domain
  features (badge-unlock callbacks for their own celebration moments).
- **Shared concepts:** Provenance, Data-quality states, Targets, Trend weight,
  Logs.
- **Boundary:** F11 owns rules, ledger, grades, and economy. Domain features
  own their raw PR detection and their in-flow celebrations; F10 owns display
  of F11 chips. F11 never writes to domain data.

## 8. Blue Sky Ideas

- **[v1] Forgiving streaks** — multi-oracle, auto freeze tokens, free automatic
  bug repair; no purchase path exists anywhere.
- **[v1] Weekly report card + `held` gating** — Gyroscope's pattern with WLO's
  data-quality spine.
- **[v1] Arbitrary-range stats hub** — Hevy's fixed-ranges complaint answered
  permanently; all history free forever.
- **[v1] Share cards with per-domain privacy** — silhouette/poop opt-in per
  share; card-as-artifact rendering.
- **[v1.x] Annual Wrapped** — install-anniversary reveal (R-U4; calendar-year
  toggle optional), chaptered, shareable, computed
  entirely on-device (MacroFactor pattern, amplified).
- **[v1.x] Consistency Score** — opt-in, published-formula composite as spec'd
  in §5.
- **[v1.x] Curated correlations** — fiber ↔ Bristol, steps ↔ trend delta,
  eating window ↔ intake; minimum-n gated.
- **[v1.x] "Log efficiency" flex** — median seconds-to-log and scans-per-week
  from F10/F02 data; the numbers-geek bragging stat (Lose It!'s published
  "3.5× faster" evidence, personalized).
- **[future] Period-over-period benchmarks** — "your September vs. your
  August" narratives; plus offline percentile reference tables shipped in the
  APK (static, cited, no network, clearly labeled as population context, not
  judgment).
- **[future] User-authored report cards** — define custom domains and which
  metrics feed them, reusing the rubric engine.
- **[moonshot] Custom metric A vs. B correlation builder** — any two EAV
  metrics from F13, with lag scan and honesty gates.
- **[moonshot] Self-experiment A/B framework** — Gyroscope Labs style: pick a
  hypothesis ("does more protein move my trend?"), run alternating A/B
  windows, auto power-analysis, and an honest "no effect detected" outcome;
  results feed the insights feed with full methodology panels.
- **[moonshot] "Ask your data" chat** — grounded in local history via F12
  `insights-chat`, staged confirm-before-write for any suggested action.

## 9. Guardrails, Privacy & Sensitivity

- Share cards: silhouette and poop content require explicit per-share opt-in;
  the composer defaults to weight-trend/energy only. A per-domain "never
  include in shares" master switch exists.
- No leaderboards, social feeds, or comparisons to other users — a documented
  anti-pattern for a privacy-first product.
- Free-forever invariants: no stat cap, no paywalled history, no purchasable
  streak repairs — structurally impossible, not just policy.
- All statistical computation runs offline; only optional prose goes to the
  user's chosen F12 provider, with identifiers redacted per F12 policy.
- Rubrics, thresholds, and the Consistency Score formula are published and
  versioned in-app ("how we grade"); changes are changelogged, never silent
  (Gyroscope's late-stage tier-churn complaints are the cautionary tale).
- Grading language is reviewed against the banned-words list; `held` states
  must always be available so no one is graded on thin data.

## 10. Open Questions

- Report-card day: Sunday (proposed, matches F07 check-in) or user-selectable?
  *(Resolved: R-U2 — Sunday default, user-selectable; grades share the check-in day.)*
- Streak oracle defaults: which events count on day one, and how configurable
  should oracles be before configuration becomes a chore?
  *(Defaults resolved: R-U10 — weigh-in OR meal-log OR workout, user-tunable;
  how far tunability should go remains open.)*
- Consistency Score: default off (proposed) vs. shown-but-dormant tile?
  *(Resolved: R-U3 — dormant tile until ≥4 weeks of data, then opt-in
  activation; hideable permanently.)*
- Wrapped date: December 31 reveal vs. install-anniversary reveal?
  *(Resolved: R-U4 — install-anniversary; calendar-year toggle optional.)*
- Badge visual direction: minimal geometric (numbers-first) vs. slightly
  playful illustration — needs design exploration.
- Do `held` grades ever count toward streaks as "neutral" days?
  *(Resolved: R-U10 — `held` days are neutral: they neither advance nor break.)*
