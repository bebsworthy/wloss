# WLO-0102 — Trend panel review

Reviewed 2026-09-16 at HEAD 578435f. Scope: supplied screenshot, current production source and mathematical counterexamples. No application changes or new device tests. This is a fresh review: some accessibility defects in WLO-0101 have since been fixed and are not repeated here.

Evidence: [supplied screenshot](WLO-0102-evidence/trend-panel.png). Screenshot establishes presentation, not its underlying dataset or binary identity. Source findings apply to the inspected revision. Severity: P1 numerical/trust defects; P2 interaction/clarity/layout defects; P3 refinement. Recommendations are design decisions, not claims that every visual preference is a Material rule.

## Verdict and user task

Changes required. The panel should answer: What is my weight doing, over which dates, and how dependable is that answer? Instead it emphasizes controls and implementation metadata, separates the useful summary from the chart, and exposes a preview whose identity is unclear. Standard M3 widgets are present; their composition and information hierarchy are the main design failure.

## Calculation and semantic correctness

**C1 · P1 — Changing viewport changes the calculation.** `WeighInViewModel.reload` derives `from` from the selected chart window (line 947), calls `weighIns.trend` with it (1092), and the repository smooths only that range. `SmoothingEngine.ewma` seeds from its first input. Thus the same historical date has a different trend when viewed in 30d versus 90d/all. Counterexample at alpha .15: [80,70,70] → [80,78.5,77.225]; cropping the inputs to [70,70] produces [70,70]. These are correct EWMA operations over different inputs, but wrong behavior for a viewport selector. Compute a stable authoritative series using a documented initialization policy, then crop its output. Verify overlapping dates are identical across viewports.

**C2 · P1 — Chart and headline/change use different histories.** `WeighInRepository.currentTrend` (542–584) restarts EWMA over the latest 30-calendar-day range, whereas the chart uses the selected range. At default settings, `reload` uses the canonical current/deltas but the viewport-derived chart. Consequently the chart endpoint can disagree with the hero; the 30-day change starts at a freshly seeded raw value rather than the historical smoothed value on that date. Canonical consistency within other consumers does not establish mathematical continuity. Reconcile the governing 30-day computation policy before changing the engine; obtain current, historic endpoints and deltas from one defined series. Verify hero = chart endpoint and change = displayed-series endpoint difference, including an outlier at the 30-day boundary.

**C3 · P1 — Reset-to-default can silently retain a different computation.** `renderTunerPreview` (823–899) recomputes current/deltas from the currently visible samples even when settings return to default, and marks preview false. A later reload instead obtains canonical 30-day current/deltas. Thus the same visible settings can yield different results before/after refresh. Test alpha away/back to .15 on a 90-day chart, then refresh; values must remain identical. Reset must restore the authoritative series, not just the method/alpha flag.

**C4 · P1 — “7-day” moving average means seven samples.** `SmoothingEngine.trend` discards dates before `movingAverage`; that method uses array indices (69–82). Two observations on day 1=100kg and day 20=80kg produce 90kg on day20, whereas the available observations in a trailing seven-calendar-day window yield 80kg. Define a calendar window and sparse-data hold/coverage rule, then implement it using dates. Do not manufacture missing observations. EWMA similarly weights observations rather than elapsed days: that can be a legitimate model, but its time interpretation must be explicit.

**C5 · P2 — 30-day preview can silently mean a stale period.** Preview delta uses last available point minus 29 days (854 and 1125), while caption ends today; the canonical path requires coverage of the current 30-day range. On stale data these paths have different availability/period semantics. Label exact change dates and freshness; hold the current-period metric when prerequisites fail. An inclusive 30-day window spans 29 elapsed days: this convention is explicitly in this implementation, not by itself an off-by-one defect.

**C6 · P1 — Provenance is missing and its displayed count is false.** Trend ChartPoints omit sourceEventIds (`WeighInViewModel` 878 and 1196); `ChartPoint` defaults to an empty list; detail renders that as “0 sources.” Derived points do have inputs. Propagate contributing identity/coverage, or explicitly say provenance unavailable for legacy data. Never equate missing metadata with zero inputs.

**C7 · P2 — Per-point explanation receives whole-series input count.** `SmoothingEngine.trend` (111–133) attaches identical `n=values.size` metadata to every point. Screenshot first selected EWMA point says n=45 although past-only EWMA at its seed uses one reading. Label full-window count separately; provide point-specific contributing inputs. Zero-phase must disclose use of later measurements and historical revision.

**C8 · P2 — “Measured points” are daily representatives.** The plotted sample list comes from `dailyScalars`, not the raw event feed. Median daily policy can create a value that was never measured. Use “Daily weight” with the active selection rule; offer raw readings separately. Verify multiple readings and even-count medians are not represented as individual measurements.

The screenshot's −0.8kg cannot be independently recalculated without the actual readings and state. Do not assert a corrected number from pixels. Nor does a trend line need to go through each raw dot: deviation and lag are expected smoothing behavior.

## Presentation, clarity and screen composition

**V1 · P2 — Debug representation takes over the card.** `chartPointDescription` in `WloTrendChart.kt` renders provenance directly, fed by `.toString()`. The large multiline `Derived(formulaVersion=…, inputs=[alpha=0.304254…])` is developer output, not an explanation. Show date, formatted value and plain-language role in one compact inspection block. Put readable method, parameters and inputs in a detail sheet. Preserve full precision internally, not in routine UI.

**V2 · P2 — The useful summary appears after the least useful detail.** “30-day trend −0.8kg” is visually subordinate and pushed below selection metadata. Selection can insert an arbitrarily tall block and move all downstream controls. Keep the latest trend/change summary stable above the plot; selection has a bounded slot or sheet and does not replace the latest-state context. Clearly label Selected versus Latest.

**V3 · P2 — Date ticks imply the wrong positions.** `trendAxisTicks` evenly divides the interval then drops day-of-month. For 19 Jun–16 Sep it produces 19Jun,19Jul,17Aug,16Sep but displays Jun/Jul/Aug/Sep. The selected 3Aug point correctly lies before the Aug17 tick, yet appears to sit in July. Use real calendar boundaries or label the actual dates. Do not move the data to satisfy misleading labels.

**V4 · P2 — Viewport and coverage are not adequately distinguished.** Roughly half this 90-day plot is blank, while 45 daily samples occupy the latter half. Keeping the requested time domain is honest; stretching those readings across 90 days would be wrong. State “90-day view · readings from 3 Aug” (using real dates/counts) beside the plot. `trendWindowCaption` suppresses coverage whenever data ends today, even when its beginning is much later than the viewport.

**V5 · P2 — 90-day view, 30-day change, 3Aug selection have no explicit relationship.** Each can be valid, but the card makes the reader infer which number answers which question. Label “Change over last 30 days” with dates; show selected-point date beside its value; keep the visible range beside its selector. A fixed 30-day metric is a product decision, not automatically an error because 90d is selected.

**V6 · P2 — Legend has no visual keys and insufficient semantics.** “Measured points · Trend line” lacks dot/line swatches; “EWMA” is not an accessible human role label. Use distinct swatches plus “Daily weight” and “Smoothed trend,” and “Preview trend” when applicable. State freshness/coverage without implying a statistical confidence interval not calculated by the engine.

**V7 · P2 — Preview is disclosed too late, without a usable reset.** Screenshot alpha .304… differs from default .15, yet selected chip says “trend (default).” That label describes method, but can be read as default configuration. The preview warning is after the tuner, outside this screenshot, and the hero has no explicit preview label. WLO-0054's answered owner question requires hero-following-preview plus visible Preview and Reset. Keep that behavior, label it locally, provide a deterministic Reset action; distinguish experimentation from saved settings.

**V8 · P2 — Technical tuning overwhelms the routine tracking task.** Method chips, alpha and generic math navigation live in the main card. Tuning is also listed as deferred in DESIGN-SYSTEM §7.7. Gate it per release policy; if retained, put it behind a clearly named disclosure/settings sheet. Explain smoothing versus responsiveness, lag, and zero-phase historical revisions before asking users to choose.

**V9 · P2 — Controls do not fit.** The screenshot clips the third smoother choice, and `SmootherTuner` uses an unwrapped Row. Use a sheet with standard M3 single-choice rows/radio buttons, or a wrapping layout appropriate to the labels. Verify compact widths, long translations and 200% font scale.

**V10 · P2 — The FAB obscures the controls.** The Weigh in action is appropriate as the screen's primary action; covering tuning controls is not. The screenshot proves overlap. The ordinary-height path uses a floating action above the scroll content, without a dedicated action exclusion region. Use a suitable reserved bottom action region or collapse/reposition the FAB while inspecting content. Every control must be revealable and fully operable; do not solve this by deleting accessible target padding.

**V11 · P3 — Heavy container, sparse plot, weak hierarchy.** A large outlined card encloses several distinct tasks and extends beyond the viewport. Repeated outlines (container, selector, chips) compete with the thin trend line; much more visual space goes to metadata/control chrome than interpretation. Prefer a calm section surface, compact summary, plot and one detail affordance. This is a composition recommendation, not a prohibition on outlined cards or rounded segmented controls.

**V12 · P2 — Plot readability does not adapt well.** Canvas fixes its height to180dp, plot insets to24/42dp and axis text to11sp. Text scaling consumes the same reserved geometry; only two y-axis labels require interpolation for every other value. Add restrained intermediate ticks where useful, scale layout with typography, and validate rather than infer contrast from the screenshot. Trend stroke is 2.5 raw pixels while dot radius uses dp, making relative visual weight density-dependent.

## Interaction and micro-interaction

**I1 · P2 — Tap selection ignores the dot the user touched.** `closestTrendPoint` uses only epochDay, and samples precede trend points on equal dates. Tapping the trend line can select the daily weight instead; tapping anywhere in the empty half selects the nearest first point, even far away. Use screen-space hit testing with a reasonable distance threshold or an explicit date cursor showing both series. Add drag scrubbing if this is the intended inspection model; do not imply drag works today.

**I2 · P2 — Arrow meaning and visual language are inconsistent.** Back arrow resembles screen navigation; next is a chevron. Both say “sample” but walk a merged daily/trend list, often changing series without changing date. Use matching icons and an explicit “Previous day / Next day” contract, with both series shown for a day, or a named series selector.

**I3 · P2 — Selection lacks clear exit and focus feedback.** There is no clear-selection action; chart selection adds content without an explicit polite announcement. Canvas is focusable but lacks a designed visible focus/cursor state. Provide dismissal, selected day/series semantics, bounded updates and keyboard/screen-reader feedback. Current code DOES enable initial previous/next navigation, supports Home/End and selectable data rows: do not repeat the older claim that these are all unavailable.

**I4 · P2 — Explain has inconsistent behavior.** Selected detail toggles raw provenance when no callback is supplied. In View data, Explain instead closes the sheet and invokes a nullable callback; this screen supplies none. The user sees selection, not an opened explanation. Make both entry points open the same human-readable explanation surface.

**I5 · P2 — Alpha remains interactive when mathematically irrelevant.** The tuner always displays alpha; MOVING_AVERAGE_7D ignores it. Hide/disable this control with an explanation for that method. For EWMA, show meaningful rounded values, an accessible name/value and reset; do not require a continuous slider to land on .15 within1e-9 to restore default status.

**I6 · P2 — Missing readings look like uninterrupted evidence.** `drawSeries` joins every point, even across long gaps. Specify a gap policy: break or distinguish long missing intervals and explain coverage. Do not silently imply daily observations or model confidence. Zero-phase historical changes also need meaningful feedback; motion quality/haptics were not measured in this review.

## Proposed composition

1. Stable summary: latest smoothed weight, date, separately labeled trailing30-day change and Preview badge/reset when relevant.
2. Standard M3 range selector; actual visible dates and partial-coverage hint nearby.
3. Legible chart with correct calendar ticks, restrained grid and keyed daily/trend legend.
4. Compact selected-day row: date, daily weight, smoothed weight, clear selection, and “How calculated.” Matching previous/next-day actions; accessible table alternative.
5. Secondary “Chart settings” disclosure for supported advanced modes. One explanation sheet with plain-language method, policy, contributing readings, coverage and technical details on demand.
6. Screen-level Weigh in action placed so it cannot obscure the panel's controls. Keep bottom navigation as screen navigation.

## Verification gate

- Golden calculation fixtures cover sparse calendar data, viewport invariance, boundary outliers, stale periods and reset/refresh consistency. Reconcile the governing numerical contract first.
- All numbers/series share an explicit policy, method, history, end date and unit; preview cannot be mistaken for saved truth.
- Point explanations contain actual contributing evidence or an explicit unavailable state, never fabricated zero-source counts.
- At compact width and200% text, every option and action fits or is reachable; FAB covers no required control.
- Tap/drag, keyboard, TalkBack and View data identify the same selected date/values; both Explain entries open the same detail.
- No implementation serialization appears in ordinary UI. Selection does not cause unbounded layout movement.
- Validate missing-data gaps, no-data, sparse, held, stale and year-crossing labels, plus long localized strings.

M3 self-audit (0 incorrect/missing,1 partial,2 ready): task clarity1; hierarchy0; component semantics1; tokens1; adaptive behavior0; states/feedback0; accessibility1 (device validation outstanding); expressive restraint1. Not ready for approval.

Reference baseline: [Android Material component guidance](https://developer.android.com/design/ui/mobile/guides/components/material-overview) and [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults). These support semantic component choice and accessibility behavior, not claims that this screenshot alone proves exact target sizes or contrast failures.
