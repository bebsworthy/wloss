## Context

FEATURES.md §6 item 7, echoed in F06 (per-profile stores + biometric lock),
F08 (per-profile archives, independent locks), F09 (caregiver logging),
F13 (vault partitioning, backups, Health Connect). The hard external
constraint: Health Connect is single-profile per device. F06 §8 [future]
household auto-detection and F01 §8 [future] household plan harmonization
both assume this lands eventually.

## Task

1. Owner call: does v1 ship multi-profile at all, or single-profile with a
   partition-ready schema (recommended)?
2. If partition-ready: define the profile key in the F13 schema (every
   domain row carries profile id), per-profile vault partitions + backup
   bundles, per-profile biometric lock, and the Health Connect/
   Bluetooth-scale binding model (which profile owns the device's HC
   connection; openScale-style weight-signature routing as [future]).

## Acceptance criteria

1. Scope ruling recorded in FEATURES.md §3 (new R-*).
2. F13 §3 schema + §7 relations updated with the profile model.
3. HC single-profile constraint handled explicitly (documented limitation
   or routing rule), not discovered at implementation time.
