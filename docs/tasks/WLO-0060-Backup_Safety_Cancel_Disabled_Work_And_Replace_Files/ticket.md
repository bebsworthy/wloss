---
id: WLO-0060
title: 'Backup safety: cancel disabled work and replace files atomically'
status: done
theme:
release:
created: 2026-09-15T18:20:51Z
modified: 2026-09-15T19:22:17Z
closed: 2026-09-15T19:22:17Z
revision: f031f598b90eb1f4
blocks: []
related: [WLO-0057]
---

# Description

Completed backup-disable and verified-replacement safety.

- BackupScheduler now exposes one suspend setEnabled boundary. OFF persists the fail-closed policy before awaiting cancellation of the unique WorkManager job.
- Enable awaits WorkManager enqueue; enqueue failure or caller cancellation rolls policy back to OFF and cancels uncertain work under NonCancellable cleanup.
- BackupWorker re-reads persisted policy on every run, skips writes while disabled, retries transient policy/backup failures for exactly three attempts, propagates cancellation, and posts terminal failure only when notification permission allows it.
- BackupControlsViewModel and VaultDashboardViewModel delegate scheduling/policy coordination to BackupScheduler.
- SAF replacement creates a distinct, listable temporary document, closes and byte-verifies it, and only then retires the predecessor. Pre-retirement failures clean the candidate without touching the known-good file. Once retirement begins, failures keep the verified candidate because providers may mutate before reporting an error. Rename cancellation is propagated rather than swallowed.

Focused failure-injection coverage:
- BackupScheduleReconcilerTest: OFF-before-cancel ordering, cancel failure, enqueue rollback, enqueue cancellation cleanup, and missing destination.
- ScheduledBackupRunnerTest: disabled already-enqueued work, successful run, retry/final-failure boundary, policy-read fail-closed behavior, and cancellation propagation.
- VerifiedReplacementWriterTest: create/write/read/delete failures, strict replacement order, distinct handle/name, unsupported rename, and cancellation during write/rename.

Verification:
- `./gradlew :core:vault:jvmTest` — PASS (79 tests, 0 failures).
- Focused backup tests plus `:core:vault:compileAndroidMain` — PASS.
- `:feature:f13-vault:compileDebugKotlin :app:compileDebugKotlin` — PASS.
- `./gradlew :core:vault:ktlintCheck` and scoped `git diff --check` — PASS.
- Targeted detekt reports no WLO-0060 findings; whole-module detekt remains red on pre-existing unrelated findings.
