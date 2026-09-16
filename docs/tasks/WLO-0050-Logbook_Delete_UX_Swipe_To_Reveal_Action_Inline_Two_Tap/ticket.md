---
id: WLO-0050
title: 'logbook delete UX — swipe-to-reveal action, inline two-tap confirm, inline undo'
status: done
theme:
release:
created: 2026-09-14T16:37:54Z
modified: 2026-09-15T21:28:00Z
closed: 2026-09-15T21:28:00Z
revision: b9cded7afd310dfb
blocks: []
related: [WLO-0051, WLO-0055]
---

# Description

Owner review of the WLO-0035 logbook delete, three defects:

1. **Delete fires with no confirmation** — one tap on the row's ✕ deletes the
   weigh-in outright.
2. **The undo notice renders at the top of the screen** — the user is looking
   at the logbook card when they delete; the banner lands a screen away
   (screenshot on record: banner above the hero, rows below the fold).
3. **The list-item action UI is wrong for M3** — actions belong on
   swipe-to-reveal (M3 lists guidelines), with the confirmation as a
   micro-interaction directly where the user is interacting — not a modal,
   not a page-level banner.

Rework:
- `WloSwipeRevealRow` in :core:designsystem — anchored horizontal drag
  reveals a trailing action; spring snap; reduced-motion aware.
- Logbook row: swipe reveals Delete → first tap arms (error morph + tick
  haptic, auto-disarm 3 s) → second tap confirms. No dialog, no page banner.
- Deleted notice renders inline at the deleted row's day group (synthetic
  group keeps the day label when the day emptied), Undo + Dismiss in place.
- Compose UI test: swipe → arm → confirm → inline undo restores the row.

Refs: WLO-0035, docs/features/F06-weight-body-metrics.md §5.
