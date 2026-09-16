# Objective
Help users understand measurement conditions without rewarding repeated weighing or judging behavior.

# Prerequisites
WLO-0038 and WLO-0072.

# Scope
- Compute time-of-day distribution and weigh-count facts from raw events, not daily scalars.
- Handle source identity and timezone changes deterministically.
- Surface evidence-backed observations only; do not ship a placeholder warning.
- Use neutral language and avoid turning repeated attempts or the daily minimum into a success mechanic.

# Acceptance
Fixtures cover mixed sources, travel, repeat attempts, and sparse data; every insight can show its inputs; no unsupported causal claim is rendered.
