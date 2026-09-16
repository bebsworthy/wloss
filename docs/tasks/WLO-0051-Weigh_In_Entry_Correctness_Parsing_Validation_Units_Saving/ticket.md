---
id: WLO-0051
title: 'Weigh-in entry correctness: parsing, validation, units, saving state, and outlier actions'
status: done
theme: weight-core
release: 1
created: 2026-09-14T16:45:09Z
modified: 2026-09-15T22:50:36Z
closed: 2026-09-15T22:50:36Z
revision: f884eb8b7d1e414e
blocks: [WLO-0037, WLO-0069, WLO-0070]
related: [WLO-0038, WLO-0050, WLO-0052, WLO-0079]
---

# Description

UX pass over the F06 weigh-in sheet from the Sep 2026 review: fix the silent-save failure cluster (empty weight, comma locales, errors hidden behind the sheet), prefill for the zero-typing common case, native date/time pickers (subtitle removed), and the trend-first save moment (haptic + confirmation + live trend preview) per F06 §1–§4. Items 5/7/8 (time-default chips, stepper repeat, custom numpad) ruled out of scope by owner.
