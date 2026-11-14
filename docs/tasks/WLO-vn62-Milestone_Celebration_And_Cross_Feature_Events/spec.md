# Objective
Add milestone celebration only after the progress ladder proves useful.

# Prerequisite
WLO-rwfh.

# Scope
- Persist exactly-once milestone events using WLO-rwfh's canonical-trend crossing definition.
- Handle restart, backfill, edit, delete, goal revision, reversal, and skipped rungs without spurious replay.
- Add restrained celebration UI/haptic respecting reduced motion and disabled haptics.
- Emit a stable event contract for F11 without moving milestone ownership there.

# Acceptance
Exactly-once behavior survives recomputation and restart; historical edits do not fabricate celebrations; accessibility and reduced-motion variants are verified.
