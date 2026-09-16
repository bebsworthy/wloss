# WLO-0099 weight UX validation

Validation date: 2026-09-16 (Europe/Paris)  
Code under test: `a1c8de0` plus this report-only change  
Host: macOS arm64; offline/local test data only

## Release decision

**Not run — release gate remains open.** All listed host-side unit, lint,
static-analysis, build, and architecture checks pass after fixing two findings
discovered by this validation. The API 29 instrumentation result is recorded
below. A current-API device, manual TalkBack/keyboard journeys, responsive and
200% font matrix, physical haptics, and comparative frame trace are still
required. No critical automated data-integrity failure was observed.

## Configuration and fixtures

The device leg uses disposable AVD `wlo-api29`, Android 10/API 29,
1080×2400 px at 420 dpi, with Android Test Orchestrator package-data clearing.
No current-API AVD was available. Host tests use their fixed clocks, IDs,
timezones and synthetic repository fixtures; they cover the F1–F8 data shapes
through the implementation-ticket suites. F0/F9 and the complete pairwise
display/input matrix require the manual device run and remain **Not run**.

Required pairwise intersections still outstanding: 320dp+200%+IME for each
form; TalkBack+delete/Undo; animation-scale-0+Undo; process death during
correction/import/onboarding; mixed methods+accessible chart; and wide
resize+dirty editor. kg/lb conversion, comma parsing, transaction rollback,
same-day selection, backup/restore, mixed methods, and lifecycle state have
automated coverage, but that does not prove their visual/assistive behavior.

## Commands and results

| Command | Result |
|---|---|
| Required JVM/unit suite from WLO-0099 | Pass |
| `./gradlew :app:assembleDebug checkArchitecture` | Pass |
| `./gradlew ktlintCheck detekt` | Pass after validation fixed WLO-0098 line-length/suppression findings; no behavior change |
| `./gradlew :app:connectedDebugAndroidTest` on `wlo-api29` | **Fail:** 78 tests, 37 passed, 41 failed |

## Review traceability A01–A18

“Not run” means automated evidence exists but the required manual/device
portion is absent; it is deliberately not promoted to Pass.

| Review item | Implementation/evidence | Status |
|---|---|---|
| A01 truthful daily selection/provenance | WLO-0088; core data policy/integrity tests | Not run — manual consumer parity pending |
| A02 transactional correction | WLO-0089; repository and ViewModel reliability tests | Not run — process-death journey pending |
| A03 derived/chart truth | WLO-0088/0092/0098; geometry and ViewModel tests | Not run — TalkBack chart journey pending |
| A04 section/body state | WLO-0090; state and body persistence tests | Not run — rotation/device journey pending |
| A05 M3 primitives/theme | WLO-0091; contrast/static checks | Not run — gallery/pressed/focus inspection pending |
| A06 dashboard continuity | WLO-0089/0092; state tests | Not run — visual journey pending |
| A07 shared controls/settings | WLO-0091/0097; lint and unit tests | Not run — assistive inspection pending |
| A08 overview/history hierarchy | WLO-0092; screen/state tests | Not run — compact/large visual run pending |
| A09 labeled/recoverable forms | WLO-0093; form state tests | Not run — 200%+IME inspection pending |
| A10 goal/setup safety | WLO-0093/0097; safety/onboarding tests | Not run — end-to-end device journey pending |
| A11 logbook edit/delete/Undo | WLO-0094; repository/state tests | Not run — TalkBack+timeout intersection pending |
| A12 body methods/units | WLO-0090/0093; body and conversion tests | Not run — mixed-method visual inspection pending |
| A13 import/Health Connect | WLO-0095/0097; parser/vault tests | Not run — picker/permission journeys pending |
| A14 restore/Fresh Start safety | WLO-0095; vault staging/recovery tests | Not run — device restore journey pending |
| A15 navigation/adaptive layout | WLO-0096; navigation unit tests | Not run — 599/600/839/840 resize run pending |
| A16 reminder/settings | WLO-0097; scheduler/state tests | Not run — platform permission/channel run pending |
| A17 accessible chart/motion | WLO-0098; geometry/selection tests | Not run — TalkBack, scale 0, frame trace pending |
| A18 full release matrix | This report | Not run — outstanding matrix above |

## Inventory traceability W01–W17 / S01–S09

| Surfaces | Owner/evidence | Status |
|---|---|---|
| W01 setup | WLO-0097 onboarding tests | Not run — manual handoffs pending |
| W02–W08 overview, capture, confirmation, outlier, trend, math, history | WLO-0088/0089/0092/0098 unit tests | Not run — manual responsive/TalkBack run pending |
| W09–W11 logbook, edit, delete/Undo | WLO-0094 state/repository tests | Not run — manual assistive timeout run pending |
| W12–W14 body segment, calculator, ratios | WLO-0090 tests | Not run — visual/unit/IME combinations pending |
| W15–W17 goal progress, editor, forecast | WLO-0092/0093 tests | Not run — held/maintenance/gain visual run pending |
| S01 Hub | WLO-0088 consumer parity tests | Not run — manual semantics pending |
| S02 Settings | WLO-0097 tests | Not run — 200% and permission return pending |
| S03 Profile | WLO-0093 tests | Not run — locale/IME visual run pending |
| S04 Health Connect | WLO-0097 state tests | Not run — platform integration pending |
| S05–S07 import/export/restore/Fresh Start | WLO-0095 parser/vault tests | Not run — SAF journeys pending |
| S08 Reminder | WLO-0097 scheduler tests | Not run — notification capture pending |
| S09 Adaptive shell | WLO-0096 navigation tests | Not run — resize matrix pending |

## Implementation-scenario traceability

Every defined scenario is assigned below. “Focused regression pass” means the
named host-level assertions pass; it does not claim the corresponding device,
process-death, assistive-technology, or visual journey has run.

| Scenario IDs | Evidence | Outcome |
|---|---|---|
| A88-01…A88-07 | WLO-0088 evidence; data/database/vault suites | Focused regression pass; consumer/device matrix pending |
| A89-01…A89-06 | WLO-0089 evidence; repository/ViewModel suites | Focused regression pass; process-death/device matrix pending |
| A90-01…A90-07 | WLO-0090 evidence; body/state suites | Focused regression pass; lifecycle/device matrix pending |
| A91-01…A91-06 | WLO-0091 evidence; contrast/static checks | Focused regression pass; visual/TalkBack pending |
| A92-01…A92-06 | WLO-0092 evidence; screen/state suites | Focused regression pass; visual/device pending |
| A93-01…A93-07 | WLO-0093 evidence; goals/profile suites | Focused regression pass; failure injection/locale/IME/TalkBack pending |
| A94-01…A94-07 | WLO-0094 evidence; logbook/repository suites | Focused regression pass; gesture/TalkBack pending |
| A95-01…A95-06 | WLO-0095 evidence; parser/vault suites | Focused regression pass; SAF/process-death device pending |
| A96-01…A96-06 | WLO-0096 evidence; navigation suites | Focused regression pass; adaptive device pending |
| A97-01…A97-07 | WLO-0097 evidence; onboarding/scheduler suites | Focused regression pass; permission/device pending |
| A98-01…A98-07 | WLO-0098 evidence; chart tests | Focused regression pass; A98-02/05/06/07 manual pending |

## M3 self-audit

Scores use 0=absent, 1=partial/limited, 2=demonstrated. Manual-only evidence
caps a category at 1 until observed.

| Category | Score | Evidence/limitation |
|---|---:|---|
| Theme/tokens | 2 | WLO-0091 contrast tests and semantic roles |
| Components | 2 | Standard Scaffold, ListItem, controls, sheets; documented Canvas exception |
| Component semantics | 1 | Automated semantics/static review; manual TalkBack pending |
| Layout/adaptation | 1 | Adaptive shell implementation; breakpoint/device matrix pending |
| Typography/density | 1 | Reflow implementation; 200% manual matrix pending |
| States/feedback | 1 | Explicit loading/error/retry/Undo states; device timing pending |
| Accessibility | 1 | Labeled controls/alternate chart list; manual assistive run pending |
| Motion/haptics | 1 | Documented/native scale policy; recordings and physical haptics pending |

No category is zero. Every remaining 1 is owned by WLO-0099's outstanding
device matrix.

## Open defects and limits

1. **P1 release-evidence gap:** no current-API device leg or manual TalkBack,
   keyboard, responsive, 200% font, and lifecycle matrix.
2. **P1 stale/incompatible instrumentation:** 41 API 29 failures. Most expect
   the pre-WLO-0096 Hub start route or removed duplicate Weight title tags;
   onboarding/resume failures also require triage before the gate can close.
3. **P1 performance-evidence gap:** no representative 60-second frame trace or
   prechange comparison.
4. **P2 physical-feedback gap:** haptics were not tested on physical hardware.

There are no known critical host-side data-loss, duplicate-write, safety-hold,
or architecture failures in this run. The device-suite failure is a release
blocker and this report is not authorization to close the gate.
