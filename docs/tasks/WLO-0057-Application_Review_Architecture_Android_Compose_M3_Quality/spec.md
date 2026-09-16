# Objective

Establish whether WLO's current Android architecture is correct for this product without being overengineered, with special attention to whether Compose UI uses Material 3 as the framework rather than maintaining a parallel component framework. Review architecture and Android/framework correctness before auditing product logic.

# Review principles

- Judge every abstraction by a concrete WLO invariant, reuse need, testability gain, or platform boundary. "Might scale later" is not sufficient.
- Preserve the product's unusually important structural guarantees: local-first data, one egress choke point, per-capability consent, event-level history, vector-only silhouette storage, and deterministic/provenanced calculations.
- Prefer standard AndroidX and Material 3 APIs through documented slots, defaults, semantics, and theme parameters.
- A WLO abstraction is justified only when it adds product semantics that M3/AndroidX does not provide, or centralizes a truly app-wide policy. Thin renames and parameter-erasing wrappers are candidates for deletion.
- Findings must cite code and describe observable cost or failure risk. Avoid architecture-style opinions without evidence.
- Review current in-flight tickets before proposing work so WLO-0050/WLO-0055/WLO-0056 are not duplicated or invalidated silently.

# Baseline already established

- Read the governing product, feature, design-system, IA, and architecture documents plus the active ticket list.
- The project currently includes `:app`, 15 `:core:*` modules, 8 feature modules, and `:benchmarks`, with roughly 65k Kotlin lines including tests and build logic.
- WLO-0030 correctly identified misuse of bespoke UI in place of M3, but WLO-0031 responded by adding a broad WLO wrapper family and an architecture rule that bans feature/app imports of many standard M3 components. That enforcement conflicts with the 2026-09-15 owner ruling that standard M3 components are the default.
- WLO-0056 is an active list-only conformance sweep and should be treated as evidence/input to this broader review, not repeated.

# Work plan

## 0. Reproducible baseline and evidence map

1. Record repository state, current open tickets, module graph, source/test size, public API surface, build variants, dependency versions, and existing architecture/static-analysis gates.
2. Run the existing architecture check, lint, JVM/unit tests, and a debug build; record failures separately as pre-existing or review-introduced.
3. Build a route/screen/component inventory and map each implemented feature to its governing spec and open ticket.
4. Produce a short "constraints versus choices" table: frozen product invariants on one side, replaceable implementation choices on the other.

Exit criterion: another reviewer can reproduce the baseline and every later finding has a known owner/module/surface.

## 1. Architecture fitness and overengineering review

Review top-down before commenting on individual classes:

1. **Module granularity:** measure dependency fan-in/fan-out, public API size, build isolation value, and boilerplate for each of the 25 included modules. Flag modules that exist only to preserve a diagram, and modules whose security/privacy boundary is genuinely structural.
2. **Layering:** trace representative vertical slices (weigh-in, meal log, consented AI call, backup/restore) from Compose event to persistence and back. Check UI -> state holder -> repository/data source boundaries, SSOT ownership, and whether optional domain/interactor layers exist only where logic is reused or complex.
3. **Composition and DI:** inspect Koin wiring, route contracts, ViewModel construction/scoping, restricted implementation bindings, and service/background entry points. Look for service-locator behavior, oversized composition roots, or interfaces with only ceremonial value.
4. **KMP posture:** quantify the cost of commonMain/JVM-purity/explicit API today versus plausible reuse. Keep portable calculation engines; challenge KMP scaffolding around Android-only data/platform code when it adds friction without a second consumer.
5. **Data architecture:** check Room/DataStore/document ownership, transaction boundaries, repository responsibilities, duplication between persisted derived values and recomputation, error contracts, and migration discipline.
6. **Enforcement:** distinguish high-value invariant checks (egress, feature isolation, no Android in pure engines) from style-policy checks that freeze a locally invented framework.

Output: a keep/simplify/merge/remove decision matrix, with benefit, cost, migration risk, and recommended order. No large refactor merely to match a reference architecture.

## 2. Android and Compose best-practice review

Use current official Android guidance as the benchmark, adapted to WLO rather than applied mechanically:

1. Lifecycle-safe Flow collection, immutable screen state, UDF/event handling, state hoisting to the lowest appropriate owner, ViewModel/nav-back-stack scope, SavedStateHandle/process recreation, and one-off event handling.
2. Coroutine ownership, cancellation, dispatchers, exception propagation, and long-running work through the appropriate scheduler rather than screen scope.
3. Room transactions, observable queries, database threading, schema/migration tests; DataStore ownership and update semantics.
4. Navigation/deep links, state restoration, single-activity composition, edge-to-edge/insets, configuration change handling, adaptive layouts/window classes, and large-screen behavior.
5. Accessibility: semantics, touch targets, traversal, content descriptions, dynamic font scaling, contrast, reduced motion, keyboard/switch access, and screen-reader behavior.
6. Platform privacy/security: permissions, camera/photo lifetime, secure storage, backup/export boundaries, notification channels, background limits, and the single-egress guarantee.
7. Build/release hygiene: lint severity, Baseline Profiles/startup, R8/resources, dependency hygiene, reproducibility, and CI coverage.

Exit criterion: findings distinguish correctness risks from maintainability issues and optional polish.

## 3. Material 3 framework review (priority UI workstream)

1. Inventory every component in `:core:designsystem` and every raw/custom control at call sites.
2. Classify each component:
   - **Theme/token mapping:** keep (color scheme, typography, shapes, motion policy).
   - **Standard M3 component with normal parameters/slots:** use directly; delete wrappers that only rename or hide the M3 API.
   - **Thin WLO extension with real product semantics:** keep only the extra behavior and compose M3 internally.
   - **Genuinely custom visualization/interaction:** keep with the required KDoc justification and ticket (for example signature charts or a proven swipe behavior M3 cannot supply).
3. Audit component choice and anatomy for Scaffold, app bars, navigation, ListItem, cards, buttons, icon buttons, chips, switches, text fields, dialogs, sheets, snackbars, progress, pull-to-refresh, and adaptive navigation.
4. Review whether WLO styling is expressed primarily through `MaterialTheme` and component parameters instead of wrappers, copied metrics, or hard-coded anatomy.
5. Replace the current `uiAtoms` policy conceptually: enforce against fake/lookalike controls, inaccessible click targets, hard-coded visual tokens, and unjustified custom components—not imports of standard M3 components.
6. Check experimental API use and version-specific behavior against the pinned Material 3 release.
7. Verify representative screens visually and semantically at API 29 and target SDK, light/dark, font scale, narrow/wide window, TalkBack, and reduced-animation settings.

Deliverable: an M3 disposition table for all WLO atoms, plus a small ordered migration plan. First remediation should change policy/gates and one representative vertical slice; only then fan out mechanically.

## 4. Code-quality and test-quality review

1. Find large/complex ViewModels, screens, repositories, DI modules, and duplicated mappings/formatters.
2. Review naming, visibility, cohesion, null/error handling, result types, exception boundaries, time/unit abstractions, and dead or speculative code.
3. Evaluate tests by risk rather than count: pure engine properties/goldens, repository transactions/migrations, ViewModel state transitions, navigation/state restoration, and minimal stable UI tests.
4. Identify brittle test tags/copy assertions, screenshot gaps, unused abstractions, and gates disabled in configuration (for example lint informational or broad detekt rules disabled).
5. Produce a deletion/simplification shortlist before proposing new abstractions.

## 5. Product/domain logic audit (only after structural review)

Audit logic in risk order:

1. Data-loss/privacy/security invariants: append/delete/undo, backup/restore/import, vector-only image lifetime, consent revocation and in-flight cancellation, receipt chaining, egress denial.
2. Shared-data invariants and transactional consistency: single profile, targets writers, event-to-day projections, derived value provenance, held states, and cross-feature ownership.
3. Numerical engines: units, rounding, dates/time zones/day boundaries, EWMA and trend windows, forecasting bands, calorie/energy constants, outlier behavior, and deterministic planner/list calculations. Use property, metamorphic, and golden tests where appropriate.
4. User-flow state machines: onboarding, correction-before-save, plan -> list -> pantry, weigh-in/edit/delete/undo, and backup/restore failure recovery.
5. Concurrency and recovery: repeated taps, cancellation, process death, partial failures, retries, duplicate imports, and idempotency.

Exit criterion: each material logic claim is backed by a traceable invariant and a test or a clearly documented test gap.

## 6. Findings and remediation sequencing

Produce one review report with findings ordered P0-P3 and grouped by root cause. Each finding includes evidence, user/product impact, smallest correction, affected tickets, and verification. Then create narrowly scoped remediation tickets only after the report avoids duplicates.

Recommended implementation order after approval:

1. Correct harmful architecture/enforcement rules.
2. Prove the simpler M3 approach on one representative screen and shared theme.
3. Apply safe mechanical UI migrations in small batches.
4. Fix lifecycle/data correctness issues.
5. Simplify module/layer cruft where measured benefit is absent.
6. Address domain-logic findings with tests first for high-risk invariants.

# Deliverables

- Architecture and module disposition matrix (`keep`, `simplify`, `merge`, `remove`, `defer`).
- Material 3 component disposition matrix and policy change proposal.
- Android/Compose best-practice findings with official-source rationale.
- Code/test-quality findings and baseline gate results.
- Domain-logic invariant matrix and prioritized defects/test gaps.
- Small, dependency-ordered remediation tickets; no big-bang rewrite.

# Non-goals during review

- No visual redesign of the product.
- No replacement of Koin, Room, Compose, or other major technology without measured evidence.
- No speculative layers, generic base classes, or abstraction introduced solely for consistency.
- No implementation changes until a finding is documented and scoped, except minimal diagnostic/test harness fixes explicitly recorded as such.

# Acceptance criteria

- Every included module and every `:core:designsystem` component receives a disposition.
- At least four representative vertical slices are traced end to end.
- Every standard M3 component family used by the app is checked for direct/idiomatic use and accessibility.
- Existing open tickets are linked as overlap, dependency, or independent work.
- All P0/P1 claims include code evidence and a verification strategy.
- The final proposal reduces or holds total abstraction count; any new abstraction must replace more complexity than it adds.


## Baseline run — 2026-09-15

`./gradlew checkArchitecture :app:lintDebug --continue` ran 509 tasks. Android lint completed and wrote its reports, but the build failed at the custom architecture gate with three `uiAtoms` violations: standard M3 `ListItem` in `EgressMonitorScreen.kt` and `ReceiptsScreen.kt`, and standard M3 `TextButton` in `LogbookScreen.kt`. Two of these `ListItem` uses arise in the active M3 conformance work (WLO-0056). This is direct evidence that the WLO-0031 enforcement policy conflicts with the current M3-first ruling and can make an M3 migration fail CI. Also observed: `:core:vault` has a `commonTest` source directory but Android host tests are not enabled; Gradle reports deprecated features that will be incompatible with Gradle 10. These are review inputs, not yet assigned severities.
