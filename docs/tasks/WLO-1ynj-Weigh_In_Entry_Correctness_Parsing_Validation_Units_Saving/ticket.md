---
id: WLO-1ynj
was: WLO-0051

title: 'Weigh-in entry correctness: parsing, validation, units, saving state, and outlier actions'

status: done
closed: 2026-09-15T22:50:36Z
status_changed: 2026-09-15T22:50:36Z

theme: weight-core

release: 1

created: 2026-09-14T16:45:09Z

blocks: [WLO-fss2, WLO-x78q, WLO-zq3k]

related: [WLO-07e3, WLO-35f0, WLO-d4a6, WLO-tdwg]

parent:

rank:
---

# Description

UX pass over the F06 weigh-in sheet from the Sep 2026 review: fix the silent-save failure cluster (empty weight, comma locales, errors hidden behind the sheet), prefill for the zero-typing common case, native date/time pickers (subtitle removed), and the trend-first save moment (haptic + confirmation + live trend preview) per F06 §1–§4. Items 5/7/8 (time-default chips, stepper repeat, custom numpad) ruled out of scope by owner.
