## Context

Companion to WLO-0053 (chart honesty; screenshots there). The smoother
tuner inside the F06 trend card — `WeightScreen.kt:352-388`
(`SmootherTuner`) + the method/alpha events in `WeighInViewModel` — is
honest about the math but not about the state, and it is expensive:

- **The preview contract contradicts itself.** At α ≠ 0.15 the banner says
  "the saved trend keeps the default smoother" while the hero stat, the
  delta chip, and the chart line have ALREADY swapped to the preview values
  (`WeighInViewModel.kt:614` delta, `:637` current:
  `current = if (atDefaults) canonical else lastPoint.trendKg`). The screen
  shows preview numbers and claims it doesn't.
- **The default is unreachable.** The α slider is continuous over
  0.05..0.5 (no `steps`, `WeightScreen.kt:381-387`) while "at defaults" is
  an epsilon compare to exactly 0.15 (`WeighInViewModel.kt:591-593`) — once
  touched, the slider practically cannot return to default, so the preview
  state (and the swapped hero) sticks for the session with no reset.
- **Every drag frame is a database trip.** Each `AlphaChange` tick launches
  a full `reload()` (`WeighInViewModel.kt:342-346`) — several repository
  queries including the canonical `currentTrend` — once per drag frame,
  un-debounced. `WindowChange`/`MethodChange`/`AlphaChange` coroutines are
  uncoordinated; two rapid taps can complete out of order and paint stale
  state under the newly-selected chip.
- **Placement.** The tuner renders even when the chart is empty (controls
  for a line that cannot exist) and sits above the math link in the card.
- **Semantics leak.** The preview banner reuses `held` (amber — the design
  system's "weak data held" provenance color) for a settings disclaimer.

## Scope

### A. α slider mechanics
1. Stepped slider: 0.01 steps across 0.05..0.5 (Material3 `steps`);
   the label reads the stepped value. Snapping makes 0.15 reachable again.
2. Reset affordance beside "Responsiveness α": a "Reset" text button,
   visible whenever method ≠ EWMA or α ≠ default; restores EWMA + 0.15 —
   the preview banner clears and the canonical read returns (single source
   of truth, WLO-0030 defect 9).

### B. Preview honesty
3. When `!atDefaults`, the hero stat + delta chip carry a visible "preview"
   mark (small label chip per DESIGN-SYSTEM chip anatomy) so a swapped
   number is self-describing. Banner copy names what is previewed and the
   way back: "Previewing {method}, α {α} — Reset returns the saved trend
   (EWMA, α 0.15)." (Final copy at implementation; zero-guilt tone per
   DESIGN-SYSTEM §1.)
4. Banner color: move off `held` to a neutral/tertiary info treatment —
   `held` stays reserved for held-data provenance.

### C. Cheap, ordered state
5. Preview recompute in memory: `reload()` caches the window's daily
   scalars; `MethodChange`/`AlphaChange` recompute the series through
   `SmoothingEngine` (pure) from that cache — no repository round-trip per
   drag frame. Full store reload happens on window change, save/delete/undo,
   and cache miss.
6. Coalesced reload: one cancellable Job per reload (cancel-and-relaunch),
   so rapid chip taps / event bursts land in order and only the final state
   paints. Inject the dispatcher so tests can prove it.

### D. Placement + reach
7. Tuner collapses behind a "Tune the math" disclosure in the trend card —
   chart-first hierarchy; disclosure state survives recomposition (Saver).
   Hidden entirely while the chart is empty (rule defined in WLO-0053 §C.8).
8. Slider a11y: `stateDescription` ("Responsiveness α 0.17") merged with the
   label semantics; soft tick haptic on step boundaries (design-system
   haptics; on-brand micro-interaction).

## Out of scope

- Persisting a non-default smoother/α: the saved trend keeps the default
  (WLO-0030 defect 9 ruling; R-A2 "α default 0.15 in the open") — unchanged
  unless the owner re-rules.
- Compare-modes overlay (all smoothers at once) — WLO-0042.
- Chart rendering itself (axis, gate, empty state) — WLO-0053.

## Tests

- Stepped slider values; Reset restores `atDefaults` (epsilon holds).
- In-memory recompute equivalence: series recomputed from cache ==
  repository `trend()` for identical inputs.
- Coalescing: a burst of window/alpha events lands exactly the final
  selection; no intermediate paints (test dispatcher).
- Preview marks: hero carries the preview mark iff `!atDefaults`; banner
  copy state table (default vs preview vs reset).
- TalkBack semantics smoke for slider + disclosure.

## Acceptance criteria

- After touching α, the saved default is always one tap away (Reset); the
  banner clears on reset.
- Hero/delta show the "preview" mark whenever they display preview values;
  the banner names the preview and the way back.
- Dragging the slider performs zero repository queries (recompute from
  cache).
- Rapid window/alpha changes never paint an older selection over a newer
  chip.
- Tuner is disclosure-collapsed by default and absent on an empty window.
- No amber `held` on the preview banner; slider fully labeled for TalkBack
  with step ticks.
