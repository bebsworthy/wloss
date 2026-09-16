---
id: WLO-0031
title: 'UI architecture remediation — complete design-system enforcement + all-surface migration (audit WLO-0031)'
status: done
theme:
release:
created: 2026-09-13T21:21:08Z
modified: 2026-09-14T00:44:48Z
closed: 2026-09-14T00:44:48Z
revision: a54e3d03c4bb6ad5
blocks: []
related: [WLO-0057]
---

# Description

Owner verdict (2026-09-13): WLO-0030 fixed reported INSTANCES, not the CLASS. Same defect families exist on all pages. Deliverable: full audit (done, below) + plan to fix ALL issues of the same type/family. Root cause must be fixed in the UI architecture, not screen-by-screen.

AUDIT RESULT (4 parallel read-only auditors, all 8 feature modules + app shell, 2026-09-13)

Family totals:
F1 fake buttons: ~60 sites — 45 raw M3 Button/OutlinedButton ignoring WloButton/WloSecondaryButton (f13 alone: 21; LockGate, PointOfUseConsentSheet), ~15 Surface(onClick) imitations (f02 ActionRow ~24 instantiations, AiStudio x3, Zoo ActionButton, pantry "match", ListScreen restore rows, SettingsRow, WizardRow), text clickables (Hub "Settings", pantry row actions, "look up" trailing icon).
F2 ad-hoc card headers: ~99 `style = wloType.title` title sites; WloCardHeader used only 11x (f10/f06 only). 5 divergent header treatments (title case, label case, 13sp body, sentence, none). Duplicated header+stat pairs ("trend"/"trend now" pattern) in f01/f10.
F3 typography overrides: 104 `wloType.*.copy(` = 20% of 530 wloType refs. Worst: `title * 1.5f` screen-title hack on 14 screens (ramp has no screen-title slot); `body.copy(receipt.fontSize)` x65 (no caption slot); FontFamily.Monospace x22 (f12/f13 receipts/ledgers); hero `* 0.72f`, title `* 0.94f/1.06f/1.12f/0.92f/0.85f` arithmetic.
F4 spacing: CLEAN (WloSpacing holds; only <=6dp chip-anatomy micro paddings).
F5 hand-rolled chips/badges: ~52 bordered/tinted Surfaces (day-status pill, "exact" badge, staple pill, StatusChip, StateBadge, v1.x badge, provider chips, tag pills, 4 divergent callout/banner variants with mismatched alpha washes 0.10/0.12/0.14/0.16).
F6 M3 avoided or misused: bottom bar hand-rolled from clickable Columns (M3 NavigationBar exists); 34 raw Card( vs WloCard 39; 23 ModalBottomSheet with hand-applied SheetTop; 7 Switch( with hand-repeated SwitchDefaults colors x3; text-only progress where M3 LinearProgressIndicator proven in ZooScreen; FormatCard radio via literal bullet glyphs; unconfirmed destructive "remove" (no AlertDialog).
F7 charts: clean post-0030 (axis+caption); remaining: week dots no legend, viewfinder brackets uncaptioned (minor).
F8 copy defects: ~90 sites — lowercase sentence-fragment CTAs ("add as note", "build list - week pre-selected"), internal rationale leaked as user copy ("the formulas live behind...", "deterministic for its seed", "architecture rationale" lines), literal spec ID "R-B1" in f03 sheet, internal ids "food-classifier/1", raw wire tokens ("off"/"on-device", purpose/outcome), ALL-CAPS shouts ("SAME", "Chain BROKEN", "HIDES"), glyph junk (check/warn bullets, dot-radio), unreachable copy branch (CaptureScreen sheet title), label that fires the opposite action (ListScreen "on - keep it on" -> onAnswer(false)), binary-vs-decimal byte format mismatch (Zoo vs f12/f13).
F9 raw colors: CLEAN (zero hex; only token alpha derivations, though 4 inconsistent alpha pairs).

Worst files: PantryScreen, PlanScreen+PlanSheets, ListScreen, AiStudioScreen, OnboardingSteps (local WloCard + WloToggleChip + WloSmallChip + StepperButton shadowing existing atoms), CaptureScreen, ExportScreen FormatCard, WloApp bottom bar, ZooScreen ActionButton.

ROOT CAUSE (architecture)
1. :core:designsystem is a token library with a PARTIAL component set. Highest-demand primitives do not exist, so features must improvise: no clickable card (onClick/header/border-accent params), no screen-title + caption ramp slots, no sheet wrapper, no status pill/badge, no list row, no switch skin, no icon-action, no empty state, no dialog, no progress wrapper.
2. ZERO enforcement: wlo.architecture-check enforces D1-D4/D6-regex/D7/D9 only; detekt.yml documents custom UI rules as TODO; nothing bans raw Card/Button/Surface(onClick)/BorderStroke/Switch/ModalBottomSheet/wloType.copy outside designsystem. Adoption is voluntary -> drift (WloButton 4 uses vs 45 raw Buttons).
3. Copy was never gated: no review pass ever covered capitalization/rationale-leaks/wire-tokens.

PLAN (phased; each phase gates the next)

P1 COMPLETE THE SYSTEM (:core:designsystem, built ON M3 — acceptance: every F1/F2/F3/F5/F6 site in the audit has a sanctioned replacement)
- Type ramp: add titleL (screen title ~24sp w600) + caption (13sp text-tertiary) slots; provide WloScreenTitle composable.
- WloCard: add onClick, header slot (WloCardHeader integration), optional borderAccent (primary/error) variants.
- WloSheet: ModalBottomSheet wrapper (SheetTop, standard inner padding).
- WloBanner: info/warning callout (replaces 6+ callout variants, single alpha convention).
- WloBadge (static status pill) + WloTag.
- WloListRow (M3 ListItem semantics: label/value/chevron/selected) + WloRailButton (icon+label quick-action).
- WloSwitchRow (M3 Switch + WLO skin + setting-row pattern).
- WloIconAction (IconButton/FilledIconButton wrapper, 48dp floor).
- WloBottomBar -> M3 NavigationBar.
- WloDialog (AlertDialog wrapper; destructive confirm for entry remove).
- WloProgress (LinearProgressIndicator + caption) for backup/restore/import "applying" states.
- WloEmptyState (DESIGN-SYSTEM 7.1 promised, never shipped).
- App shell: WloApp bottom bar, SettingsScreen rows, LockGate, zoo, Stub/Placeholder titles migrated too.

P2 ENFORCE (acceptance: green build REQUIRES conformance; regressions impossible)
- Extend build-logic ArchRules (source-scan, same infra as D1-D9) for :feature:* and :app:*: ban raw androidx.compose.material3 {Card, Surface-with-onClick, Button, OutlinedButton, TextButton, IconButton, Switch, ModalBottomSheet, AlertDialog, NavigationBar} constructors; ban BorderStroke and FontFamily.Monospace outside designsystem; ban `wloType.` `.copy(` outside designsystem. Allowlist file for reviewed exceptions (target: empty).
- detekt TODO closed or explicitly superseded by ArchRules; wire into `check`; CI green required.

P3 MIGRATE ALL SURFACES (acceptance: zero F1/F2/F3/F5/F6/F8 findings remain; per-module agent batches: f01, f02, f03, f04, f06 residuals, f10 residuals, f12+zoo, f13, app shell)
- Every screen/sheet migrated to P1 atoms; copy family fixed everywhere: sentence-case CTAs, card headers via WloCardHeader only, rationale/spec-id/wire-token leaks rewritten to user words, glyph junk replaced with icons/words, opposite-action label bug fixed, byte formatting unified, unreachable branch removed.
- Instrumented test assertions updated per batch; module gates green.

P4 VERIFY (acceptance: screenshot matrix of EVERY route vs DESIGN-SYSTEM.md, copy lint grep clean, full suites green)
- Full connectedDebugAndroidTest + JVM gates; deep-link-driven screencap of every registered route; visual pass against DESIGN-SYSTEM + mock frames; grep gates for leaked-rationale patterns (e.g. "never ", "on-device", spec IDs in strings) reviewed and cleared or whitelisted.

Est. ordering: P1+P2 one agent; P3 eight module batches (parallelizable in two lanes after P1); P4 orchestrator verification. Commit per phase.
