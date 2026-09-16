---
id: WLO-0063
title: 'M3-first UI architecture: remove import bans and replace fake controls'
status: done
theme:
release:
created: 2026-09-15T18:20:51Z
modified: 2026-09-15T18:45:35Z
closed: 2026-09-15T18:45:35Z
revision: 5be627b0bf206520
blocks: [WLO-0056]
related: [WLO-0057]
---

# Description

Implemented the M3-first correction from WLO-0057.

- Standard Material 3 imports are legal in app and feature code. The architecture gate now rejects the proven clickable-`Surface` lookalike pattern and retains typography guardrails rather than enforcing wrapper ownership.
- `WloSegmentedBar` composes `SingleChoiceSegmentedButtonRow`/`SegmentedButton`.
- `WloCheckRow` composes `ListItem`/`Checkbox` with row-wide checkbox semantics.
- `WloPrimaryRow` composes `FilledTonalButton`.
- `WloSwitchRow` composes `ListItem`/`Switch`.
- `WloTemplateCard` composes clickable `Card` and exposes selected semantics.
- Banner actions use `TextButton`; actionable provenance uses `AssistChip` while preserving the domain-enforcing provenance API.
- `WloSwipeRevealRow` remains custom, with KDoc recording the exact `SwipeToDismissBox` limitation and owning WLO-0050 ticket.

Verification: architecture self-tests pass, direct-M3 legality has a regression test, design-system ktlint passes, design-system compilation passes, `checkArchitecture` passes with zero violations, and `:app:assembleDebug` passes.
