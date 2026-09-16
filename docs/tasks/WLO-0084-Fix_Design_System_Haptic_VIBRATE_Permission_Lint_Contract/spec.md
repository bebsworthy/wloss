# Objective
Make the haptic implementation's permission contract explicit so `:core:designsystem:lintDebug` and aggregate lint pass without suppressing a real runtime requirement.

# Scope
- Resolve `WloHaptics.kt:121 MissingPermission(VIBRATE)` using the least-privilege Android approach.
- Verify the manifest/merged-manifest contract and runtime behavior on supported API levels.
- Do not add broad lint suppression unless the permission is proven unnecessary and the reasoning is documented locally.
- Add a focused regression/static check if practical.

# Acceptance
Design-system and aggregate relevant lint pass; haptics still degrade safely on devices without capability; manifest/privacy documentation remains accurate; architecture and formatting gates pass.
