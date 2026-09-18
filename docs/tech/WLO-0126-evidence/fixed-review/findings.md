# Visual correction verification — WLO-0126

Compared the rebuilt Android app with flow 10 using `tools/screenshot-compare/agent.py`. Inspected side-by-side.png, overlay.png and report.json.

## Corrected

- Hero values enlarged from 28sp to 36sp. At normalized screen width, the first hero glyph is now 24.56px high against 25px in the reference (previously 19.5px).
- Maintenance and intake remain side by side; units now precede compact source labels. Intake and weight goal use gold. Existing manual targets correctly say “Set by you”.
- Intake toolbar uses the screen background and a smaller title.
- Adjustment / Intake is a green M3 segmented capsule. Both modes retain the slim M3 slider with a round thumb and exact number entry.
- Save is full width; the redundant bottom Cancel button is removed. Toolbar Up retains the unsaved-change guard.
- “How is this calculated?” is a neutral disclosure row with +/−. It and the slider labels fit above Save on the tested device.
- Cold-start projections show the outer uncertainty range and a visible dashed gold goal line, without suggesting a measured central trajectory.

## Evidence boundaries

The in-memory HTML fixture matches the hero values (1,776 maintenance and 1,276 intake) and goal heading (77.1 → 74.0 kg). It does not alter the HTML file. The illustrative mock chart still uses its original 84 → 78 example; Android uses the production forecast engine and actual fixture data. Chart trajectory and dates therefore intentionally differ. The saved Android plan uses a weekly schedule, so the unit says “average” and source says “Set by you”.

This is not a pixel-perfect match. Android's standard M3 field uses a floating label; its font rasterization, source-chip metrics and vertical spacing differ. The reference is 390×901 including its decorative frame; Android is 1080×2400 with platform system bars. Width normalization and first-glyph alignment preserve those differences rather than stretching the screen. Remaining spacing accommodates the shorter native screen and its navigation bar.

## Validation

- `:app:assembleDebug` passed; APK installed on emulator-5554.
- F01, core/designsystem and app detekt and ktlint checks passed.
- App's 24 and F01's 27 unit tests passed (51 total).
- Emulator: Intake mode preserved 1,276 with slider visible; center tap selected 1,776 maintenance; switching to Adjustment preserved that as 0; tapping the deficit side selected −550 / 1,226 and held the projection while keeping Save enabled. Restored the suggested −500 / 1,276. Disclosure opened and closed correctly. No stored plan changes were made during this visual pass.
- `intake-mode.png` records the second mode after restoring the original values.
