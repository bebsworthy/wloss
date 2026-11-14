# M2 — Data spine + onboarding

## Objectives
1. Implement the persistent spine: event-level storage (R-B8) with day-level rendering,
   provenance per derived number, partition-ready `profileId` (R-B9).
2. Versioned documents with one migration funnel (Targets per Appendix A; DietPlan,
   Recipe).
3. F01 onboarding: zero-account, <3 min, zero network at any step.
4. F07 Transparent engine + mandatory cold-start forecast (R-A5) with 3 decelerating
   bands (R-A6) from a formula BMR.

## Contents
- Room 3 entities/DAOs: profiles, measurement events (EAV sidecar), day records,
  provenance table (formula version + inputs), consent-ledger table (schema only —
  consumers arrive in M6), food-item catalog stub (archive-don't-delete), Targets
  versions.
- Day-projection read API (cached day scalars; consumers never parse schedules).
- Targets two-writer API: `TargetsWriter` implemented by F01-studio and F07-apply only;
  invariants of Appendix A.2 enforced in the write path (floors, no-eat-back, pace cap).
- Document machinery: `schemaVersion` envelope, defaults + ignoreUnknownKeys, pinned
  discriminators, transforming-serializer migrations, CI round-trip + old-version
  fixtures.
- F01 UI per spec §3: preference quiz (R-S6), template pick/adapt, schedule bars;
  deterministic constraint→field applier; templates as plain JSON in the shipped schema.
- F07 Transparent engine: closed-form TDEE on rolling 14-day window, 7,700 kcal/kg
  constant from the versioned constants registry (R-A1/A3); cold-start bands from
  formula-BMR with ESTIMATED provenance chips; decision ledger + Apply-only writes
  (skeleton).

## Relevant documentation
- `docs/features/F01-onboarding-diet-plans.md` (full), `docs/features/F07-energy-engine.md` §3
- `docs/features/FEATURES.md` §2.1 (spine concepts), Appendix A.1–A.3
- `docs/features/F13-data-vault.md` §3 (schema shape, archive-don't-delete)
- `docs/tech/adr/ADR-003` (Room 3 house rules), `ADR-004` (document rules)
- Rulings: R-A1, R-A2 (EWMA default — engine constant now), R-A3, R-A5, R-A6, R-B1,
  R-B2, R-B3, R-B8, R-B9, R-S6, R-S10, R-U13, R-D8 (not yet UI-relevant), R-D10

## Acceptance criteria
1. Onboarding completes offline on the emulator in <3 min; killing the app mid-flow
   resumes or restarts cleanly.
2. Ending onboarding writes Targets v1 (via the writer API only) and the Hub placeholder
   shows a real 3-band forecast with ESTIMATED provenance chips.
3. Golden tests: forecast bands for 5 scenario profiles; Appendix A.2 invariant tests
   (floor, pace cap, no-eat-back) pass and are property-tested.
4. One deliberate schema change demonstrates the migration harness (exported schema +
   migration test in CI).
5. Zero egress: debug egress counter stays at zero for the whole milestone.
