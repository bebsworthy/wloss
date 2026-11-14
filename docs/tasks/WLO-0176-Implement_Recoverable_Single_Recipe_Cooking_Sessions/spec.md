# Implement recoverable single-recipe cooking sessions

## Source of truth

- [Functional specification](../../features/F03-recipe-creation.md).
- [Technical specification](../../tech/WLO-0167-recipe-implementation.md).
- [Accepted mockup](../../design/flows/12-recipe-creation.html), visual direction only; use native M3 and current DESIGN-SYSTEM/IA.
- WLO-0167 tracks the completed specification. This ticket tracks implementation and remains TODO until picked up.

## Outcome

Add native Start/Resume cooking with pinned revision and batch scale, reversible ingredient checks, current-step tracking and foreground-only screen-awake behavior.

## Dependencies and ownership

Blocked by WLO-0157, WLO-0160, WLO-0170. WLO-0157 owns the existing ingredient/content/nutrition foundation; WLO-0160 owns catalog normalization and revision semantics. Build on their delivered primitives without duplicating or reversing those dependencies.

## Requirements

- Implement profile-scoped persisted sessions with one active session, pinned recipe/content/note snapshot, batch scale, stable checked ingredient/step IDs and scroll anchors. Start/replace/end must be transactional and revision-aware.
- Expose Start/Resume, ingredient check-offs and explicit current-step state using standard M3. Empty directions or missing yield/nutrition do not prevent following the original batch.
- Preserve progress across navigation, rotation/background and process recreation. Recipe edits/archive leave the session on its pinned revision. Explicit restart is required for a new revision or changed batch scale after progress.
- Use Android lifecycle-aware keep-screen-on only while the cooking route is foreground/resumed; release on exit/background/end/profile switch. No background CPU wake lock or foreground service for reading recipes.
- Scale only supported quantities from the snapshot; vague amounts and time/temperature/pan size remain unchanged with review cues. Ending clears active placekeeping but creates no diary, pantry deduction or asserted last-eaten event.
- Own session Room/vault migration and merge handling, preserving original content offline and resolving the one-active-session invariant without dropping other saved state.

## Acceptance and evidence

FR-05, FR-08, FR-09; AC-07, AC-09, AC-12; technical §§3, 9–11; T-01, T-07, T-09, T-10.

- Verify rapid reversible checkmarks, step/scroll recovery, process death, revision/note pinning, explicit session switch/restart, archive during cooking and corrupted-session recovery.
- Verify actual screen-awake lifecycle on Android, TalkBack/current/completed semantics, 200% text and focus preservation; ending changes no food/plan/pantry records.
- Round-trip active sessions offline, including merge collisions and missing catalog packs.
- Include this slice's migrations, serialization/restore and focused tests in the implementation; they are not postponed to final integration QA. Record commands and material limitations.

## Scope boundary

Separate follow-on increment: no named timers, multiple active dishes, hands-free navigation, counted components or automatic logging. Do not enable in the current weight-first release.

Frozen feature/release/consent rulings remain authoritative. WLO-0159 source-ranking/carbohydrate questions stay there; do not fabricate answers or expand network permissions to make this ticket pass.
