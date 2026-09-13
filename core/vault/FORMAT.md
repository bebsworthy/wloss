# WLO on-disk formats — backup (`.wlo`) and export (`.json`), v1

Normative for: `:core:vault` (M6, WLO-0028 PART A). House rules:
ADR-004 (versioned documents). Feature authority: F13 §3. Third parties can
build tooling against this file — that is the point (the anti-Mealime
guarantee, F13 §1).

## 1. The two artifacts

| | Auto/manual BACKUP | EXPORT |
|---|---|---|
| Name | `wlo_backup_<yyyy-MM-dd>.wlo` | user-chosen |
| Outer form | binary container (below) | plain JSON |
| Readable without WLO | with the passphrase (JSON inside) | immediately |
| Rotation | write-new-then-rotate, keep 7 (R-U5) | none (user-managed) |
| Receipts | excluded (device-local audit) | excluded |
| Photo attachments | excluded by default (R-U18 opt-in) | excluded |
| Integrity | per-section sha256 + total manifest, INSIDE the envelope | none (plaintext) |

## 2. `.wlo` container layout

All integers are big-endian. Offsets are sequential (no padding).

```
size  field
4     magic "WLOB" (0x57 0x4C 0x4F 0x42)
1     container formatVersion (0x01)
1     kdfId: 0x01 = Argon2id, 0x02 = PBKDF2
1     saltLen (16)
s     salt
12    kdf params:
        Argon2id: memoryKiB int32 | iterations int32 | parallelism int32
        PBKDF2:   iterations int32 | reserved int32 (0) | reserved int32 (0)
1     nonceLen (12)
12    AES-GCM nonce
4     ciphertextLen int32
n     ciphertext || GCM tag (128-bit), AES-256-GCM over the JSON document
```

- Key = the KDF over the user's passphrase and salt (Argon2id defaults:
  m=32768 KiB, t=2, p=1, 32-byte key — BackupKdf KDoc has the mobile
  justification). The KDF params live in the HEADER so a reader can derive
  before decrypting; a wrong passphrase fails the GCM tag — a clean error,
  never garbage.
- WHY a binary wrapper around JSON: header-driven decryption + magic-byte
  hostile-file rejection + a stable place for future format versions. The
  document INSIDE is the readable, documented JSON (§3).
- The container bytes are all that ever leaves the device (SAF folder the
  user owns). The passphrase is never stored; scheduled runs use a
  Keystore-wrapped COPY of the derived key that is device-bound (see
  AutoBackupKeyVault KDoc) — a stolen backup file is still passphrase-only.

## 3. The backup document (the decrypted bytes)

UTF-8 JSON, kotlinx-serialization canonical form (`DocumentCodec` house
rules: explicit `schemaVersion`, defaults for new fields,
`ignoreUnknownKeys`, migration funnel):

```json
{
  "format": "wlo-backup",
  "createdAtEpochMs": 1760000000000,
  "schemaVersion": 1,
  "manifest": {
    "sections": {
      "profiles": { "sha256": "<hex>", "rows": 1, "schemaVersion": 1 }
    },
    "totalSha256": "<hex over every section, in section order>"
  },
  "sections": { "profiles": [ ...rows... ], "settings": {"values": {...}} }
}
```

Section order (hashing + FK-safe commit order): `profiles, measurements,
diary, targets, provenance, consent_ledger, food_items, recipes,
grocery_items, plans, plan_slots, list_items, pantry_items,
aisle_corrections, settings, documents, vault_blobs`.

Row shapes are the `*Row` data classes in `app.wlo.core.vault.BackupSchema`
— kotlinx `@Serializable`, field names are the contract. Notables:

- `measurements[].attrs` is the EAV sidecar (F13 §3), nested per event.
- `diary[].revisions` carries the full correction chain (R-B8).
- `targets[].documentJson` is the untouched versioned Targets document
  (R-B2) — its own schemaVersion envelope travels inside the string.
- `consent_ledger` rows carry the hash chain; restore appends only when the
  chain continues the local head.
- EXCLUDED by ruling: `network_receipts` (they audit THIS install's egress —
  not user data), `day_records` (derived cache; recomputed on restore),
  `food_search` (FTS mirror; rebuilt), photo attachment blobs (R-U18; the
  opt-in `vault_blobs` section appears only when a bundle explicitly
  includes them).

Schema history: **v1** (M6) — first version. Migration hops register in
`BackupMigrations`; every shipped version keeps a decode fixture in CI
(ADR-004 rule 4).

## 4. The export bundle (plain JSON)

```json
{
  "format": "wlo-export",
  "exportSchemaVersion": 1,
  "exportedAtEpochMs": 0,
  "secrets": { "byokKeys": "blanked (F13 §3: ...)" },
  "sections": {
    "<section>": { "schemaVersion": 1, "count": N, "data": [ ...same rows... ] }
  }
}
```

Secrets: BYOK keys never exist in the database (F12 §3.3) and settings keys
matching sensitive prefixes are stripped at the store layer — the export's
`providers`-class structure, when it appears in later versions, ships values
as `""`. The invariant is test-pinned.

## 5. Metric CSV (per-metric export/import dialect)

RFC-4180, CRLF, header row:

```
day,time,weight_kg,trend_kg,kcal_in,kcal_out,body_fat_pct,custom/<name>_<unit>
2026-09-12,07:12:00,84.2,,,,,
```

- One row PER EVENT (R-B8: raw points ship verbatim — multiple same-day
  weigh-ins are multiple rows).
- Custom EAV metrics become `custom/<name>_<unit>` columns (F13 §3).
- Import: map columns → targets via `CsvColumnMapping` (R-S4 wizard API);
  `day` accepts ISO dates, `yyyy/MM/dd`, or bare epoch days; unparseable
  cells are skipped WITH reasons, never coerced (F13 §4).

## 6. Restore contract (F13 §4 flows 1/3)

stage (decrypt → verify manifest → migrate → typed decode → REPORT, zero
writes) → user reads the staged report → commit (ONE Room transaction,
insert-or-ignore reconcile: local rows win on PK collision, nothing is ever
deleted — R-B7) → derived views recompute. Any failure before commit leaves
the stores untouched by construction.
