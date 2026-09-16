# WLO-0087 runtime evidence

Captured 2026-09-16 from a fresh build of source baseline `66fd2c5`.
Command: `./gradlew :app:assembleDebug --console=plain --quiet` (exit 0), followed by `adb install -r`.

Environment: existing `wlo-api29` fixture, API 29 ARM64, 1080×2400 at 420dpi; app forces dark theme. Emulator launched read-only with snapshot saving disabled. No stored weigh-in, edit, deletion, import, restore or Fresh Start was committed. Emulator shut down after review; font scale was reset to 1.0 before shutdown.

- 01: Weight launch with existing populated history. Chart lies below the first viewport; Current trend value is replaced by a derived badge.
- 02: Capture opened from Weigh in; dismissed without saving. Keyboard/validation were not exercised in this capture.
- 03: Goals reached through Settings; target/pace/budget have no persistent label when populated.
- 04: Weight after tapping Body fat. Selection/content did not change. The source's SectionChange handler updates a separate flow without publishing uiState.
- 05: Weight at Android font_scale 2.0; inspected reflow. Navigation truncation and hero/delta wrapping are visible.
- 06: Body fat appears after Home and activity resume trigger a reload. Empty-state hierarchy and compact tape inputs are visible.

The text files are compact UIAutomator summaries, not complete TalkBack test results. No physical haptics, performance, large-window, Health Connect, destructive transaction or full lifecycle validation is claimed. See the parent review acceptance matrix.
