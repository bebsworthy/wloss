# WLO-0108 — Overview typography refinements

Applied the owner's detailed review:

- Hero: 56 → 50sp, semibold, −2.2sp tracking; unit 23 → 21sp medium.
- Change: 24 → 22sp semibold, tighter tracking, proper minus glyph.
- Goal summary: 17 → 15sp semibold. Supporting copy: 12 → 11sp medium with 16sp leading.
- Hero date: “Today 16 September”; day-first compact range dates. Month names follow device locale.
- Standard M3 segmented buttons: empty icon slot removes checkmarks; 13sp semibold labels, selected semantics and touch targets preserved.
- Yellow chart goal labels: semibold.
- Native M3 history ListItem: 14/20sp headline, 11/16sp supporting text.
- Standard M3 weigh-in Button: 13sp semibold label with a plus character using the same style, replacing the large icon.

Named typography roles and DESIGN-SYSTEM.md updated. No smoothing or data-calculation changes.

Validation: debug build, ktlintCheck, detekt, 22 design-system unit tests, and git diff --check passed. Installed in place on emulator-5554 without clearing data. Captured 30-day and 90-day selections and 200% font scale. Date assertion passed; controls and summaries remain readable, enlarged content scrolls. Restored system font scale to 1.0. These captures are the Android implementation, not generated mockups.

Evidence: 90-days.png, 30-days.png, large-text.png and accompanying UI hierarchies.
