# Objective
Offer advanced smoothing controls without dominating the weight surface or adding a state-management subsystem.

# Prerequisites
WLO-0053 and evidence from dogfooding that multiple methods or alpha tuning are useful.

# Scope
- Keep the primary surface on one trusted default; put advanced method/alpha controls behind a math/advanced disclosure.
- Use a Material 3 single-choice pattern for mutually exclusive methods.
- Add Reset and label every non-default result as a preview.
- Recompute from already-loaded samples, or on slider release; do not query repositories per drag frame.
- Keep canonical stored/default results distinct from temporary preview state.

# Out of scope
Persistent custom smoother profiles, compare overlay (WLO-0078), and a cache/dispatcher architecture solely for slider traffic.

# Acceptance
The default is always one action away, preview values cannot masquerade as canonical values, an empty chart exposes no controls, and TalkBack can identify choice and value.
