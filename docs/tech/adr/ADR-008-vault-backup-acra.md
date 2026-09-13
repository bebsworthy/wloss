# ADR-008: Vault partitions, backup encryption & crash diagnostics
**(decides T-C3, T-F2/T-F4, T-K4)**

**Status:** Accepted (WLO-0028 implementation, 2026-09-12; library versions verified
against Maven Central with artifact inspection — check dates in the version catalog;
decisions realized in `:core:vault` + `:core:network` and proven by the wipe-restore
E2E, `scripts/e2e-wipe-restore.sh`)

## Context

Three security/storage decisions converge in M6: how secrets-at-rest are protected
(T-C3 vault design), how passphrase backups are hardened (T-F2/T-F4 KDF), and how
opt-in crash reports can exist without a WLO backend (T-K4).

## Decisions

### 1. Vault (T-C3) — Keystore-wrapped partitions, per-file AES-GCM

- **Partitions are purpose-scoped** (`photo` first; gut/silhouette join later), each
  with its own AES key generated inside **AndroidKeyStore** (non-exportable); the
  partition key never leaves the keystore — files are encrypted/decrypted stream-wise.
- **Per-file AES-GCM** with a random 12-byte IV (IV prepended), **opaque filenames**
  (random UUIDs; the name→content mapping lives inside the partition), app-private
  storage, never registered with MediaStore. Storage accounting (bytes/count per
  partition) is a first-class query — R-S14's one-tap-reclaim pattern applies to
  retained photos too.
- Rationale: AndroidKeyStore already gives hardware-backed key protection at zero
  dependency cost; Tink was rejected (~2 MB facade for two primitives WLO already
  drives directly). Invariants from F13 §9 are enforced in code and tested (opacity,
  exclusion-from-backup R-U18, reclaim).

### 2. Backup encryption (T-F2/T-F4) — Argon2id via BouncyCastle

- Backups are passphrase-encrypted (R-U5): container = versioned document envelope
  (ADR-004 house rules) + integrity manifest (per-section SHA-256) + AES-GCM payload;
  on-disk format documented in `core/vault/FORMAT.md`.
- **KDF = Argon2id** (BC `bcprov-jdk18on` 1.86, lightweight API — no provider
  registration): m=32 MiB, t=2, p=1, 16-byte salt, 32-byte key. OWASP mobile-tier
  pick (~100–300 ms mid-range, no OOM risk on 2 GiB devices). **PBKDF2-HmacSHA256
  (600k iters) remains a first-class container `kdfId`** so the format is honest
  about its history and a fallback path exists.
- Keys are user-passphrase-derived and never stored; auto-backup convenience uses a
  keystore-wrapped key (`AutoBackupKeyVault`) — passphrase backup remains the
  recovery path of record (phone-loss test proves it).

### 3. Crash diagnostics (T-K4) — ACRA collect, WLO scrub + dispatch

- **ACRA 5.13.1 (`acra-core`, Apache-2.0) collects; WLO owns the wire.** A custom
  ReportSender reduces every report to a whitelist before it can leave: app version,
  Android version, generic phone model, and a **rebuilt stack trace where only
  `app.wlo.*` frames survive verbatim** (all other frames → `at <class>: <REDACTED>`).
  No logs, no configuration, no user data, no identifiers — the scrubbed JSON is the
  entire payload.
- The scrubbed report dispatches through `NetworkDispatcher` with the new
  **`EgressPurpose.DIAGNOSTICS`** (settings toggle, NOT a ConsentCapability — per
  q-000026), so the receipt ledger and audit discipline apply unchanged.
- **Endpoint: v1 = user-configured webhook (ships empty = unusable until set) with a
  documented GitHub-Issues recipe as the zero-infra default**; self-hosted ACRA
  collector stays the power-user option. **Send-once, never queue-and-retry** — a
  crash report is not worth a background egress loop.
- Toggle ships OFF (opt-in, content-free per q-000026). Implementation beyond the
  skeleton (`AcraReportSender`) is optional this milestone.

## Consequences

- "Your data outlives the app" is now provable: the wipe→restore E2E uninstalls the
  app and rebuilds logical state from the host-side encrypted backup.
- BC is a new first-party dependency (~5.7 MB jar; ProGuard keeps only the Argon2
  path) — revisit if/when Android ships a memory-hard KDF in javax.crypto.
- The DIAGNOSTICS egress purpose widens the dispatcher's enum; the fail-closed matrix
  and receipts cover it identically (zero receipts when the toggle is off).
