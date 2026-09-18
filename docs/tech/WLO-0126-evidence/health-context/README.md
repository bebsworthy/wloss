# Profile health context and intake scenario verification

Health answers now belong to Profile only. Intake consumes the profile context and links there for recommendation details. The older goal editor and plan wizard no longer expose answer controls. Canonical profile context overrides legacy copies; consistent legacy answers are preserved, conflicts require review, and unanswered never implies No.

## Checks

- Android debug assembly, F01/app detekt and ktlint checks passed.
- 201 unit tests passed: 143 engine, 34 F01, 24 app. Includes all nine goal/adjustment combinations, unknown health context exploration, invalid/held inputs, legacy reconciliation and canonical profile persistence/isolation.
- Emulator: profile health context saved and reopened; prior selected answers retained. Existing intake displayed as Saved target; Reset selected a current suggestion. No intake target was saved during verification.
- Native screenshots/XML for saved deficit, zero, surplus and blank input. `native-stability.json` asserts identical header, capsule, input label, Reset and slider bounds at the same scroll position. Keyboard focus can scroll the viewport; returning to the top restores identical bounds.
- Mockup: `mockup-stability.json` checks three objectives × deficit/zero/surplus/blank; all chart/control rectangles remain identical, and every valid scenario is visible. Placeholder text clears correctly on recovery.
- Emulator density/size restored to default after comparison.

## Visual evidence

See ../profile-scenario-final-02/index.html and its overlay/side-by-side/report.json. The capture uses a 390px reference and uniform 390/1080 scale, anchored on the first maintenance numeral. Headline numbers are matched by a runtime-only fixture. Mockup chart remains illustrative (84→78 kg); native chart uses 77.1→74 kg and the production scenario engine. Therefore paths, goal positions, captions and provenance labels intentionally differ. Both use Inter; platform rasterization and small text/control details remain visible in the overlay, which is not a pixel-identity claim. The key vertical regions align and no scenario removes chart space.

The earlier profile-scenario-final directory is an unsuccessful capture (anchor was scrolled off-screen); final evidence is profile-scenario-final-02.
