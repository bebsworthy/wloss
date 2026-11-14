# M6 — Vault + AI platform shell

## Objectives
1. Phone-loss recovery: fresh install restores 100% of M2–M5 data from the SAF backup
   folder with a validation report before commit.
2. Open, documented export formats (JSON bundle + per-metric CSV) that re-import after
   schema migration — the "anti-Mealime guarantee" demonstrable.
3. The consent architecture becomes user-visible: ledger, receipts, kill switch, model
   manager — with zero receipt rows in normal use (nothing egresses without consent).
4. Security hardening: biometric lock, FLAG_SECURE on archive/gut-capture surfaces,
   per-file AES-GCM vault partition (used by photo retention from M4).

## Contents
- `:core:vault`: Keystore-wrapped partition keys, per-file AES-GCM, opaque filenames,
  MediaStore exclusion, storage accounting queries (T-C3 design decided here → ADR-008).
- Auto-backup: `BackupScheduler` port → WorkManager impl; `wlo_backup_<date>.json` +
  integrity manifest; write-new-then-rotate (default 7, R-U5); passphrase KDF decision
  (Argon2id vs PBKDF2 → ADR-008); backup-failure notification; photo attachments
  excluded by default (R-U18).
- Restore: staged-and-validated (parse → validate → atomic commit-or-nothing), versioned
  migration-on-import via `:core:documents`; Fresh Start interplay (R-B7).
- Export bundle v1: JSON (documented in-repo) + CSV per metric (EAV types → columns);
  BYOK keys + consent secrets blanked; import wizard with column mapping (generic CSV).
- F12 shell: six-category consent rows (R-C1) reading/writing the M2 ledger; global kill
  switch; receipt log viewer (hash-chain verification UI); model manager deep link from
  M4; point-of-use consent sheet component (payload preview) — ready for cloud calls.
- ACRA spike: endpoint options research (no WLO backend), content-freeing pipeline,
  consent toggle — decision recorded, implementation optional this milestone.
- BiometricPrompt gate + lock timeout + FLAG_SECURE per F13 §3.

## Relevant documentation
- `docs/features/F13-data-vault.md` (§3 all, §4 flows, §9 hard invariants)
- `docs/features/F12-ai-platform.md` §3.1, §3.4, §3.6, §4, §5
- `docs/design/` onboarding/vault-related flows; `docs/design/IA.md` §6 (settings and privacy)
- Rulings: R-U5, R-U7 (discretion), R-U18, R-C7 (no nag when consent off), R-B9
- DECISION-SPACE: T-C3, T-C6, T-F1–F4, T-K4, T-G3; ARCHITECTURE §2.4 (documents pattern)

## Acceptance criteria
1. Install → onboard (M2) → generate M3–M5 data → backup → uninstall → reinstall →
   restore: byte-identical logical state, validation report shown, commit atomic
   (corrupt-backup test: hostile file rejected, existing data untouched).
2. Export → wipe → import round-trip passes CI fixtures for every document/schema
   version shipped so far.
3. Consent toggles + kill switch mutate only the ledger; receipts viewable with intact
   hash chain; with all consent off, the debug egress monitor shows zero connections.
4. Vault partition: photo-retention files exist only as opaque encrypted blobs; not
   visible in gallery/pickers; excluded from backups by default.
5. Biometric lock gates `wlo://archive/capture` and settings vault surfaces.
