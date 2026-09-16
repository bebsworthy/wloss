# Problem

KMP documentation implies portability while commonMain contains JVM APIs and only JVM/Android targets are compiled. Dependency enforcement examines declarations rather than the resolved graph. Lint is non-fatal despite a current error, and complexity/error Detekt rules are globally disabled.

# Scope

- Ratify an honest KMP objective: keep genuinely pure model/engine/document/consent code portable; classify JVM-specific common modules without speculative `expect/actual` work.
- Either add a credible non-JVM compile check for promised portable modules or amend ADR wording/targets.
- Check selected resolved compile/runtime graphs for transitive restricted networking dependencies.
- Baseline reviewed lint findings, then make correctness/security lint fatal in CI.
- Re-enable complexity and broad-exception checks with pragmatic thresholds and documented local suppressions.
- Enable or relocate `:core:vault/commonTest` so intended tests actually run for relevant targets.

# Acceptance

- Architecture documentation, targets, and source APIs describe the same portability guarantee.
- A transitive banned network stack fails a TestKit fixture.
- Current notification permission lint error is resolved and future correctness errors fail CI.
- Complexity suppressions are local and reasoned rather than global.

# Evidence

WLO-0057 report, architecture/build findings.
