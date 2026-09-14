---
id: WLO-0040
title: 'F06 weigh-in reminder notification: soft, user-set, zero-guilt copy'
status: done
theme:
release:
created: 2026-09-14T13:37:55Z
modified: 2026-09-14T16:05:12Z
closed: 2026-09-14T16:05:11Z
revision: f75db9f3fff7ace2
blocks: []
related: []
---

# Description

# Goal

The soft daily weigh-in reminder (F06 §4 entry points). Reminder, never nag: dismissal is respected, no streak-guilt copy, ever.

# Work items

- Settings row: on/off + time picker (default morning window).
- POST_NOTIFICATIONS runtime permission flow (Android 13+), politely worded.
- WorkManager scheduling (inexact is fine); survive reboot; cancel cleanly on off.
- Tap → deep link wlo://weight/log (sheet-open route).
- Copy per §2/§6 tone rules ("the morning window reads steadiest") — no "you missed" framing.
- Scheduler unit tests (time edges, DST shift).

# Gates

Emulator demo firing via adb; arch enforce 0; ktlint + detekt.

## References

- Spec: [F06 §2](docs/features/F06-weight-body-metrics.md) (cadence — gently teaches same conditions, never demands) · §4 entry points (weigh-in reminder: soft, user-set)
- Design: [IA.md](docs/design/IA.md) notification budget rules (R-U13/R-U1) + deep-link registry (wlo://weight/log)
