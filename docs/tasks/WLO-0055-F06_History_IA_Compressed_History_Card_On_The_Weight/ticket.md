---
id: WLO-0055
title: 'F06 history IA: compressed history card on the weight surface + full-page logbook (verbatim, month sections, windowed)'
status: done
theme:
release:
created: 2026-09-14T21:37:01Z
modified: 2026-09-15T21:28:00Z
closed: 2026-09-15T21:28:00Z
revision: 159a1869412ab347
blocks: []
related: [WLO-0050, WLO-0052, WLO-0056]
---

# Description

Owner review 2026-09-14: the day-grouped logbook does not scale (a header per entry when the mode is one weigh-in/day; unbounded list — full-history query + plain Column composition). Agreed IA:

1. **Weight surface — compressed History card.** One row per bucket, the further back the coarser: each day for the last 7 days, then 4 weeks (Mon–Sun), then 4 months, then quarter by quarter (cap 12 quarters). Bucket row: range label, weigh-in count (when >1), delta vs the previous bucket, end-of-bucket day's weight (lowest-of-day, the existing canonical). Empty buckets inside the range show "—" — gaps are data. Buckets older than the earliest entry are not rendered. Rows tap through to the full logbook.
2. **New full-page Logbook (f06/logbook).** Every verbatim entry, newest first, sticky month headers ("SEPTEMBER 2026 · 14 weigh-ins · −1.1 kg"), self-describing rows ("Sun 14 · 06:30 … 77.0 kg" — no month on the row, it lives in the header), windowed feed: latest 3 months, "Load earlier (N more)" appends older chunks; bounded kind-filtered queries + LazyColumn.
3. **Delete UX (WLO-0050) moves to the full logbook** — swipe-to-reveal on verbatim rows, release-gated, visible arm affordance, inline undo with visible countdown. Aggregate buckets are not deletable.
4. Tier constants are tunable; kg formatting stays until WLO-0052 globalizes units.
