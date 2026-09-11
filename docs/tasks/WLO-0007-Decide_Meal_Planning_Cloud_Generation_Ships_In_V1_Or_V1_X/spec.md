## Context

FEATURES.md §6 item 10 — the only product-call left on the v1 critical
path's edge. F01's cloud template refinement and F03's cloud plan/recipe
generation are fully specced with consent, payload preview, and deterministic
fallbacks; both features work end-to-end without them. It is also the first
BYOK showcase (F12's value proposition made concrete).

## Task

Owner decides: ship `meal-planning` cloud generation in v1 or v1.x.

- v1.x (recommended): deterministic engine carries v1; cloud refinement
  joins receipt-proven BYOK flows after launch; no consent-surface work on
  the v1 critical path.
- v1: differentiating demo from day one, at the cost of F12 payload-preview
  + consent-sheet polish landing before launch.

## Acceptance criteria

1. Ruling recorded in FEATURES.md §3 as a new R-* (scope ruling).
2. F01 §8 / F03 §8 blue-sky tags updated to match.
3. FEATURES.md §6 item 10 removed from the open list.
