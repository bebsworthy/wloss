# WLO-0088–0099 implementation review — WLO-0101

Reviewed 2026-09-16 at **d41a60a**, comparing the twelve implementation commits with baseline **66fd2c5** and their full handoffs. Verdict: **changes requested; not ready for release or acceptance as complete**. There are **21 actionable findings (10 P1, 11 P2)**. No P0 was established.

The implementation makes useful progress: standard M3 cards/segmented controls, accessible light/dark foregrounds, atomic correction/body transactions, immediate section switching, a stable dashboard after save, visible logbook actions, bounded off-main-thread import reads, labeled Goals fields and a working four-destination shell. These gains do not fulfill the complete recovery, provenance, adaptive and accessibility contracts.

The highest-confidence reproductions are a 160 lb goal draft reopening as 352.7 lb, canonical/stored trend disagreement (80 versus 79 kg), mixed lb/kg chart presentation, disabled initial chart navigation, duplicate Goals titles/Up bypass, and expanded layouts that remain a stretched column. Source-confirmed failure/race findings below explicitly say when they were not injected on device.

## Scope and evidence discipline

All twelve ticket specifications, implementation diffs, relevant current source and implementation evidence reports were reviewed. The acceptance ledger below covers all 79 scenario IDs. Host checks ran successfully; Gradle reused up-to-date/cache results where applicable, so this is not represented as a forced fresh run of every assertion. A temporary focused regression test was executed and failed on the real Room repository as expected; its source/result are retained under evidence, and it was removed from the production test source tree afterward. No application source was changed.

The installed APK exactly matched the newly built debug APK by SHA-256 (`e35b6af9b92cbbecafa541405d0672dfc6bc61c97b66d4fb812474ef352062aa`). API29 visual/semantics checks used the existing `wlo-api29` emulator, synthetic fixture data, and adb/UI-tree-derived coordinates. No database clear, record save/delete/import or instrumentation data reset was performed. The goal draft entered for reproduction was discarded; mass unit, font scale, density and resolution were restored to kg, 1.0, 420dpi and 1080×2400. Window/selection/navigation state may differ from the initial display. Full TalkBack, current-API notification integration, physical haptics, exhaustive fault injection and a performance trace remain unverified here; lack of those is not evidence of correctness.

## Findings

### R01 [P1] Restoring a pounds goal draft converts it twice

Owner(s): WLO-0093 · [GoalsEditorViewModel.kt:675](/Users/boyd/wip/wloss/feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/state/GoalsEditorViewModel.kt:675)

**Reproduced on API 29.** Set units to lb, open Goals, enter 160, leave with toolbar Up, reopen Goals: target becomes **352.7 lb**. `displayWeight` converts the draft to kg, converts it back to display units, then passes that display value to `formatNumber`, which itself expects kg and converts again. This can block a valid goal via eligibility checks and presents a materially wrong proposed target. No goal was saved during the review.

**Fix:** pass canonical kg directly to `unit.formatNumber`; preserve canonical precision independently of formatted draft text. Add a real GoalsEditorViewModel restore test for lb→lb, kg→lb and repeated reopen, not just the current test of `toKilograms(fromKilograms(x))`.

**Retest:** 160 lb remains 160.0 lb after navigation and process recreation; switching to kg yields about 72.6 kg without changing the canonical target. Evidence: `04-goal-draft-160lb.png`, `05-goal-draft-reopened.png` and corresponding UI trees. A93-03/A93-06 fail.

### R02 [P1] The new daily policy leaves old persisted trends and projections intact

Owner(s): WLO-0088 · [WeighInRepository.kt:772](/Users/boyd/wip/wloss/core/data/src/commonMain/kotlin/app/wlo/core/data/WeighInRepository.kt:772) · [Migrations.kt:423](/Users/boyd/wip/wloss/core/database/src/commonMain/kotlin/app/wlo/core/database/Migrations.kt:423) · [RoomRepositories.kt:456](/Users/boyd/wip/wloss/core/data/src/commonMain/kotlin/app/wlo/core/data/RoomRepositories.kt:456)

**Reproduced with a focused Room regression test.** Seed the post-upgrade data shape: 07:00=80 kg, 18:00=79 kg, legacy persisted trend=79. Initialize the new policy and read canonical trend: **80.0**, but the persisted TREND row remains **79.0**. Migration 8→9 adds nullable columns; lazy initialization sets metadata only. No full rebuild, mixed-version gate, retryable migration state or policy-change notice exists. Writing a new reading repairs only the affected suffix, leaving older projections inconsistent. DayProjector continues consuming the stored TREND rows.

**Fix:** atomically or resumably rebuild all derived history at the policy transition before exposing the new version; retain raw events and make legacy backup restore use the same path. Persist the migration state/timezone at the intended boundary and implement the promised notice.

**Retest:** the included `ImplementationReviewRegressionTest.kt` must pass with canonical and stored values both 80.0, then add real v8 upgrade/rollback/restart and forecast/projection parity tests. Its captured assertion failure is in `policy-regression-result.xml`. This fixture tests post-upgrade consistency, not an actual v8 migration invocation. A88-04 fails.

### R03 [P1] Undo failure is followed by automatic destruction of its recovery copy

Owner(s): WLO-0094 · [LogbookScreen.kt:107](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/LogbookScreen.kt:107) · [LogbookScreen.kt:122](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/LogbookScreen.kt:122) · [LogbookViewModel.kt:566](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/LogbookViewModel.kt:566)

**Confirmed in the failure control flow; failure injection not run on device.** After restore fails, `restoreFailed` restarts the Snackbar effect. It still starts the ordinary expiry timer. Expiry dismisses the “Recovery copy retained” receipt and dispatches FinalizeDelete, which clears the durable snapshot. A transient restore failure therefore becomes permanent deletion after roughly the normal Undo interval, despite promising Retry. A second failed Retry does not change `restoreFailed` again, so the effect may not even present a new actionable receipt after the action dismissed it.

**Fix:** make restore failure a durable recovery state with no automatic finalization; use an attempt/generation state to reshow Retry after each failure. Only successful restore or explicit final-delete acknowledgement may clear it. Serialize restore/finalize so expiry cannot race an active restore.

**Retest:** fail restore twice, advance beyond the normal/accessibility deadline, background/recreate, then recover storage and Retry. The complete event and attrs must return exactly once. A94-05 fails.

### R04 [P1] Two fast deletes can overwrite the single recovery record

Owner(s): WLO-0094 · [LogbookViewModel.kt:498](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/LogbookViewModel.kt:498) · [LogbookViewModel.kt:527](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/LogbookViewModel.kt:527)

**Confirmed race in source.** The pending-delete guard runs before launching a coroutine, but `pendingDeletion` is not reserved until snapshot lookup, recovery-file write and database deletion have completed. Two actions before the first suspension resolves both pass the guard. Different events can both be deleted while the one per-profile recovery document is overwritten; repeated deletion of the same event can let the second failure clear the first operation's recovery. UI deletion is likewise disabled only after `deleted` becomes non-null.

**Fix:** reserve a busy/pending operation synchronously before launching work, serialize all delete/Undo/finalize transitions, and clear a receipt only when its operation ID matches. A snapshot and delete also need a coherent version check if the event changes between preparation and commit.

**Retest:** suspend `deletionSnapshot`, dispatch Delete(A) and Delete(B), then release it. Exactly one deletion may proceed and its receipt must remain recoverable. Also test two Delete(A) calls and concurrent Undo/dismiss. A94-05 fails; existing serialization tests do not exercise this ViewModel race.

### R05 [P1] Retrying first-run completion can duplicate the starting measurement

Owner(s): WLO-0097 · [WeightFirstOnboarding.kt:115](/Users/boyd/wip/wloss/feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/domain/WeightFirstOnboarding.kt:115) · [WeightFirstOnboarding.kt:128](/Users/boyd/wip/wloss/feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/domain/WeightFirstOnboarding.kt:128)

**Unimplemented required recovery behavior; the unsafe writer predates this change.** Completion still appends a new starting-weight event and only afterward writes goal intent, handoff, completion flag and draft removal. A failure or process death after append but before completion causes retry to append another event. WLO-0097 adds a handoff document but no operation token, transaction/journal or reconciliation for the profile/weight writes. Persisting navigation intent does not make completion idempotent.

**Fix:** create a durable completion operation before writing, reuse its profile/event identities on retry, and set completion only after durable reconciliation. Surface failures instead of letting document-write exceptions leave a busy UI.

**Retest:** inject failure after initial weight commit and after each document write, restart and finish again; one profile and one initial measurement must exist. A97-01/A97-02 remain unsatisfied. Do not mark these scenarios “automated pass” from draft serialization tests.

### R06 [P1] A known oversized file escapes the import error boundary

Owner(s): WLO-0095 · [ImportSourceReader.kt:50](/Users/boyd/wip/wloss/feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/state/ImportSourceReader.kt:50) · [ExportImportViewModels.kt:184](/Users/boyd/wip/wloss/feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/state/ExportImportViewModels.kt:184)

**Confirmed exception path; a hostile SAF provider was not exercised.** `queryMetadata` and the declared-size `require` run before the reader's try/catch. A provider reporting more than 20 MiB causes IllegalArgumentException; metadata access can throw SecurityException. ImportViewModel catches only ImportSourceException. These errors escape the launched coroutine instead of reaching the promised recoverable state and can terminate the app. The streaming-size check is wrapped correctly, so the result depends on whether the provider supplies SIZE.

**Fix:** put metadata lookup, size validation, directory/file setup and read inside the same typed exception boundary while preserving CancellationException. Ensure partial staging is cleaned in every path.

**Retest:** providers returning oversized declared size, unknown size with oversized stream, revoked metadata permission and open failure all produce an in-context error, zero app-data mutation and a working Choose another file/Retry. A95-03 fails.

### R07 [P1] Recreated body drafts reuse an already committed token for new values

Owner(s): WLO-0089, WLO-0090 · [BodyFatViewModel.kt:100](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/BodyFatViewModel.kt:100) · [BodyFatViewModel.kt:157](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/BodyFatViewModel.kt:157) · [RoomRepositories.kt:240](/Users/boyd/wip/wloss/core/data/src/commonMain/kotlin/app/wlo/core/data/RoomRepositories.kt:240)

**Confirmed lifecycle mismatch in source.** SavedStateHandle persists `operationId` and tape text, but not `committedOperationId` or a durable committed-result reconciliation. After saving and process recreation, the old operation token is restored while committed state is null. Editing then does not rotate the token. The next save returns the previous measurement bundle from the repository, while the ViewModel reports “Saved” using the newly calculated estimate. The new body measurement is silently absent.

**Fix:** persist/reconcile operation status and the exact submitted snapshot; after a committed operation, a deliberate edit must start a new operation. Also handle death after commit but before receipt. The same reconciliation principle is missing from weigh-in capture: an already-committed restored draft can be edited and resubmitted with its old token; repository replay returns the old reading instead of saving the edit.

**Retest:** save A, recreate with saved state, enter distinct B and save. Two intended bundles must exist and B's receipt must name its actual committed value; replay of unchanged A must not duplicate it. Exercise both body and weigh-in drafts. A90 lifecycle contract and A89-05 fail.

### R08 [P1] Chart navigation cannot be started through its advertised accessible controls

Owner(s): WLO-0098 · [WloTrendChart.kt:252](/Users/boyd/wip/wloss/core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloTrendChart.kt:252) · [WloTrendChart.kt:283](/Users/boyd/wip/wloss/core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloTrendChart.kt:283)

**Reproduced through the emulator accessibility tree.** On an unselected chart, both Previous and Next have `enabled=false`; they require a selection that their own actions cannot initiate. The Canvas exposes a description but no accessibility selection action. “View data” rows are static ListItems with no selection or Explain action. Consequently a screen-reader user can read values but cannot follow the required select→explain journey without a coordinate tap. Keyboard arrows work on the focused Canvas, but Home/End/Enter from the handoff are absent.

**Fix:** enable a first-selection action, expose selection and Explain directly in the standard data list, implement the specified keyboard actions and announce completed selection changes. Preserve focus on refresh.

**Retest:** start from no selection with TalkBack and keyboard only; select any sample, open its actual explanation, reach boundaries and return. Evidence: `imperial-unselected.xml`; actual TalkBack traversal remains unrun. A98-02 fails by semantics/source, independently of that manual gap.

### R09 [P1] CSV staging can publish a report for mappings that have already changed

Owner(s): WLO-0095 · [ExportImportViewModels.kt:317](/Users/boyd/wip/wloss/feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/state/ExportImportViewModels.kt:317) · [ExportImportViewModels.kt:355](/Users/boyd/wip/wloss/feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/state/ExportImportViewModels.kt:355) · [ExportImportViewModels.kt:305](/Users/boyd/wip/wloss/feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/state/ExportImportViewModels.kt:305)

**Confirmed asynchronous race in source.** Review starts a coroutine but leaves Mapping editable and Review enabled. The coroutine captures a mapping, suspends in `stageCsvImport`, then unconditionally publishes its report/token into whatever state exists when it returns. Change kg→lb during staging, or choose another file: the older report can still switch the wizard to Reviewing and provide a commit token for the old interpretation/source. Setting `staged=null` at edit time does not invalidate an in-flight result. Concurrent staging also shares one mutable pending batch in VaultPortAdapter.

**Fix:** give each source/mapping revision a generation, freeze or explicitly cancel Review work, and apply results only if generation/source/profile still match. Commit must bind the exact reviewed immutable mapping to the batch. Serialize adapter staging/commit or scope sessions independently.

**Retest:** block stage, change unit/source, release old result and assert it cannot enter Reviewing or commit. Review the new mapping and verify only its values are imported. A95-04 fails despite the added token check.

### R21 [P1] The release gate lacks working journey tests and overstates automated coverage

Owner(s): WLO-0099 · [WLO-0099-WEIGHT-UX-VALIDATION.md:97](/Users/boyd/wip/wloss/docs/tech/WLO-0099-WEIGHT-UX-VALIDATION.md:97) · [commands.md:33](/Users/boyd/wip/wloss/docs/tech/WLO-0099-evidence/commands.md:33)

**Confirmed from retained XML and current tests.** The previous device run contains 78 cases, 41 failures. Most stop at stale Hub-first expectations or removed `f06-title` before reaching the behavior being tested; onboarding has additional failures needing triage. There are no new end-to-end device tests in these commits exercising most added recovery contracts. The validation table nevertheless groups all scenario IDs as “Automated pass; manual pending”. A93-05 is backed by journal serialization, not failure-after-commit recovery; adaptive tests assert an unused breakpoint; import tests cover mapping validation, not ViewModel lifecycle/races. The defects above show these are missing implementations/test coverage, not exclusively unrun visual checks.

**Fix:** repair robots/selectors and changed contractual assertions, implement focused failing-path tests, then map each acceptance scenario to a specific assertion/manual artifact. Keep Fail distinct from Not run. WLO-0099 correctly remains doing and must remain a release blocker; completed implementation tickets need their acceptance status revisited.

**Retest:** relevant device suite green on disposable API29/current-API targets, explicit process-death/assistive/large-font intersections, and the named 60-second performance baseline comparison. This review did not rerun the 17-minute failing suite merely to repeat known setup failures. A99-01/A99-02/A99-03/A99-06/A99-07 do not pass.

### R10 [P2] Logbook and overview history still compute the old daily-minimum summary

Owner(s): WLO-0088, WLO-0092, WLO-0094 · [LogbookViewModel.kt:331](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/LogbookViewModel.kt:331) · [WeighInViewModel.kt:921](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/WeighInViewModel.kt:921)

**Confirmed in source.** Logbook `flushMonth` explicitly uses `dayEvents.minOf` and flags the lowest raw entry. Overview still sends raw readings to WeightHistory, whose daily canonical reduction is also minimum-based. For a first day containing 07:00=80 and 18:00=79 and a last day with 07:00=80, these summaries show a +1 kg change while the new selected daily values show 0. This is a left-unfixed cross-consumer requirement, not a defect in the new pure reducer.

**Fix:** obtain dailySelections for aggregate start/end/delta values, preserving raw count and raw list rows separately. Replace the lowest badge with a truthful selected/contributing label if needed, including a two-event median.

**Retest:** the same-day fixture must agree in overview buckets, month summary, canonical chart and explainers. A88-01/A94-06 aggregation contract fail.

### R11 [P2] Chart details ignore preferred units and expose raw Double precision

Owner(s): WLO-0098, WLO-0090 · [WloTrendChart.kt:352](/Users/boyd/wip/wloss/core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloTrendChart.kt:352) · [WeighInViewModel.kt:839](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/WeighInViewModel.kt:839) · [WeightScreen.kt:1022](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt:1022)

**Reproduced on API 29.** With lb selected, the hero/axes use lb while a selected point shows `77.30454545454546 kg`. Setting `displayUnit="kg"` bypasses the supplied `formatWeight` function and concatenates the raw Double. The data-list path does the same. Waist charts likewise remain cm in imperial mode. This defeats the unit contract and makes dense numeric UI visibly unfinished.

**Fix:** keep canonical unit separate from display formatting; use one unit-aware formatter for axes, details, lists and accessible descriptions, with appropriate precision. Translate waist to in when that is the active convention.

**Retest:** 80 kg displays about 176.4 lb consistently everywhere; no long floating-point tail appears; 101.6 cm displays 40.0 in. Evidence: `08-imperial-selection.png`. A98-01/A98-05 unit parity and WLO-0090 unit presentation are incomplete.

### R12 [P2] Body chart identity drops repeated same-day readings and provenance

Owner(s): WLO-0090, WLO-0098 · [WeighInViewModel.kt:969](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/WeighInViewModel.kt:969) · [WloTrendChart.kt:196](/Users/boyd/wip/wloss/core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloTrendChart.kt:196)

**Confirmed in source.** The repository returns event IDs, capture time, method version and formula inputs, but the UI maps them to a key containing only method label and day. Two same-day measurements using one method share the key; `distinctBy` removes one from the accessible list/selection while Canvas can still draw both. The mapping also drops sourceEventIds, capture time, method version and explanationInputs, so detail may claim zero sources and cannot explain the formula inputs.

**Fix:** use event ID as stable key and carry the full method-aware provenance through the UI model. Order same-day samples by capture time/ID and expose meaningful per-event explanation in View data.

**Retest:** two Navy measurements on one day plus RFM and unknown-method readings remain individually selectable/listed, with matching values and formula/source details. A90-06/A98-01 fail.

### R13 [P2] Goal recovery and conflict actions do not perform the promised reconciliation

Owner(s): WLO-0093 · [GoalsEditorViewModel.kt:220](/Users/boyd/wip/wloss/feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/state/GoalsEditorViewModel.kt:220) · [GoalsEditorViewModel.kt:393](/Users/boyd/wip/wloss/feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/state/GoalsEditorViewModel.kt:393) · [GoalsEditorViewModel.kt:486](/Users/boyd/wip/wloss/feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/state/GoalsEditorViewModel.kt:486)

**Confirmed in source; storage-failure UI injection remains unrun.** After Targets commits but safety metadata fails, the message says Retry to reconcile, yet the normal Save action creates a new journal and attempts another write using the stale base version; it does not resume the committed journal. Recovery occurs only during reload and matches document equality rather than a durable operation identity. “Review differences” only swaps baseVersion and displays a notice; it does not show the current values/diff before an overwrite. On recreation, a draft whose base version changed is silently filtered out instead of entering Conflict.

**Fix:** route Retry to journal completion, preserve committed operation identity, and present actual old/current/draft values for explicit conflict resolution. Restore stale-version drafts as conflicts, not missing drafts. Prevent pending journal state being replaced by a new ordinary Save.

**Retest:** fail metadata after vN+1, retry in place and recreate; exactly one target version with matching safety record. Concurrent change plus recreation must retain draft and show differences. Existing journal encode/decode tests prove serialization only. A93-04/A93-05/A93-06 remain incomplete.

### R14 [P2] Toolbar Up bypasses dirty/busy policies and titles are still duplicated

Owner(s): WLO-0093, WLO-0095, WLO-0096 · [WloApp.kt:190](/Users/boyd/wip/wloss/app/src/main/kotlin/app/wlo/app/navigation/WloApp.kt:190) · [GoalsEditorScreen.kt:100](/Users/boyd/wip/wloss/feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/ui/GoalsEditorScreen.kt:100) · [ImportScreen.kt:73](/Users/boyd/wip/wloss/feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/ui/ImportScreen.kt:73)

**Dirty-Up bypass and duplicate Goals title reproduced on API 29.** Goals intercepts system Back locally, but the shell toolbar navigates directly. Up exits a dirty form with no Keep editing/Discard prompt, and the same shell escape remains available while Goals saves or Import applies. Removing the import Close button does not gate toolbar/system Back. The shell now renders a title for every route while Goals, Profile and several F13 screens retain WloScreenTitle, so the “one title” acceptance is not fulfilled.

**Fix:** expose a screen exit policy to the shell and use it for Up, system Back and relevant navigation actions. Remove redundant content titles once the shell owns them; retain sheet headings. Busy operations must resolve/reconcile instead of appearing cancelled.

**Retest:** both Back affordances behave identically on dirty/saving/applying forms, and one visible title exists on every affected route. Evidence: goal before/after UI trees show two “Goals” titles and the Up→Settings transition without a dialog. A93-06/A95 Applying contract/A96-02 fail.

### R15 [P2] Supporting-pane and logbook list-detail layouts are not implemented

Owner(s): WLO-0096 · [WloNavigationPolicy.kt:39](/Users/boyd/wip/wloss/app/src/main/kotlin/app/wlo/app/navigation/WloNavigationPolicy.kt:39) · [WeightScreen.kt:147](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt:147) · [LogbookScreen.kt:98](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/LogbookScreen.kt:98)

**Reproduced at 1080dp window width.** The only reference to `useSupportingPane` in production is its definition. Weight uses width-independent single-column content; Logbook has no expanded list-detail branch. The implementation adds a rail and a unit-tested breakpoint helper, but the content simply stretches across the available width. A breakpoint unit test cannot prove an adaptive layout that is never called.

**Fix:** implement the specified primary/supporting panes and event-ID-based list/detail state, with compact fallback, minimum content widths and one editor instance across resize. Preserve filter/selection/draft through the transition.

**Retest:** 599/600/839/840dp and a genuinely expanded window show the correct structure, including dirty-editor resize. Evidence: `09-wide-1080dp.png` (2160px at 320dpi); earlier wide attempts were hardware-capped to 2160px at 420dpi and are not claimed as >=840dp evidence. A96-03/A96-04 fail.

### R16 [P2] Reminder UI can claim enabled when notifications cannot be delivered

Owner(s): WLO-0097 · [SettingsScreen.kt:204](/Users/boyd/wip/wloss/app/src/main/kotlin/app/wlo/app/ui/settings/SettingsScreen.kt:204) · [WeighInReminder.kt:80](/Users/boyd/wip/wloss/app/src/main/kotlin/app/wlo/app/notification/WeighInReminder.kt:80) · [WeighInReminder.kt:101](/Users/boyd/wip/wloss/app/src/main/kotlin/app/wlo/app/notification/WeighInReminder.kt:101)

**Unimplemented acceptance behavior, not merely missing device evidence.** The switch reads the saved preference only. There is no resumed-platform/channel availability state or repair action; the worker checks runtime permission but not app/channel blocking in its predicate. The schedule remains a fixed 24-hour interval computed from LocalDateTime, with no timezone/clock/DST reconciliation. These parts of the old implementation were not changed by the time-picker/copy work.

**Fix:** model requested versus effective state, refresh on resume, expose provider/system repair links and a scheduling failure state. Schedule/recalculate the next approximate local occurrence with a zone-aware policy and time-change handling. Retain inexact delivery without requesting exact alarms.

**Retest:** revoked permission, globally disabled app notifications and disabled channel must be distinguishable and recoverable; timezone/DST changes preserve the selected local-time intent. A97-05/A97-06 cannot be labeled implemented until these code paths exist.

### R17 [P2] Body inputs lose canonical precision and retain stale profile facts

Owner(s): WLO-0090 · [BodyFatViewModel.kt:173](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/BodyFatViewModel.kt:173) · [BodyFatViewModel.kt:122](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/BodyFatViewModel.kt:122) · [BodyFatViewModel.kt:322](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/BodyFatViewModel.kt:322)

**Confirmed in source.** Unit changes convert the current displayed string and round to one decimal, then use that rounded string as the next canonical input: 100 cm→39.4 in→100.1 cm. The ViewModel reads height/sex only during initialization; editing Profile and returning to the retained Weight/body ViewModel does not refresh or invalidate the computed snapshot. Both contradict the explicit “physical quantity remains unchanged” and profile-change invalidation requirements.

**Fix:** retain canonical inputs separately from rendered text and convert only for display; observe or explicitly refresh profile facts, increment the calculation revision, and invalidate prior estimates when relevant facts change. Persist the draft's display unit to interpret restored strings safely.

**Retest:** repeated cm/in switches preserve the canonical value within 1e-6; profile height/sex change makes Save unavailable until recalculation with new facts. A90-03/A90-04 fail beyond their existing single-conversion tests.

### R18 [P2] Correction rounds the original timestamp and changes its stored day bucket

Owner(s): WLO-0089 · [WeighInViewModel.kt:688](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/WeighInViewModel.kt:688) · [WeighInViewModel.kt:586](/Users/boyd/wip/wloss/feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/WeighInViewModel.kt:586)

**Confirmed in source.** CorrectFlagged formats the original capture instant in the current device timezone, uses that local date rather than original dayEpochDay, and truncates time to minutes. Save always reparses these strings even if only weight changed. A 07:00:45 reading becomes 07:00:00; after travel across a date boundary it can move to a different stored day. The original exact snapshot is retained but not used to preserve unchanged time, unlike the logbook edit implementation.

**Fix:** detect untouched date/time fields and pass original day/instant; only re-bucket when the user explicitly changes them. Apply the same stale-original checks used by logbook editing where appropriate.

**Retest:** change only kg on a second-precision reading before/after a timezone change and verify original timestamp/day are identical, with one atomic replacement. Include this in A89-02/A89-05 regression coverage.

### R19 [P2] Import process restoration loses the current mapping and commit phase

Owner(s): WLO-0095 · [ExportImportViewModels.kt:162](/Users/boyd/wip/wloss/feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/state/ExportImportViewModels.kt:162) · [ExportImportViewModels.kt:238](/Users/boyd/wip/wloss/feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/state/ExportImportViewModels.kt:238) · [ExportImportViewModels.kt:495](/Users/boyd/wip/wloss/feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/state/ExportImportViewModels.kt:495)

**Confirmed in source.** SavedStateHandle holds only staging path/name. Recreation starts from bytes and the sniffer/global remembered mapping, so unreviewed user edits are lost; sniffed columns override remembered corrections. No durable wizard phase or commit receipt is reconciled. A process death after database commit can therefore reopen as Mapping instead of showing that the import already happened. Stable row IDs limit some duplication, but they do not preserve the user's mapping choices or distinguish commit from readback failure. Raw staged bytes also live in cache, so they can disappear and require a clear recovery path.

**Fix:** persist versioned session source identity, profile, mapping revision, immutable reviewed mapping/token, phase and committed receipt. Recover the session rather than re-sniff it; preserve correct retry semantics and clean it on explicit discard/success.

**Retest:** edit a sniffed kg column to lb, kill before Review and after commit, recreate, and verify the exact chosen mapping plus truthful outcome. A95-01/A95-04 lifecycle contract remains incomplete.

### R20 [P2] The M3 contrast evidence omits meaningful forecast lines

Owner(s): WLO-0091 · [WloForecastCard.kt:459](/Users/boyd/wip/wloss/core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloForecastCard.kt:459) · [WloTheme.kt:37](/Users/boyd/wip/wloss/core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloTheme.kt:37) · [ThemeContrastTest.kt:22](/Users/boyd/wip/wloss/core/designsystem/src/test/kotlin/app/wlo/core/designsystem/ThemeContrastTest.kt:22)

**Measured from shipped colors.** The forecast's fast/slow boundary lines use AccentDim `#1F6B54` on dark Surface `#151A21`: approximately **2.74:1** (2.51:1 on SurfaceRaised). This misses the handoff's 3:1 target for meaningful chart boundaries. ThemeContrastTest covers many text pairs and outline but not this chart role, so “every active pairing” in the evidence is overstated. Labels/alternate data help interpretation but do not make a barely visible uncertainty boundary satisfy the specified visual target.

**Fix:** define a chart-boundary semantic role with sufficient contrast in both themes and test actual composites/containers, while keeping low-alpha fill decorative. Also reconcile the remaining direction-based delta accent with the handoff's neutral loss/maintenance/gain treatment; do not treat “green downward” as automatically suitable for every goal mode.

**Retest:** verify every essential line/point/boundary and text/container pairing actually used by Weight/Goals, with light/dark and grayscale screenshots. Existing primitive M3 delegation is a real improvement; this finding is about incomplete token coverage, not replacing it.

## Ticket-by-ticket disposition

“Reopen” below is a review recommendation, not a tracker mutation performed by this review. Implementation statuses have not been changed automatically. WLO-0099 is already doing.

| Ticket | What is demonstrably present | Acceptance blockers | Disposition |
|---|---|---|---|
| WLO-0088 | Pure deterministic window/median reducer, policy backup fields, new repository read contract | R02 migration/rebuild, R10 consumer parity; full explanation inputs still not surfaced | Reopen |
| WLO-0089 | Original retained until atomic replacement; busy guard; dashboard receipt | R07 committed-draft reconciliation, R18 timestamp/day preservation; assistive/lifecycle coverage | Reopen |
| WLO-0090 | Immediate section state, independent periods, atomic body bundle, invalidation after tape edits | R07 token lifecycle, R12 chart identity, R17 canonical precision/profile refresh | Reopen |
| WLO-0091 | Real M3 wrappers, sentence-case card/sheet roles, substantially better color pairs | R20 essential chart contrast; runtime states/200%/assistive evidence incomplete | Reopen remaining acceptance work |
| WLO-0092 | Chart before goal/history, direct goal/logbook callbacks, persistent dashboard | R10 history values, R11 chart detail presentation; receipt still adds a sizeable card above hero; full entry/held-state matrix unverified | Reopen integration work |
| WLO-0093 | Persistent labels/date picker, first weight-only Targets path, drafts/journal types | R01 imperial corruption, R13 recovery/conflict, R14 exit policy | Reopen |
| WLO-0094 | Visible actions, standard row content, durable recovery document and snackbar | R03 restore failure expiry, R04 race, R10 min summary; edit/filter recovery across process death incomplete | Reopen |
| WLO-0095 | Vertical mapping rows, explicit units, source staging, Fresh Start hidden | R06 exception escape, R09 stale stage result, R14 busy exit, R19 restored session | Reopen |
| WLO-0096 | Four functioning destinations, route metadata, bar/rail switch | R14 duplicate titles/exit policy, R15 missing panes; filter Clear state is not saved back to SavedStateHandle | Reopen |
| WLO-0097 | Time picker, time-neutral notification copy, durable handoff intent | R05 completion idempotence, R16 effective availability/local-time handling | Reopen |
| WLO-0098 | Tap selection, detail region, View data list, typed point fields | R08 accessible start/explain, R11 units/precision, R12 body event identity; keyboard/announcements/performance work missing | Reopen |
| WLO-0099 | Honest overall blocked verdict and retained failing suite report | R21 overly broad automated-pass claims; required matrix and performance evidence absent | Keep doing; release blocked |

## Further specification gaps to include in the fixes

These are acceptance gaps rather than additional independently ranked defects:

- WLO-0088 does not surface the captured policy timezone, version, source IDs and excluded-input reasons in a complete user-facing explanation. Invalid-only days return null with no exclusion record. A generic Math page/status word is not the promised per-value provenance.
- Logbook edit state and cleared/changed range are not written to saved state; only incoming range arguments are restored. `remainingForegroundMillis` exists in the delete receipt but is neither updated nor used. Navigation/recreation restarts a full timeout rather than reconciling remaining foreground time.
- Capture opens a correction with the original value and leaves the event safe on cancel, which is correct, but stale/recreated submitted drafts still need a durable committed-result query before editing.
- The Goals forecast renderer still parses the target with `toDoubleOrNull()` in `GoalsEditorScreen.kt:325`, while its ViewModel accepts comma decimals. An otherwise eligible `80,5` draft can silently lose its forecast. Use the shared DecimalInput parser at every UI/domain boundary and add a renderer parity test.
- Goals current weight now visibly renders a number, but its working provenance action was removed rather than implemented; forecast/ratio surfaces also need the per-value explanation audit.
- The shared `WloDeltaChip` still emphasizes downward change using primary/accent independent of goal mode. This is inherited behavior but within WLO-0092/WLO-0099's neutral-weight design scope. Resolve the old token rule and new handoff explicitly.
- WLO-0096's amended R-D2 still includes following sentences saying F06 is reached from Hub and Archive/Digestion remain tabs. Clean up this contradiction so future implementation agents do not inherit two navigation contracts.
- The source-level animation policy is restrained, but the promised motion/announcement behavior and measured frame trace were not demonstrated. Do not infer smoothness from compilation or one screenshot.

## Material 3 and ergonomic assessment

Standard component adoption is materially better. The remaining problem is not primarily “wrong corner radii”: data interpretation, exit policies, recovery and adaptive structure still fail the intended experience. The compact overview is readable at normal font, but substantial outlined containers give every section similar weight. At 200% the hero's change annotation wraps awkwardly; this is a refinement opportunity after correctness fixes, not an invented data-loss defect. Expanded screens waste context by stretching the same column.

| Skill category | Score (0–2) | Evidence |
|---|---:|---|
| Task clarity | 1 | Clear capture/goal entry points; import/recovery outcomes still unreliable |
| Information hierarchy | 1 | Chart moved up; duplicate titles and oversized repeated containers remain |
| Component semantics | 1 | Standard M3 controls, but chart selection/explanation remains inaccessible |
| Token discipline | 1 | Improved paired roles; actual forecast boundary excluded from contrast audit |
| Adaptive behavior | 0 | Required supporting/list-detail panes absent; expanded screenshot confirms |
| States and feedback | 0 | False body-save result risk, disappearing recovery copy, escaped import error |
| Accessibility | 0 | Required chart selection→explanation cannot be completed through exposed controls |
| Expressive restraint | 2 | No added count-up/confetti or decorative motion in reviewed changes |

This fails the skill's approval gate because states/feedback and accessibility contain confirmed failures. The 48dp target and standard-control principles follow [Android Compose accessibility guidance](https://developer.android.com/develop/ui/compose/accessibility/api-defaults). The required useful multi-pane structure is consistent with [Android canonical layouts](https://developer.android.com/develop/adaptive-apps/guides/canonical-layouts?hl=en) and [list-detail guidance](https://developer.android.com/develop/adaptive-apps/guides/list-detail). This review does not require a dependency upgrade to the latest adaptive library.

## Acceptance ledger — all 79 scenarios

**Pass (bounded)** means the stated isolated rule has direct automated evidence; it does not approve the parent ticket. **Partial / not verified** means some code or unit coverage exists but the complete scenario was not established. **Fail** means a concrete finding contradicts at least one required part; it can be source-confirmed without pretending a device fault was injected. Scenario text is summarized; the ticket spec remains authoritative.

| Scenario | Requirement | Review result | Evidence / next check |
|---|---|---|---|
| A88-01 | in one day, 07:00=80 kg and 18:00=79 kg produces daily 80, not 79, in every listed consumer | Fail | R10: consumers retain daily minima |
| A88-02 | only 12:00=80 and 18:00=82 produces 81 with both event IDs; a third 20:00=90 produces 82 with the middle event ID | Pass (bounded) | DailyWeightPolicyTest: odd/even median and permutation fixtures |
| A88-03 | 06:45=80 and 07:15=81 selects 80 | Partial / not verified | WLO-0088 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A88-04 | the upgraded database preserves raw row/attribute counts and content hashes; rerunning initialization changes nothing | Fail | R02: regression fails 80 vs 79 |
| A88-05 | replacement, deletion and Undo recompute from the earliest affected day and preserve earlier results | Partial / not verified | WLO-0088 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A88-06 | Goals current trend, BMI/ratios and forecasts show actual values with units, or an explicit held/unavailable state | Fail | Missing per-value explainers; R08/R12 |
| A88-07 | JSON backup/restore round-trip preserves timezone/version/attribution semantics | Partial / not verified | WLO-0088 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A89-01 | start with one flagged 120 kg event | Partial / not verified | WLO-0089 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A89-02 | correct successfully to 82 | Partial / not verified | WLO-0089 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A89-03 | fail at each repository mutation probe (raw write, attrs, original deletion, trend/projection rebuild) | Partial / not verified | WLO-0089 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A89-04 | block repository response, tap Save twice and press IME Done | Partial / not verified | WLO-0089 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A89-05 | rotate in Editing and Saving; recreate after commit but before receipt delivery | Fail | R07: no committed-draft reconciliation |
| A89-06 | a committed write followed by read failure is reported as saved with refresh Retry; Retry rereads and does not append | Partial / not verified | WLO-0089 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A90-01 | tap Body fat then Weight on a static repository | Partial / not verified | WLO-0090 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A90-02 | Weight=30d and Body fat=1y stay independent after switching sections, navigating to a sheet and returning. | Partial / not verified | WLO-0090 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A90-03 | calculate method A, edit waist, then attempt save: no write until Calculate is pressed again | Fail | R17: profile changes not observed |
| A90-04 | 40 in is persisted as 101.6 cm within conversion tolerance (1e-6 cm); toggling cm/in does not repeatedly round stored canonical values | Fail | R17: repeated conversion rounds canonical input |
| A90-05 | inject failure at each event/attr write | Partial / not verified | WLO-0090 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A90-06 | seed adjacent body-fat estimates from two methods and an unknown legacy event | Fail | R12: event identity/inputs dropped |
| A90-07 | save in the body sheet, dismiss and observe the new point on the parent without navigation | Partial / not verified | WLO-0090 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A91-01 | a token contrast test enumerates every active role pairing used by weight surfaces in both themes, reports actual ratios and passes the thresholds … | Fail | R20: essential forecast boundaries below 3:1 |
| A91-02 | a component gallery shows default, pressed, focused, selected, disabled, busy/error where applicable | Partial / not verified | WLO-0091 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A91-03 | at 320dp/200% font, labeled buttons, a long ListItem, sheet heading, stat with lb value and error supporting text wrap/reflow without losing value/… | Partial / not verified | WLO-0091 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A91-04 | no weight UI renders an info mark with a missing action; no static badge is made focusable merely to imitate a chip | Partial / not verified | WLO-0091 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A91-05 | source audit records every retained custom wrapper and its standard delegate or justified exception; no hand-rolled button, list row or switch is r… | Partial / not verified | WLO-0091 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A91-06 | screenshots of overview, capture, Goals, logbook and import in both themes show one coherent hierarchy | Partial / not verified | WLO-0091 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A92-01 | seeded history and active goal show hero → period/chart → compact goal → history in semantic/visual order | Partial / not verified | WLO-0092 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A92-02 | no goal “Set a goal” opens creation; active goal “Edit goal” opens existing values | Partial / not verified | WLO-0092 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A92-03 | tap September history bucket: destination shows only its half-open range, title and Clear filter | Partial / not verified | WLO-0092 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A92-04 | after save, the chart and provenance remain mounted, no full-screen confirmation replaces them, and the selected period remains unchanged. | Partial / not verified | WLO-0092 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A92-05 | at 320dp/200% font and with IME open in capture, every field/action is reachable, FAB/action region never hides content, and exactly one primary ca… | Partial / not verified | WLO-0092 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A92-06 | loading, empty, failed read, forming trend, held forecast, loss, maintenance and gain screenshots/semantics demonstrate truthful states with no fab… | Partial / not verified | WLO-0092 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A93-01 | populate every field, remove focus and inspect at 200% font | Partial / not verified | WLO-0093 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A93-02 | a weight-first profile with zero Targets opens Set a goal and saves valid maintenance/loss/gain examples through v1 | Partial / not verified | WLO-0093 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A93-03 | enter `80,5` in a comma-decimal locale; save canonical 80.5 kg | Fail | R01: 160 lb becomes 352.7 lb |
| A93-04 | save concurrently from two editors with the same baseVersion | Fail | R13: no actual conflict diff |
| A93-05 | inject failure before Targets write, after Targets commit/before safety completion, and during profile write | Fail | R13: Save does not retry journal completion |
| A93-06 | Back with dirty draft, then Keep editing preserves all values; Discard removes only this draft | Fail | R01/R13/R14: draft/exit failures |
| A93-07 | profile birth year beyond injected current year is rejected; unchanged supported bounds and all existing safety tests remain green | Partial / not verified | WLO-0093 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A94-01 | without swiping, a keyboard/TalkBack user can find Edit, Explain and Delete for the correct row | Partial / not verified | WLO-0094 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A94-02 | a fast short swipe never commits; crossing threshold then returning below it before release does not commit; releasing beyond threshold follows exi… | Partial / not verified | WLO-0094 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A94-03 | delete the only row while scrolled at the end of a long month | Partial / not verified | WLO-0094 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A94-04 | enable extended accessibility timeouts; Undo remains usable for the recommended interval | Partial / not verified | WLO-0094 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A94-05 | inject delete and restore failures | Fail | R03/R04: lost retry receipt and delete race |
| A94-06 | September range excludes October 1, includes September 1, and Clear filter restores all rows | Fail | R10: aggregate contract still minimum-based |
| A94-07 | edit double-tap creates one replacement; Cancel and failed transaction preserve original | Partial / not verified | WLO-0094 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A95-01 | map a CSV with 12 columns at 320dp/200% font | Partial / not verified | WLO-0095 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A95-02 | import 220.46226218487757 lb; canonical value is 100 kg within 1e-6 | Partial / not verified | WLO-0095 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A95-03 | picker cancellation, read permission loss, 20 MiB limit exceedance, malformed CSV, parser error and destination-write failure each show the appropr… | Fail | R06: metadata/size exception escapes |
| A95-04 | duplicate Apply, rotation, process recreation after commit and a readback failure yield one committed batch | Fail | R09/R19: stale stage and missing commit-phase restore |
| A95-05 | new policy timezone/version and body method metadata survive encrypted/JSON backup round trips; legacy bundles migrate deterministically | Partial / not verified | WLO-0095 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A95-06 | Fresh Start cannot be reached or executed in the release flow; no UI promises a backup that was not created | Partial / not verified | WLO-0095 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A96-01 | first launch after completed onboarding selects Weight | Partial / not verified | WLO-0096 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A96-02 | every W01–W17/S01–S09 destination has one visible screen title, correct selected top-level owner and correct Up action; modal titles remain indepen… | Fail | R14: duplicate Goals/Profile/F13 titles |
| A96-03 | resize 599→600→839→840dp and back with Body fat selected and history scrolled | Fail | R15: no adaptive content |
| A96-04 | tap a history range, edit an event, press Back and return to overview | Fail | R15: no list-detail state |
| A96-05 | malformed deep-link ranges and missing event IDs show recoverable fallback; valid historical deep links remain compatible | Partial / not verified | WLO-0096 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A96-06 | landscape with IME, 320dp width and 200% font leave all actions reachable | Partial / not verified | WLO-0096 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A97-01 | manual, file, Health Connect and skipped-source completions land in their correct surfaces | Fail | R05: completion can duplicate weight |
| A97-02 | fail each completion write, retry, and confirm one profile/initial measurement/completion record | Fail | R05: no durable completion token |
| A97-03 | settings initially exposes units/profile/goals/reminder before AI | Partial / not verified | WLO-0097 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A97-04 | choose 20:30; cancel leaves old schedule, confirm enqueues one replacement | Partial / not verified | WLO-0097 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A97-05 | deny permission, revoke it in system settings, disable the channel, then return | Fail | R16: no live availability/repair state |
| A97-06 | synthetic DST gap/overlap and timezone-change tests schedule one next valid occurrence; disabling cancels it | Fail | R16: timezone/DST policy absent |
| A97-07 | at 200% font with TalkBack, time row is named, switches have correct state, optional setup paths are distinct and every button is reachable above I… | Partial / not verified | WLO-0097 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A98-01 | fixture with two raw readings and a median daily sample shows the correct series label/contributing sources in detail and View data | Fail | R11/R12: units/identity mismatch |
| A98-02 | a keyboard/TalkBack user reaches every sample through controls/list, opens explanation, and returns without losing selection | Fail | R08: inaccessible selection/explanation |
| A98-03 | vertical scrolling initiated over the chart still scrolls; horizontal exploration selects deterministically; an equal-distance tap picks the earlie… | Partial / not verified | WLO-0098 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A98-04 | change period excluding the selected point: selection clears honestly | Partial / not verified | WLO-0098 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A98-05 | grayscale/high-contrast inspection still distinguishes measured/trend/forecast; light/dark legend/detail text pass WLO-0091 thresholds | Fail | R11/R20: detail precision and visual contrast |
| A98-06 | animation scale 0 yields correct final UI immediately; 1× animation stays interruptible; haptics off yields no vibration | Partial / not verified | WLO-0098 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A98-07 | record a 60-second representative chart/scroll/section-switch session on the named emulator/device using frame timing | Partial / not verified | WLO-0098 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A99-01 | every scenario A88-01 through A98-07 as actually defined in its ticket maps to executable test or manual evidence; no missing scenario IDs | Fail | R21: grouped pass claims do not map to assertions |
| A99-02 | exact expected raw/attr/version counts hold through failed transactions and retries | Fail | R02/R04/R05/R07: integrity not proven |
| A99-03 | contrast reports meet 4.5:1 normal text, 3:1 large text/essential visual boundaries; actionable targets >=48dp; populated labels visible; no info a… | Fail | R08/R14/R20: accessibility/contrast failures |
| A99-04 | manually perform the named TalkBack/keyboard journeys; record traversal/actions and outcome | Partial / not verified | WLO-0099 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A99-05 | representative frame trace reports fixture/device/duration/frame totals/jank and comparison baseline | Partial / not verified | WLO-0099 source/host coverage; full specified lifecycle/device/assertion matrix still required |
| A99-06 | no shame copy, unconditional loss-is-good coloring, guessed held values, unsafe Fresh Start entry, empty primary destination or duplicate screen ti… | Fail | R14: duplicate titles remain |
| A99-07 | M3 self-audit scores each of the eight skill categories 0/1/2 with evidence | Fail | Confirmed zero-score areas and P1 defects |

## Validation performed and limitations

- `./gradlew :core:data:jvmTest :core:engines:jvmTest :core:database:jvmTest :core:vault:jvmTest :core:designsystem:testDebugUnitTest :feature:f06-weight:testDebugUnitTest :feature:f01-onboarding:testDebugUnitTest :feature:f13-vault:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug checkArchitecture` — successful; existing up-to-date/cache results reused where applicable. Architecture checker: 28 projects, D1–D7/D9 clean.
- Temporary `ImplementationReviewRegressionTest.legacyPersistedTrendMustMatchCanonicalAfterPolicyInitialization` — **fails**, expected 80.0, actual stored 79.0. The fixture source and exact XML/log are attached. A compile/import issue in the initial review harness was corrected before this actual assertion run; it is not an application defect.
- After removing the temporary test from module sources, `./gradlew :core:data:jvmTest ktlintCheck detekt` — successful, using Gradle caching. This restores the normal test/source workspace; it does not invalidate the saved regression failure.
- Existing instrumentation XML inspected: 78 tests, 41 failures, 37 passes, no skipped/errors. This result belongs to the prior WLO-0099 run, not a fresh review run. Several tests fail before executing the new behavior. No blanket claim that all 41 are harmless stale tests is justified without triage.
- Emulator checks: normal/200% text overview, expanded rail/column, Goals dirty draft in lb, toolbar/system Back behavior, chart unit formatting and disabled initial navigation semantics. Settings restored afterward. No real health data was used in generated artifacts.
- Not run: complete current-API leg, physical TalkBack/keyboard journey, provider-failure instrumentation, all process-death races, theme/gallery pressed/focus comparisons, 60-second frame trace and physical haptics. The original WLO-0099 release gate still needs these, even after fixing confirmed code defects.

## Recommended repair order

1. Resolve R01/R02 and the write/recovery failures R03–R07/R09 with focused regression tests. These protect user-entered numbers and committed records.
2. Complete goal/import journals, canonical history consumers and per-event chart identity/units. Test cross-feature parity and recovery before visual refinement.
3. Finish chart accessibility and shared exit policies, then adaptive panes and reminder effective states.
4. Finish semantic chart contrast/provenance and compact/large-font composition; record normal/reduced-motion behavior.
5. Repair instrumentation robots/assertions, run both API legs and the required manual intersections, and rewrite WLO-0099's evidence row by row. Close implementation tickets only after their full acceptance scenarios hold.

All referenced findings are recommendations for implementation fixes; this review intentionally did not apply those application changes.
