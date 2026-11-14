# Objective
Make one authoritative weight-first release contract before product implementation branches further.

# Decisions to record
- Launch promise and minimum raw weight → trend → goal loop.
- Loss-only versus loss/maintenance/gain support.
- Weight as a primary destination versus a weight-centered Hub.
- First-run sequence: unit, optional goal, import/manual first weigh-in; diet planning becomes optional later.
- Release 1, release 2, and deferred capabilities.

# Docs to reconcile
`objective.md`, `FEATURES.md`, F01/F06/F07/F13, design system, IA, flows 01/04/07, and relevant research. Correct kg/lb/st, global/profile unit, multi-profile, silhouette retention, plateau, Health Connect identity, and trend-gate contradictions.

# Acceptance
One documented launch journey and capability matrix; no contradictions among governing docs; every open weight ticket can point to a supported goal mode, navigation, and release boundary.


# Implementation evidence (2026-09-16)

- Recorded loss, maintenance, and gain as first-class Release 1 goal modes.
- Made Weight the default primary destination; Hub remains the optional broader day surface and More holds later-suite destinations.
- Defined one launch journey: kg/lb selection, optional goal, then Health Connect/file import or manual first weight; diet planning no longer gates first run.
- Added the Release 1 / Release 2 / deferred capability matrix to the governing master feature document.
- Reconciled objective, F01/F06/F07/F13, IA, design system, research synthesis, and flows 01/04/07.
- Resolved unit policy (kg/lb only, one app-wide preference, Settings-only changes), single-profile scope, vector-only silhouette retention, plateau language, Health Connect durable source identity, versioned daily-scalar semantics, and cold-start versus measured forecast gates.
- Verification: contradiction grep clean for superseded claims; `git diff --check` passes; all relative Markdown/HTML links in the 12 touched product/design documents resolve locally.
