---
id: WLO-0032
title: 'Design-system API polish — adopt lane follow-ups (icons, switch enabled, sheet header, shared byte formatting)'
status: done
theme:
release:
created: 2026-09-14T06:19:06Z
modified: 2026-09-14T06:48:57Z
closed: 2026-09-14T06:48:57Z
revision: 10b6bfe2289c6f0f
blocks: []
related: []
---

# Description

Follow-ups recorded by the WLO-0031 migration lanes (see its evidence comment). These are API friction items, not violations — the goal is that features never need to hand-build what the design system should own.

1. WloIcons: only ChevronRight exists; three lanes hand-built vectors in-module (f01 Minus/Plus/Close, f02 Scan/Search). Promote a shared minimal set (Plus, Minus, Close, Check, Search, Scan) in core/designsystem WloIcons idiom and migrate the private copies away.
2. WloSwitchRow: no `enabled` (disabled visuals lost — consent rows gated by the kill switch, f13 toggles gated by folder/passphrase preconditions) and no `secondary` caption slot. Add `enabled: Boolean = true` (+ dimmed row rendering) and `secondary: String?`. Adopt where semantics allow — CAREFUL: the f12 kill-switch instrumented test is now behavioral (grants a category through the UI while Cloud: OFF and asserts the VM refuses the write); if disabling the row's tap would break that flow, apply the visual without disabling the tap, or scope adoption to where tests permit. Verify against the test, not assumptions.
3. WloSheet: no title/header slot — sheet titles remain bare wloType.title Text (legal but inconsistent with card headers). Add `title: String? = null` rendering WloCardHeader inside the sheet; migrate f02's four bare-title sheets.
4. formatBytes decimal formatter triplicated (f12/f13 internal + ZooScreen copy) — move one shared pure formatter to :core:common and use it in all three.

OUT OF SCOPE: WloTextLink (no current consumer — skip), determinate progress (needs VM fractions — deferred).

ACCEPTANCE: designsystem + core:common builds green; build-logic self-tests 21/21; affected module builds green with uiAtoms 0; full :app:connectedDebugAndroidTest green; ktlint/detekt green; emulator reinstalled; spot screenshots (a sheet with the new title slot, studio switches). Enforce mode must stay on.
