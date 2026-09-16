# Dogfooding distribution — alpha + release channels

Goal: install WLO on the owner's phone for daily dogfooding, with (a) an
**alpha channel** = latest green `main`, and (b) a **release channel** =
tagged stable builds. Updates as transparent as possible.

## Current state (surveyed 2026-09-13)

- `.github/workflows/ci.yml` exists (build+checkArchitecture, arch self-test,
  connected API-29 instrumented) **but the repo has no git remote** — CI has
  never actually run; nothing is published anywhere.
- `versionCode = 1` / `versionName = "0.1.0"` hardcoded in
  `wlo.application.gradle.kts` → Android will refuse same-version reinstalls;
  every published build needs a monotonically increasing CI-injected code.
- Release build type is stock: unsigned, no R8 decision; the existing
  `benchmark` type shows the pattern for a signed release-like build.
- **R-S13 already sanctions Play Store + GitHub APKs** as the two distribution
  channels (F-Droid optional later). This plan stays inside that ruling.
- Data-safety base is good: Room schemas 1–6 exported, JVM `MigrationTest`
  exists; F13 vault backup/restore (M6) is built. Alpha-channel dogfood data
  is irreplaceable — migrations must stay tested, channels must never force
  uninstall (uninstall wipes data; mismatched signing keys force uninstall).

## Requirements (channel-agnostic)

1. **Hosting**: push to GitHub (public preferred — FOSS project, and both
   Obtainium and an in-app updater work cleanly against public Releases;
   private Releases need auth tokens on the phone).
2. **Versioning**: CI injects `versionCode` (recommended: `git rev-list
   --count HEAD` — monotonic along main, shared by both channels so a stable
   tag cut from a main tip is always installable over older alphas, and
   never over newer ones); `versionName` per channel (tag → `x.y.z`; main →
   `x.y.z-alpha.N+<shortsha>`).
3. **Signing**: one dedicated keystore, base64 in GitHub Actions secrets,
   used for **both** channels and never committed; debug and channel builds
   must never mix signing identities (signature change ⇒ forced uninstall ⇒
   data loss). Same `applicationId` for both channels (no `.alpha` suffix)
   so the alpha rides ahead and stable replaces it in place — dogfood data
   continuity.
4. **Publish workflow** (extends ci.yml or a sibling `release.yml`):
   on green push to `main` → assemble release-signed APK → GitHub Release
   (rolling `alpha` tag or per-build tag) with auto-generated notes from the
   disciplined commit subjects (`build: M6 … (WLO-00XX)`); on `v*` tag →
   same, marked stable.
5. **Transparency of content**: release notes = what changed + ticket refs;
   long-term an in-app "update explainer" fits the product ethos.

## Update-mechanism alternatives (phone side)

| Mechanism | Update UX | Infra/code | Notes |
| --- | --- | --- | --- |
| adb / shared APK | manual | none | baseline, zero transparency |
| GitHub Releases, manual download | manual, changelog visible | trivial | |
| **GitHub Releases + Obtainium** | auto-check → notification → 1-tap, notes shown | none in-app | recommended to start |
| In-app self-updater (PackageInstaller + GitHub API through NetworkDispatcher) | fully silent possible, in-app explainer/changelog | a real feature (consent + egress receipts required; needs `REQUEST_INSTALL_PACKAGES`) | the WLO-souled endgame |
| Play Console internal testing / app sharing | fully silent (Play-managed) | $25 once, AAB, Play App Signing, policy overhead | sanctioned by R-S13; better when Play launch prep starts |
| Firebase App Distribution | notification → manual install | Firebase project | manual installs + Google-service dependency; clashes with no-SaaS ethos — skip |
| F-Droid / IzzyOnDroid | store-managed | metadata + reproducible builds | slow queues; public-release channel later, not alpha |

## Recommendation

Start: GitHub repo + `release.yml` (alpha-on-main, stable-on-tag) +
Obtainium on the phone. Next: decide whether/when to build the in-app
self-updater as a proper consent-gated platform feature. Play internal
testing when Play work begins in earnest.


## Implementation checkpoint — 2026-09-16

The owner-approved choices are now the channel contract: a public GitHub
repository, Obtainium for phone-side updates, and one application ID/signing
identity shared by alpha and stable.

Distribution is consolidated into `.github/workflows/ci.yml`; there is no
independent release workflow that can publish around the verification gate.
Pushes to `main` and `v*` tags must pass the build/checkArchitecture job,
build-logic self-tests, and API-29 instrumented suite before a signed APK can
publish. Alpha is a rolling prerelease; stable accepts semantic `vX.Y.Z` tags
only and requires the tagged commit to be in `main` history.

Signing fails closed: release builds never fall back to the debug key, all four
CI secrets are required, environment variable names match the documented
contract, and the final APK certificate SHA-256 is checked against the pinned
canonical dogfood identity before publication. A stale main run refuses to
replace a newer alpha.

Operator and installation guidance lives in `docs/tech/DISTRIBUTION.md`; the
repository landing page links the governing product, architecture, design, and
distribution documents.

Remaining completion evidence: create/push the public repository, configure
its signing secrets, observe the first green CI-published alpha, and validate
an update on the owner's real device through Obtainium.


## First remote gate finding — 2026-09-16

The first GitHub Actions run correctly blocked publication because
`KoinModuleVerifyTest` exposed that the WLO-0045 goal-progress loader was built
inline inside the ViewModel definition and therefore invisible to Koin's graph
verifier. The feature graph now owns `GoalProgressLoader` as an explicit
factory. Its document-reader closure and the derived mass-unit flow are marked
`@Provided`, accurately documenting values supplied by module code rather than
looked up as graph definitions.

Local remediation evidence: `:app:testDebugUnitTest` and
`:feature:f06-weight:testDebugUnitTest` pass, including the whole-graph verify
test and goal-progress reliability coverage. A new push will rerun the complete
remote gate before publication.


## Remote emulator gate adjustment — 2026-09-16

The first remediated run proved the JVM/build/architecture gates green, but the
hosted `connectedDebugAndroidTest` invocation emitted `Running tests on devices`
and then no test progress for 61 minutes; it was cancelled after 65 minutes to
recover logs. An opaque, effectively unbounded 62-test orchestrated suite is not
an operable per-push release lock.

The publication gate now uses a 35-minute, six-scenario API-29 release-smoke
set covering launch, weight-first onboarding, weigh-in entry, trend-first save
confirmation, forecast rendering, and backup. The complete instrumented suite
remains intact in `instrumented-full.yml`, runs nightly or by manual dispatch,
has a 90-minute bound, and uploads reports even on failure. Publication still
requires Android device evidence; exhaustive coverage is retained without
letting one hung test block every dogfood build indefinitely.
