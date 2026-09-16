# WLO-0094 implementation evidence

## Shipped behavior

- Each logbook row exposes a standard Material 3 overflow `IconButton` and
  `DropdownMenu` for Edit, Explain, and Delete. Equivalent TalkBack custom
  actions and the existing release-gated swipe route dispatch the same state
  holder commands.
- Edit uses the repository's atomic replacement path, freezes its draft while
  saving, rejects duplicate saves, retains failed drafts, and detects a stale
  or removed source event.
- Logbook state distinguishes loading, ready, unfiltered empty, filtered empty,
  and read failure. `HistoryRange` is half-open (`startInclusive`,
  `endExclusive`), including September 1 while excluding October 1.
- Undo is a single Scaffold snackbar receipt. Material 3 owns its recommended
  accessibility timeout; there is no parallel delay or countdown animation.
  Only one delete can be pending.
- Before deletion, the complete event and sidecar attributes are persisted in
  the app-private `weight/logbook-delete-recovery-v1/<profile>` document. It is
  reconciled on recreation, excluded from event export, bounded to one record,
  and removed after explicit dismissal/expiry or successful restore. Restore
  replay is idempotent.

## Automated verification

Passed:

```text
./gradlew :feature:f06-weight:testDebugUnitTest :core:data:jvmTest \
  :core:database:jvmTest :core:designsystem:testDebugUnitTest \
  :app:testDebugUnitTest

./gradlew :feature:f06-weight:ktlintCheck :feature:f06-weight:detekt \
  :core:data:ktlintCheck :core:data:detekt \
  :core:designsystem:ktlintCheck :core:designsystem:detekt
```

Added regression coverage proves recovery-document round trip and retirement,
complete pre-delete snapshot capture, sidecar restoration, and idempotent
restore replay. Existing repository, feature, design-system, database, and app
unit suites remain green.

## Manual verification

Physical TalkBack focus traversal, extended accessibility timeout behavior,
background/foreground timing, and swipe/haptic feel were not run because no
disposable emulator or physical test device was attached. These checks remain
explicitly unverified; no real health data was used.
