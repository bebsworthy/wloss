# Recipe UX specification revision

Apply the owner's accepted Paprika research recommendations to the canonical recipe UX document and reconcile F03, relevant F04 evidence and the governing scope note. Cover incomplete library recipes, contextual review, multiline ingredient entry, retrieval/reuse, bounded cooking support and component-recipe semantics. Preserve the existing import/AI/current-release deferrals.

Acceptance: explicit states and calculation gates; acceptance scenarios for all six revisions; no obsolete mandatory-yield saving rule or Paprika comparison left in affected contracts; WLO-0157 implementation requirements aligned through the Ticket CLI; no application code changes.


## Delivered

- Revised docs/research/recipe-creation-ux.md as the canonical accepted UX contract, preserving its research location and forwarding design link.
- Added readiness states, save-without-yield behavior, contextual hierarchy, lossless text/row editing, minimum retrieval/reuse, stable navigation links versus counted components, bounded cooking sessions and staged acceptance scenarios.
- Reconciled F03 model/behavior and cooking scope, FEATURES.md's accepted future contract note, F13 recipe portability and F04's outdated Paprika attribution. Linked the research report to the adopted contract.
- Updated WLO-0161 and WLO-0157 through the Ticket CLI. Counted-component math, multi-dish/timer extensions and current release deferrals remain explicit.
- Validation: relative links resolve in the canonical/design/F03/F13/master documents; targeted checks find all six revision areas and no superseded mandatory-yield save rule; git diff --check passes for affected documentation. No application code was changed or runtime tests needed for this documentation-only revision.
