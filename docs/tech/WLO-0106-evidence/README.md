# WLO-0106 — Weight overview visual fidelity

Implemented and checked on 16 September 2026.

## Result

- Weight root owns a standard M3 top app bar, with chart settings in its action slot and the page background continuing underneath it.
- Quiet eyebrow, medium display numeral and explicit grouping replace uniform vertical gaps.
- Change and goal share a baseline; the goal remains an M3 text button with enough internal space to avoid clipping.
- Summary divider and 24dp content gutters restore structure. The native history ListItem's own 16dp inset is accounted for.
- The period selector uses the theme's teal container roles. Weigh in is a standard M3 Button with a plus icon and reserved bottom space.
- Chart values occupy a right-hand axis gutter; the yellow goal annotation sits at the left. Goal and data share one linear scale. Individual weigh-in dots remain visible.
- At enlarged text sizes, date labels reserve space for the final date and skip intervening labels that would collide.

## Visual evidence

Open `comparison.html` for the updated device screen beside the approved mockup rendered with the same data and 30-day selection. Production is 1080×2400 at 420dpi (approximately 411dp wide); mock viewport is 412 CSS pixels wide. The mock's arrays were replaced only in browser memory using a read-only export of this emulator's synthetic data. The original mockup file was not modified.

The comparison is about composition rather than pixel identity: Android system bars, M3 component metrics, localized date formatting, chart tick selection and the existing navigation icon family differ. Production keeps the history and primary action visible at normal text size on the tested viewport; at 200% text, the content scrolls while the action stays reachable.

- `30-days.png`, `90-days.png`: standard text, actual Android app.
- `mock-matched-30-days.png`: reference using the same raw readings, persisted trend and goal.
- `selected-point.png`: actual point selection with date/weight tooltip.
- `large-text.png`, `large-text-chart.png`: 200% text; chart dates remain separated and goal visible when chart is scrolled into view.
- `settings.png`, `history.png`, `weigh-in.png`: navigation smoke checks. No weigh-in was saved.

The 30-day change is −0.8kg; the 90-day window's available data change is −1.2kg. Both retain the same latest trend of 77.1kg. No smoothing or change-calculation logic was changed by WLO-0106.

## Validation

Passed final command:

```
./gradlew :app:assembleDebug :core:designsystem:testDebugUnitTest :feature:f06-weight:testDebugUnitTest :app:testDebugUnitTest ktlintCheck detekt checkArchitecture :app:lintDebug
```

87 tests: design system 22, weight 42, app 23; zero failures/skips in the final run. `git diff --check` passed. Installed the debug APK in place without clearing data. Restored font scale to 1.0 after accessibility checks.

Earlier combined runs reproduced the pre-existing BodyFatViewModelTest timing failures (real DataStore I/O and test scheduler); isolated tests and final combined validation passed. These tests were not modified. One UI automation assertion expected a generic “History”/“Logbook” string before loading finished; a fresh hierarchy confirmed the loaded Weight logbook and 46 entries, then returning and opening Weigh in succeeded.

Scope: app bar ownership, Weight overview composition, chart rendering and the settings glyph. Existing unrelated working-tree changes were preserved. No broader navigation redesign or claim of device coverage beyond this emulator.

## Follow-up: typography and leading

Reopened after owner review identified remaining font-size/spacing differences. Corrected the actual cause rather than shrinking the page: the previous hero requested medium from a semibold-only display font family; the global headlineSmall alias supplied 20sp instead of the reference change figure's 24sp; 12sp metadata inherited 22sp prose leading. Added named overview roles with explicit sizes, actual font weights and line heights, documented in DESIGN-SYSTEM.md. Unit styling is now an optional WloHeroStat parameter, preserving defaults for other consumers. Wrapped goal text remains right-aligned. All sizes still follow system text scaling; no density/fontScale override was introduced.

Updated normal-size capture: typography/30-days.png. The comparison HTML uses that capture at the same 412px display width as the reference. Reviewed the summary at 200% text (typography/large-text.png); text wraps and the page remains scrollable. The final right-alignment refinement affects wrapped goal text only. Font scale restored to 1.0.

Validation for follow-up: debug build, ktlintCheck, detekt and 22 design-system unit tests passed; git diff --check clean. Earlier 87-test/lint report above belongs to the preceding layout revision, not a rerun of every suite for this styling-only correction.
