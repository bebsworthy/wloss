## Context

R-B2 (FEATURES.md §3) froze *authority* over the Targets document — F01
creates v1, F07 writes adaptive adjustments only via explicit Apply, every
other feature reads — but the schema itself is still unsigned. F07 §10 calls
it "the app's single most politically loaded schema": every feature
(F02 rings, F03 generation, F05 context, F10 nudges, F11 grades) consumes
it, so field and semantics decisions ripple app-wide.

## Task

Author the ratified Targets contract:

- **Fields**: calorie budget + daily/weekly cadence, weekday/weekend
  schedule, macro splits, fiber (R-B3), water (logged by F02 per master
  §2.1), workout cadence, pace, calorie floor (F07 defaults 1,200 F /
  1,500 M, user-overridable with acknowledgment).
- **Versioning**: every edit is `vN+1` with a human-readable diff and
  one-tap revert (F01 pattern); check-in Apply writes through the same path.
- **Write API**: F01 create/edit; F07 Apply-only (atomic, ledgered,
  reversible); exercise expenditure structurally incapable of raising eating
  targets; floor validation non-negotiable in the write path.
- **Day-level projection**: the per-day resolved budget F02/F10 render
  (schedule expansion, cadence semantics).

## Acceptance criteria

1. Schema documented and referenced from FEATURES.md (location per open
   question below).
2. F01 §7 and F07 §7 boundary notes co-sign the contract.
3. Floor + no-eat-back enforcement specified as write-path validation, not
   convention.
