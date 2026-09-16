# Problem

Weigh-in append/delete/edit performs multiple required writes outside one repository transaction and suppresses secondary failures. Backdated mutations recompute only the edited day's EWMA scalar even though later values are path-dependent. Numeric validation permits non-finite/non-positive values.

# Scope

- Add repository-level atomic append/delete/replace operations using the existing database transaction boundary.
- Remove append/attach/delete orchestration from `LogbookViewModel`.
- Return typed flags/results rather than exposing Room implementation constants.
- Recompute every affected derived suffix or stop persisting redundant trend events.
- Validate finite, positive, plausible canonical kg values at the write boundary.
- Integrate unit conversion with WLO-0052; preserve WLO-0054 ownership of reload coalescing.

# Acceptance

- Injected failure cannot leave a raw event, attributes, trend rows, and projections mutually inconsistent.
- Edit cannot leave duplicates or delete the original unless replacement is fully committed.
- Backdated insert/delete projections equal fresh calculations for every later day.
- NaN, infinity, zero, negative, and implausible input cannot enter storage.

# Evidence

WLO-0057 report, weigh-in mutation/trend/validation findings.
