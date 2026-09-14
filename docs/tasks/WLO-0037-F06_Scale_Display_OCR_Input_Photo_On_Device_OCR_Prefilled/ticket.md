---
id: WLO-0037
title: 'F06 scale-display OCR input: photo -> on-device OCR -> prefilled weigh-in (v1 blue-sky)'
status: backlog
theme:
release:
created: 2026-09-14T13:37:55Z
modified: 2026-09-14T14:00:19Z
closed:
revision: f6b1126f649b5adc
blocks: []
related: []
---

# Description

# Goal

Photo-of-the-scale-display input (F06 §8 [v1]; research gyroscope.md): snap the display, on-device OCR fills the weigh-in field, editable before save. The camera assist is offered ABOVE the pad, never instead of it (R-U15); OCR failure lands silently on the pad and nothing is lost.

# Work items

- Reuse the :core:media viewfinder/shutter stack (WloViewfinder/WloShutterBridge pattern, as F02 does) in a capture step feeding the weigh-in sheet's field.
- On-device text recognition (ML Kit text recognition, bundled — no cloud call, F06 owns no consent category).
- Numeric parse + sanity check (plausible kg range; reject multi-number noise); recognized value animates into the field.
- Failure path: silent fallback to the typed pad; no error shaming.
- Test tags + parser unit tests; emulator demo with a display photo.

# Gates

Parser unit tests green; arch enforce 0; ktlint + detekt; manual capture demo. No new permissions beyond the camera F02 already uses.

## References

- Spec: [F06 §2](docs/features/F06-weight-body-metrics.md) (single most common flow) · §4 (no-smart-scale path — OCR offered above the pad, failure lands silently) · §8 [v1] photo-of-the-scale-display
- Research: [gyroscope.md](docs/research/gyroscope.md) (photo-of-display input origin)
- Mockup: [flows/04-weigh-in-trend-f06-f08.html](docs/design/flows/04-weigh-in-trend-f06-f08.html) — OCR path / OCR lock / OCR thumb annotations ("OCR failure lands silently on the pad")
- Precedent: :core:media WloViewfinder + WloShutterBridge wired in app/navigation/WloApp.kt for F02 capture
