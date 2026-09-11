# Tech-stack research phase

Ground zero: **docs/tech/DECISION-SPACE.md** — the full inventory of decisions (T-A…T-K),
the already-frozen choices (§2), non-negotiable constraints (§1), owner questions (§5),
and the research order (§6).

## Scope
Research every open decision T-A1…T-K6, produce one-page ADRs in `docs/tech/adr/`, then
ratify as R-T* rulings in FEATURES.md §3. Design continues in parallel (WLO-0013 /
design tree) — this phase does not touch docs/design.

## Research order (§6)
- **Phase A — foundation forks:** T-A2 (KMP posture) → T-A3 (minSdk) → T-C1/T-D1/T-H1
  (persistence, serialization, HTTP).
- **Phase B — AI/capture stack:** T-E1 runtime matrix → T-E2…E5, E7, E8 per-capability
  models (license/size/benchmark) → T-E6 local LLM. Output includes a min-spec spike
  (tokens/s, inference latency).
- **Phase C — security & documents:** T-F1–F4, T-G1, T-C3 vault design.
- **Phase D — quality & release:** T-J testing/perf tooling, T-K build/CI/flavors/
  reproducible builds, crash policy (Q6).

## Hard filters for every candidate (§1)
GPLv3 · F-Droid-clean ("WLO Pure" flavor, no Play-Services-only SDKs) · no backend ·
airplane-mode parity · single NetworkDispatcher egress · provenance chips · R-U14/U16
photo discipline · model cards with named open training data.

## Acceptance criteria
1. Every T-* decision has an ADR with options, evidence, choice, consequences.
2. Owner questions below answered (or defaulted with explicit rationale).
3. R-T* rulings merged into FEATURES.md §3.
4. Min-spec device spike numbers recorded for the model zoo.


## Amendment 2026-09-11 — owner policy reframe (R-S1 amended, R-S13 added)
- F-Droid: **not required** → Play Store + GitHub APKs (Q1/Q2 resolved).
- License: **Apache-2.0** (was GPLv3). openScale GPL scale drivers → clean-room reimpl.
- Proprietary SDKs allowed when free + materially better: on-device ⇒ audit card only;
  transmitting ⇒ explicit consent toggle + receipts via NetworkDispatcher.
- "WLO Pure" no-INTERNET flavor: **dropped**.
- Hard line unchanged: no data leaves the device without explicit consent.
- Proprietary SDKs now default candidates: ML Kit Barcode/Text (T-E8/T-E4);
  Gemini Nano/AICore joins the local-LLM tier (T-E6); iText/AGPL out of PDF (T-G1).
