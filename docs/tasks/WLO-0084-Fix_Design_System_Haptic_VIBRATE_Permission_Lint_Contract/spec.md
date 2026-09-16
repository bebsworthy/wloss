# Objective
Make the haptic implementation's permission contract explicit so `:core:designsystem:lintDebug` and aggregate lint pass without suppressing a real runtime requirement.

# Scope
- Resolve `WloHaptics.kt:121 MissingPermission(VIBRATE)` using the least-privilege Android approach.
- Verify the manifest/merged-manifest contract and runtime behavior on supported API levels.
- Do not add broad lint suppression unless the permission is proven unnecessary and the reasoning is documented locally.
- Add a focused regression/static check if practical.

# Acceptance
Design-system and aggregate relevant lint pass; haptics still degrade safely on devices without capability; manifest/privacy documentation remains accurate; architecture and formatting gates pass.


## Implementation evidence (2026-09-16)

- Declared the normal install-time `android.permission.VIBRATE` contract in the design-system manifest, where the vibrator-backed implementation lives.
- Replaced the deprecated service lookup and retained capability detection plus `View.performHapticFeedback` fallback when no vibrator is present.
- Verified the merged API 29 package reports `VIBRATE` granted and cold-launches without a fatal exception.
- Design-system lint, aggregate lint, ktlint, detekt, and architecture checks pass without a permission suppression.
