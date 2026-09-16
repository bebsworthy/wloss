# Problem

Turning automatic backup off does not cancel existing periodic work, and the worker does not recheck the persisted policy. Same-day replacement also deletes the known-good document before the new backup has been written and validated.

# Scope

- Give the existing `BackupScheduler` boundary cancel/set-enabled semantics.
- Cancel unique work on OFF and make the worker fail closed when policy is disabled.
- Move scheduling coordination out of the ViewModel into the existing backup-control owner.
- Write a unique new/temp SAF document, close and validate it, then retire the superseded file.
- Preserve cancellation and guard failure notifications by notification permission.

# Acceptance

- OFF guarantees no scheduled backup write, including already-enqueued work.
- A provider/create/write/process failure cannot delete the last known-good backup.
- Failure-injection tests cover disable, retry, cancellation, and replacement order.

# Evidence

WLO-0057 report, backup-control and replacement findings.
