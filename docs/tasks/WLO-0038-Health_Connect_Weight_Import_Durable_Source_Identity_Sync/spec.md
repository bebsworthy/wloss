# Objective
Import and incrementally synchronize Health Connect weight/body-fat records without conflating source identity with WLO's derived daily scalar.

# Scope
- Add the Android Health Connect client behind the existing F13/port boundary with availability and permission states.
- Persist immutable source identity: Health Connect record ID, data origin, client record ID/version when present, recording method, instant, zone offset, metric, and canonical SI value.
- Use source identity/version for insert/update/delete synchronization and a durable change cursor. Never deduplicate by lowest-of-day or a fuzzy time/value window.
- Preserve every distinct source event under R-B8; compute minimum/other daily-scalar policies only in derived reads.
- Show import counts, skips, updates, deletions, conflicts, permission revocation, and retry state in an import log.
- Begin with manual import/sync; background scheduling is separate.

# Tests
Availability, permission denial/revocation, initial and incremental sync, retry, update/delete, duplicate delivery, cross-source same-time records, kg/lb source conversion, timezone travel, and process restart.

# Acceptance
Replaying a cursor is idempotent; updates do not create duplicates; deletions are reflected without destroying unrelated events; provenance survives export/restore; F06 and Hub show the same recomputed answer.
