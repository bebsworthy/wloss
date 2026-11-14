---
id: WLO-psfc
was: WLO-0061

title: 'Restore/import: explicit confirmation and crash-recoverable multi-store commit'

status: done
closed: 2026-09-15T19:16:29Z
status_changed: 2026-09-15T19:16:29Z

theme:

release:

created: 2026-09-15T18:20:51Z

blocks: []

related: [WLO-sbnk]

parent:

rank:
---

# Description

Implemented WLO-psfc. Restore report Continue now advances to a separate confirmation step without invoking the vault; only Apply commits. Bundle restore persists the complete staged operation before mutation and serializes commit/recovery behind a mutex. Room is one transaction, settings and documents use atomic DataStore edits, projections roll forward, every phase is idempotently checkpointed, and cold start resumes any pending journal. Post-Apply failures use RECOVERY_PENDING copy instead of claiming no mutation. CSV imports now use deterministic IDs and one Room transaction, so failure/cancellation rolls back and retry/re-import is idempotent. Consent restore verifies a genesis-rooted chain, compares the shared prefix, preserves sequence zero, appends only a valid strict suffix, and holds actual forks. F13 contract documentation was updated.

Verification: focused StagedRestoreJvmTest + CsvImportCommitterTest (15 tests) PASS; :app:compileDebugKotlin + :app:compileDebugAndroidTestKotlin PASS; API 29 connected M6RestoreWizardTest#wipeAndRestoreThroughWizard_rebuildsSeedState PASS; scoped git diff --check PASS.

Deliberate contract: cross-store atomicity is not claimed; the durable roll-forward journal is the recovery mechanism.
