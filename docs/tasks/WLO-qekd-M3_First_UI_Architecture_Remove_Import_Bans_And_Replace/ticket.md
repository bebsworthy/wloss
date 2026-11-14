---
id: WLO-qekd
was: WLO-0063

title: 'M3-first UI architecture: remove import bans and replace fake controls'

status: done
closed: 2026-09-15T18:45:35Z
status_changed: 2026-09-15T18:45:35Z

theme:

release:

created: 2026-09-15T18:20:51Z

blocks: [WLO-0kw9]

related: [WLO-sbnk]

parent:

rank:
---

# Description

Implemented the M3-first correction from WLO-sbnk.

- Standard Material 3 imports are legal in app and feature code. The architecture gate now rejects the proven clickable-`Surface` lookalike pattern and retains typography guardrails rather than enforcing wrapper ownership.
- `WloSegmentedBar` composes `SingleChoiceSegmentedButtonRow`/`SegmentedButton`.
- `WloCheckRow` composes `ListItem`/`Checkbox` with row-wide checkbox semantics.
- `WloPrimaryRow` composes `FilledTonalButton`.
- `WloSwitchRow` composes `ListItem`/`Switch`.
- `WloTemplateCard` composes clickable `Card` and exposes selected semantics.
- Banner actions use `TextButton`; actionable provenance uses `AssistChip` while preserving the domain-enforcing provenance API.
- `WloSwipeRevealRow` remains custom, with KDoc recording the exact `SwipeToDismissBox` limitation and owning WLO-tdwg ticket.

Verification: architecture self-tests pass, direct-M3 legality has a regression test, design-system ktlint passes, design-system compilation passes, `checkArchitecture` passes with zero violations, and `:app:assembleDebug` passes.
