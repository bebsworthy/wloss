# Problem

Restore claims an all-or-nothing transaction although Room commits before DataStore/documents/projections. The advertised Confirm step is unreachable because Report's Continue action commits immediately. CSV import is also row-by-row and can leave partial state.

# Scope

- Restore an explicit review/confirm boundary before mutation.
- Introduce a persisted, resumable, idempotent multi-store restore journal or narrow the contract/UI to honest per-store outcomes.
- Make the Room portion atomic and define recovery after process death between stores.
- Make CSV import transactional where data shares Room; otherwise return an explicit committed/failed row report.
- Reconcile consent-ledger matching by overlap and append a valid strict suffix; reject actual forks.

# Acceptance

- Continue from Report cannot mutate data; only explicit Apply can.
- Failure after the Room phase can resume or roll forward deterministically and never claims “nothing changed.”
- CSV cancellation/failure has specified, tested atomic or partial semantics.
- Fresh restore, matching-prefix suffix restore, and fork rejection are tested.

# Evidence

WLO-0057 report, restore/import findings.
