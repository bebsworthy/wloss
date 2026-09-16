# Objective
Add one understandable progress signal: a ribbon comparing the canonical trend with a fixed 30-day reference.

# Prerequisites
WLO-0053 chart correctness and WLO-0072 validated canonical series policy.

# Scope
- Define the ribbon geometry and sparse-data behavior mathematically.
- Use accent only for progress toward the active goal; neutral treatment for movement away or maintenance ambiguity.
- Fixed 30-day reference for the first release; no scrubber until comprehension is validated.
- Apply active mass units, reduced motion, TalkBack summary, and the same answer on Hub and Weight where shown.
- Test loss, gain, maintenance, insufficient history, gaps, outliers, and goal revision.

# Out of scope
10-day-best (WLO-0076), weigh-time analysis (WLO-0077), compare modes (WLO-0078), and alpha controls (WLO-0054).

# Acceptance
The band never implies progress without a valid goal/reference, never hides uncertainty, and its geometry is covered by pure tests and screenshot checks.

## Product decision (2026-09-16)

Canceled by owner as a feature. Remove the shifted reference line, shaded band, goal coloring, animation, scrubber, and settings. A separate cleanup ticket may expose only the useful fact as neutral text: 30-day trend change, shown only when sufficient history exists.
