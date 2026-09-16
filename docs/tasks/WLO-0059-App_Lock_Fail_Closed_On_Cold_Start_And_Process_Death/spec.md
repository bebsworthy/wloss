# Problem

`AppLockController` starts unlocked and relies on an in-memory background timestamp. Process death loses it, while `MainActivity` initially treats the persisted enabled setting as false. An enabled lock can therefore fail open or flash app content on cold start.

# Scope

- Model startup posture explicitly (`unknown/locked/unlocked`) and fail closed while enabled state is unresolved.
- Define cold-start behavior for each timeout without relying on process memory.
- Ensure secure-window flags are set before sensitive content can compose.
- Preserve graceful behavior when biometric/device credential is unavailable.

# Acceptance

- With lock enabled, force-stop/process-death relaunch never renders WLO content before authentication.
- Delayed DataStore emission cannot expose content.
- Immediate/one-minute/five-minute background behavior remains tested.
- Disabled lock does not add a startup prompt.

# Evidence

WLO-0057 report, P0 app-lock finding.
