# Objective
Offer record-low psychology as an explicit alternative view without replacing the canonical trend.

# Prerequisites
WLO-0053 and WLO-0072.

# Scope
- Define the 10-day window, boundary, tie, missing-data, source-event, and outlier rules.
- Keep trend as the default; persist the user's explicit headline preference.
- Label the value and its source/window so it cannot be mistaken for canonical trend.
- Define honest behavior for maintenance and gain modes.

# Acceptance
A low outlier cannot silently become the canonical answer; sparse and timezone cases are deterministic; Hub and Weight agree; preference and accessibility tests pass.

## Product decision (2026-09-16)

Canceled by owner. WLO keeps one canonical trend headline. A best-of-window number would cherry-pick favorable readings, amplify outliers and repeated-weighing incentives, and has no coherent meaning across loss, maintenance, and gain goals.
