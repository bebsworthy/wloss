# Objective
End a successful weigh-in with a trustworthy, emotionally neutral result.

# Prerequisites
WLO-0051, WLO-0053, and WLO-0071.

# Scope
- Build confirmation only from the persisted event and recomputed canonical trend.
- Show trend as hero, raw reading as supporting data, and an honest sparse-history fallback.
- Show delta in the active unit; upward movement is neutral, never failure copy.
- Emit one soft haptic exactly once after a successful commit.
- Return cleanly to the Weight home/Hub per WLO-0068.

# Acceptance
Failed/cancelled saves yield no confirmation or haptic; recomposition cannot repeat the haptic; kg/lb and sparse-data cases are tested; TalkBack announces the result in a useful order.
