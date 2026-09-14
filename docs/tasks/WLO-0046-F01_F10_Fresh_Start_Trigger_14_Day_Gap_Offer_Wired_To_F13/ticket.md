---
id: WLO-0046
title: 'F01/F10 Fresh Start trigger: >=14-day gap offer wired to F13 hide-not-delete (R-B7)'
status: backlog
theme:
release:
created: 2026-09-14T13:38:55Z
modified: 2026-09-14T13:53:08Z
closed:
revision: bdc0a89a16e5cdf9
blocks: []
related: []
---

# Description

# Goal

The ritual half of Fresh Start (F01 §3, R-B7): returning after a >=14-day weigh-in gap, the Hub offers Fresh Start — hide-not-delete everything before a chosen date. F13's mechanics half already exists (VaultFreshStartReport, hide/reveal in the vault); this ticket is the trigger, flow, and render. No "welcome back, you gained" copy — ever.

# Work items

- Gap detection in HubViewModel (last weigh-in vs today >= 14 days; offer once per gap, dismissable).
- Offer card copy per §2/§6 tone rules; Fresh Start framed as a clean page, never a verdict.
- Flow: pick boundary date → vault hide-not-delete pass → confirmation; reversible from the vault surface; pre-gap history stays exportable.
- Fresh Start render (page-turn) per the DESIGN-SYSTEM inventory.
- Tests: gap edges (exactly 14, DST, zero weigh-ins), hide/reveal roundtrip via the vault port.

# Gates

Demo with backdated seed + the frozen demo clock; arch/ktlint/detekt green.

## References

- Spec: [F06 §4](docs/features/F06-weight-body-metrics.md) (long gap / relapse: >=14 days → Hub offers Fresh Start; no "welcome back, you gained" — ever) · F01 §3 (the ritual owner)
- Ruling: [FEATURES.md §3](docs/features/FEATURES.md) R-B7 (hide-not-delete, reversible, exportable)
- Mechanics (already built): [DataVaultPort.kt](core/ports/src/commonMain/kotlin/app/wlo/core/ports/DataVaultPort.kt) VaultFreshStartReport + vault surface
- Design: [DESIGN-SYSTEM.md F06 inventory](docs/design/DESIGN-SYSTEM.md) ("Fresh Start render (page-turn)")
- Note: no dedicated flow prototype exists yet — frame 04's archive framing is the nearest mock; a small frame addition may be wanted.
