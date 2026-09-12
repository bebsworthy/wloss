# F13 — Data Vault & Integrations — Functional Specification

## Identity

| | |
|---|---|
| **Feature ID** | F13 — Data Vault & Integrations |
| **Provides** | The foundation under every feature: the on-device schema of record, attachment storage policy, versioned export/import with converters, validated auto-backup, app lock, Health Connect sync, Bluetooth scales, and the provable-privacy network story. |
| **User problems solved** | • "Mealime is shutting down (Oct 2026) and took my plans and history with it — never again." • "My smart scale's vendor app demands a cloud account to show me my own weight." • "I won't put body photos in an app I don't control." • "I'm switching from MFP/Lose It!/Paprika — how does my history come with me?" |
| **AI consent category** | none — F13 executes no AI calls. It owns the network gate, consent ledger, and audit surface that make "only F12-consented traffic ever leaves the device" architecturally true. |
| **Primary evidence** | `docs/research/waistline.md` (versioned JSON export with migration-on-import, secret-blanking, auto-backup, archive-don't-delete), `docs/research/openscale.md` (EAV measurement schema, staged-and-validated restore, ~68 scale drivers, "no internet permission"), `docs/research/happy-scale.md` (14-year local-first trust, Fresh Start, min-of-day import rule), `docs/research/hevy.md` (CSV export as trust), `docs/research/gyroscope.md` (one-tap full deletion), `docs/research/synthesis.md` §1.3, §3 (Mealime's cloud death as the portability wedge) |

## 1. Purpose & Core Objectives

F13 is the single source of truth under the user's absolute control; every other
feature is a tenant. It makes three promises enforceable by architecture rather
than policy: (1) your data outlives the app — readable from documented, open
formats without WLO; (2) your data stays put — nothing crosses the network
except explicit user consent, verifiable with a packet analyzer; (3) mistakes
are survivable — automatic backups, staged and validated restores.

Core objectives (verifiable):

- Phone-loss recovery: a fresh install restores 100% of data from the SAF
  backup folder, with a validation report before anything commits.
- Exports are importable by any future version after schema migration, and
  parseable by third-party tooling (documented format).
- A packet analyzer sees only F12 BYOK calls the user explicitly triggered and
  opt-in food-database lookups — no telemetry, no analytics; data leaves the
  device only through an explicitly consented capability.
- Every capture (photo, poop, silhouette record) lands in app-private storage,
  invisible to the gallery and OS cloud-photo backup, by default — and for
  silhouette, what lands is a vector record, never an image (R-U16).
- A corrupt or hostile import can never destroy existing data (staged import;
  openScale pattern).

## 2. User Moments — when and how it is used

- **Continuously (invisible):** every feature's reads/writes route through the
  vault; nobody "uses F13" during normal tracking.
- **Daily (fire-and-forget):** scheduled auto-backup; the F10 backup-health dot
  confirms it silently.
- **Weekly/monthly:** PDF exports — the journey report, and the F09 doctor
  export (the poop-tracker category's #1 real-world need).
- **Episodically:** device migration; importing from MFP, Lose It!, Paprika,
  or generic CSV; restore after loss; enabling a scale or Health Connect.
- **Rarely:** deletion, or a Fresh Start with F01 after a relapse.

## 3. How It Works — functional mechanics

**Schema (Room/SQLite, relational core).** Normalized entities: profiles,
days, food items, meals, recipes, plan entries, workouts/exercises/set-logs,
weigh-ins, body measurements, silhouette records, Bristol logs, targets,
check-in results, badge/streak ledger (F11), notification settings (F10). Every domain row carries a `profileId` (default profile created at onboarding — partition-ready per R-B9; per-profile vault partitions, locks, and backup bundles arrive with multi-profile [v1.x]). Food entries reference the catalog by ID with **archive-don't-delete**
semantics (Waistline's diary-by-reference: history stays honest when a product
is reformulated; items are archived, never orphaned).

- **EAV sidecar for custom metrics** (openScale's Measurement/Type/Value
  pattern): user-invented measurement types become first-class F11 series and
  CSV columns on export.
- **Provenance table** — every derived number records its formula version and
  inputs; the backbone of every "how we got here" explainer.
- **Attachments registry** — rows point to files; the registry, not the file
  system, defines sensitivity class (food photo / poop / silhouette record) so
  F11 share policies and the hidden gallery enforce consistently.
- **Consent ledger** — append-only record of every F12 toggle change and every
  consented network call, surfaced in the audit page.

**Attachments policy.** Photos (meals, poop) live in app-private
storage, excluded from the gallery/MediaStore and Android cloud backup by
default; OS backup of attachments is an explicit opt-in. Discreet mode (with
F08/F09) hides sensitive entries from the app switcher; originals are never
downscaled without retaining the original. **Retention follows R-U14:** food
and stool photos default to discard-at-save. **Silhouette generates no photo
attachments at all (R-U16):** F08's capture derives vector outlines in memory
and discards the frame — the vault holds records, never body images. Because
there is no Google-Photos-style cloud
overflow to bail storage out, F13 ships a **storage dashboard [v1]** —
per-category photo usage, one-tap cleanup (shrink to thumbnails, delete
photos older than N, per-category purge) — and a **user-folder photo offload
[v1.x]** that copies originals to the SAF backup folder so device space can
be reclaimed (the local-first stand-in for cloud overflow; private
Google-Drive offload remains [future]: complex and account-bound).

**Export formats.**

- **Versioned JSON bundle** — one document per store plus a `schema_version`
  header; import migrates older versions forward on import (Waistline's
  discipline). API keys, secrets, and BYOK keys are **blanked out** of every
  export (Waistline's `BACKUP_KEYS` hygiene). Format documented in-repo so
  third parties can build tooling — the anti-Mealime guarantee.
- **CSV per metric** — one file per measurement type; custom EAV types become
  columns (openScale 3.1.3 pattern).
- **PDF reports** — the generic journey report (trend charts, milestones,
  silhouette strip if opted in) and the F09 doctor export (Bristol history,
  meal correlation, fiber context) rendered fully on-device.

**Import & restore.** Same-version JSON restores directly; older versions
migrate forward. **Generic CSV/JSON import ships [v1]** — a column-mapping
wizard that remembers mappings (R-S4); **competitor converters — MyFitnessPal
CSV, Lose It! CSV, Paprika recipe export, Mealime — land [v1.x]** (R-S4). All
imports land in a **staging area**, produce a validation report
(rows parsed / skipped / warnings), and commit atomically or not at all — a
corrupt file can never merge garbage into live data (openScale's
staged-and-validated restore).

**Auto-backup & security.** Android SAF folder chosen once; scheduled
WorkManager writes `wlo_backup_<date>.json` plus an integrity manifest;
write-new-then-rotate keeps the last N (default 7); backup health surfaces on
F10, and a week without a successful backup raises one respectful nudge.
Photo attachments are **excluded from bundles by default** (explicit
per-bundle opt-in — R-U18, matching R-U14's posture).
Security: biometric app lock (BiometricPrompt, PIN fallback) with a
configurable timeout; optional attachment encryption [v1.x].

**Health Connect.** Two-way sync with per-datatype consent (weight, body
composition, steps, exercise, nutrition). HC is single-profile per device:
in v1 the sole profile owns the connection (R-B9). WLO-authored records win on WLO
surfaces; multi-source weight deduplicates with the min-of-day import rule
(Happy Scale's semantics) so smoothing stays deterministic.

**Bluetooth scales.** A driver layer for BIA scales. *(Owner amendment
2026-09-11, R-S1/R-S13: WLO is Apache-2.0, so openScale's GPLv3 driver code
cannot be reused directly; drivers are clean-room implementations of popular
scale protocols instead.)* WLO still **credits openScale prominently in
About → Scale support and links upstream** — the protocol knowledge exists
because openScale reverse-engineered it. Values carry provenance
(vendor-decoded vs. formula-estimated, cited — openScale computes this but
never shows it; WLO surfaces it).

## 4. User Interaction Model

**Entry points.** Settings → Data Vault; the F10 backup-health dot; the F01
onboarding "coming from another app?" path; notification after a failed backup.

**Primary flows.**

1. *Device migration:* old phone → export to SAF folder → new phone → install
   → "Restore found a backup" prompt → validate → preview diff → apply.
   Happy path ≤3 minutes.
2. *Importer wizard:* pick source (MFP / Lose It! / Paprika / CSV) → file →
   column mapping (remembered) → staged validation report → commit.
   *Fallback:* unknown format → generic mapper; unparseable rows are skipped
   with reasons, never fatal.
3. *Restore from corrupt file:* validation fails → explicit "nothing was
   changed" state with error detail and last-known-good backup offered.
   Data loss by import is structurally impossible.
4. *Scale pairing:* Settings → Scales → scan → pair → step on → live capture
   with a haptic tick per arriving metric.

**Input minimization.** Backup setup is one folder pick; imports remember
column mappings; scale pairing is a single dialog; deletion requires only a
typed confirmation phrase. Nothing in F13 ever asks for a login.

**Micro-interactions.** Backup success: the vault glyph morphs to a check for
800 ms — silent, no toast spam. Restore validation: staged progress with
per-stage ticks and a "validated" stamp on pass. Biometric unlock: fast 200 ms
fade, no splash theater. Export: the file icon materializes with a spring and
offers the share sheet. Deletion: type-to-confirm with an explicit count of
what will be erased ("3,412 entries · 187 photos"), then a clean empty state
with a "Fresh Start" hand-off to F01.

**Data-quality gating.** Importers never silently coerce: ambiguous columns
and unit mismatches (lb vs. kg) are flagged per row in the validation report;
Health Connect sync pauses with a visible state when a datatype consent is
revoked.

## 5. What the User Gets Out

- A documented, versioned JSON bundle; CSVs per metric; PDF journey and F09
  doctor reports — all readable without WLO.
- Automatic, rotated backups to a folder the user owns (point it at
  Syncthing/Nextcloud-backed storage and user-owned sync comes free).
- One-tap migration from the competitors people are fleeing.
- Health Connect bridging and Bluetooth scale auto-capture, with provenance
  badges on every ingested value.
- The **storage dashboard** [v1]: per-category photo usage and one-tap
  cleanup (shrink to thumbnails, age-based purge, per-category wipe) — the
  space-management answer to photos-default-off (R-U14).
- The **network audit page** [v1.x]: every egress path, its consent state, and
  last-used timestamp — "provable privacy" as a screen, not a blog post — plus
  provenance records powering every "how we got here" explainer app-wide.

## 6. Motivation & Psychology

Trust is the retention engine: Happy Scale held a 4.89 rating for 14 years on
"your data is on your device"; openScale's "no internet permission" is the
strongest privacy claim in the category because it is *structural*. F13 makes
WLO's version visible inside the product — the audit page, backup-health dot,
and export formats are trust surfaces, not settings cruft. Portability is the
wedge the category handed over: Mealime's October 2026 shutdown stranded users
mid-plan, and WLO's answer is "your planner can't die — your data was never
ours." Deletion psychology follows Fresh Start: hide-not-delete for relapses,
with F01 owning the ritual and F13 the data mechanics.

## 7. Relations to Other Features

- **Consumes from:** every feature's domain writes (F01–F09 data); F12 (consent
  states to enforce at the network gate, BYOK keys to protect).
- **Feeds into:** F11 (query layer, custom EAV metrics, provenance); F10
  (backup-health dot, widget persistence); F06 (Bluetooth scale ingestion);
  F02 (food-DB offline cache storage); F09 (doctor PDF); F01 (Fresh Start,
  multi-profile, onboarding import); F08/F09 (attachment sensitivity classes,
  hidden gallery).
- **Shared concepts:** Provenance, Consent, Logs, Profiles, Fresh Start.
- **Conflict/boundary:** F13 owns storage, formats, transport, and enforcement;
  it owns no domain logic and no AI. F12 owns which AI calls *may* happen; F13
  makes sure nothing else can. F01 owns the Fresh Start *ritual*; F13 its data
  mechanics (hide/restore/purge).

## 8. Blue Sky Ideas

- **[v1] Versioned JSON + CSV + PDF exports** with migration-on-import and
  secret-blanking (Waistline's contract, documented in-repo).
- **[v1] SAF auto-backup with staged-and-validated restore; biometric lock;
  attachments policy; Health Connect consent; Bluetooth scales with openScale
  credit; deletion + Fresh Start mechanics.**
- **[v1] Generic CSV/JSON import** — column-mapping wizard that remembers
  mappings (R-S4).
- **[v1.x] Competitor converters** — MFP, Lose It!, Paprika, Mealime (R-S4).
- **[v1.x] Network audit page** — every egress path with consent state and
  last-used time; plus a bundled self-test packet-capture recipe so geeks can
  verify the (near-)empty capture themselves.
- **[v1.x] Encrypted backups** — passphrase-derived (AES-GCM) bundle option;
  keys never leave the user and never enter exports.
- **[future] User-owned multi-device sync** — two installs pointing at one
  Syncthing/WebDAV folder; CRDT-merged, E2E-encrypted, still no WLO server.
- **[future] Signed exports** (ed25519; restores prove integrity) and a
  **plain-SQLite export mode** with documented schema for power users.
- **[moonshot] Journaled vault** — append-only mutation log: time-travel
  ("show my data as of June 1"), diffs between any two exports, undo of any
  past import; git-like history for a health app.
- **[moonshot] Community converter registry** — open-source, locally executed
  import filters for any tracker's export, curated in-repo.

## 9. Guardrails, Privacy & Sensitivity

- **What a network-traffic-analyzing user would ever see leaving the device:**
  1. BYOK AI calls to the user-chosen provider endpoint, only while that
     capability's F12 consent is on (e.g., a food photo's bytes to their own
     OpenAI-compatible endpoint). TLS to the provider; no WLO endpoint exists.
  2. Food-database lookups (Open Food Facts / USDA FDC) when enabled: barcode
     numbers and search strings only — no user identifiers, no telemetry.
     *(Consent placement: see §10.)*
  3. Nothing else. No analytics, no ads, no crash reporting with content, no
     CDN/font/time-API fetches. Health Connect and Bluetooth are local radios,
     invisible to a network capture. Enforcement is architectural: all HTTP
     egress flows through one module behind the consent gate, unreachable from
     feature code that hasn't declared a consented capability.
- Attachments never leave app-private storage without the matching F12 consent
  *and* an explicit per-action share; exports include attachments only when
  the user opts in per bundle.
- Deletion is real deletion: in-app undo window, then purge; backups offer
  "purge from backups too" explicitly. Fresh Start hides rather than deletes.
- Hard invariants: free forever, no account, no SaaS, no WLO backend; open
  documented formats forever; secrets never exported; no non-consented
  telemetry/analytics SDK — every egress requires an explicit consent grant
  (R-S13).

## 10. Open Questions

- **WLO's license:** openScale's scale drivers are GPLv3; reuse requires a
  GPL-compatible license decision (GPLv3 adoption vs. clean-room drivers).
  *(Re-resolved 2026-09-11, owner via WLO-0014: Apache-2.0 per amended
  R-S1 — scale drivers are clean-room reimplementations; see R-S13.)*
- Food-database (OFF/USDA) network consent: inside the F12 matrix as a seventh
  capability, or a separate F13 "integrations" toggle? Master doc should
  resolve; either way it is off-capable, cached, and audited.
  *(Resolved: R-C4 — an F13 integration toggle, not an F12 AI category;
  default on, cached, per-lookup audit trail.)*
- Backup encryption default: on with a generated passphrase (lockout risk) or
  opt-in (plaintext-backup risk)? Rotation count; attachments inside bundles
  by default or per-bundle choice?
  *(Encryption and rotation resolved: R-U5 — encrypted by default with a user
  passphrase and a lockout warning at setup; plaintext is an explicit
  per-export choice; rotation default 7. Attachments-in-bundles resolved by
  R-U18: excluded by default, per-bundle opt-in.)*
- Health Connect nutrition write granularity: kcal/macros only, or full
  micronutrient coverage where the DB has it?
  *(Resolved: R-S9 — kcal/macros at v1; micronutrients when the food DB
  supports them.)*
- Multi-profile: how do profiles partition the vault, backups, and Health
  Connect (which is single-profile per device)?
  *(Scope ruled: R-B9 — single-profile v1 with `profileId` on every row;
  partitioning, locks, and backup separation land with multi-profile [v1.x].)*
