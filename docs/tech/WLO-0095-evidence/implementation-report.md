# WLO-0095 implementation report

## Shipped behavior

- Replaced unbounded, main-thread picker reads with an injected IO reader that copies at most 20 MiB into app-private staging, survives ViewModel recreation, cleans expired sessions, and deletes a session after success or explicit discard.
- Added Pick → Reading → Mapping → Reviewing → Applying → Done states with recoverable errors, retained mappings, Back to mapping, Choose another file, and guarded duplicate Apply.
- Replaced the horizontal mapping-chip strip with one Material 3 `ListItem` per source column, example values, exposed destination dropdowns, and labeled source-unit controls.
- Enforced exactly one Date mapping, at least one measurement, unique destinations, strict ISO `YYYY-MM-DD`/epoch-day dates, and row-numbered correction guidance.
- Converts lb to canonical kg once (`220.46226218487757 lb` → `100 kg` within `1e-6`), rejects imported derived trend, and preserves explicit custom units.
- Added opaque review tokens so a changed/stale mapping cannot commit. Existing stable import keys keep retries and duplicate Apply idempotent.
- Review now discloses file, accepted/rejected counts, date/unit/capture-time assumptions, duplicate policy, and concrete warnings.
- Export keeps selected options on cancellation/failure, reports success only after the destination writer returns, exposes a busy state, and offers Retry write.
- Removed the unsafe Fresh Start action and claims from release UI. `FEATURES.md` and F13 now preserve R-B7 as explicitly unimplemented pending a genuinely reversible ledger workflow.

## Verification

- `./gradlew :core:vault:jvmTest :feature:f13-vault:testDebugUnitTest :app:testDebugUnitTest` — passed.
- `./gradlew :feature:f13-vault:ktlintCheck :feature:f13-vault:detekt :core:vault:ktlintCheck :app:ktlintCheck :app:detekt` — passed.
- `./gradlew :app:assembleDebug checkArchitecture` — passed; D1–D7 and D9 clean.
- Unit coverage includes strict/ambiguous dates, exact lb conversion, derived-trend rejection, duplicate destinations, required mappings, and existing atomic/idempotent CSV commit tests.
- Existing `BackupDocumentIOTest`, `ExportBundleTest`, and `StagedRestoreJvmTest` remained green within `:core:vault:jvmTest`, covering metadata/bundle and staged-restore regressions.

## Not verified in this environment

- `M6RestoreWizardTest` instrumentation, 320dp/200% font screenshots, TalkBack traversal/announcements, non-English device-locale interaction, and real SAF-provider permission-loss behavior were not run because no confirmed disposable emulator or test provider was available. These remain unverified, not passes.
