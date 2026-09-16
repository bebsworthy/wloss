# WLO-0087 — Weight UX, UI and micro-interaction review

Date: 2026-09-16 · source baseline: `66fd2c5` · scope: WLO-0086, W01–W17, S01–S09, F1–F12.

## 1. Verdict and evidence

**WLO has a credible Material 3 foundation, but the weight experience is not yet ready to be called polished.** The main problems are inconsistent information hierarchy, unreliable interaction/state transitions, disappearing labels and values, and incomplete translation of product truth into the UI. Changing radii or adding animation would leave the most serious problems intact.

The recommended direction is **a calm instrument for understanding change**: one unmistakable trend number, its chart directly beneath it, one reachable logging action, a compact optional goal, and progressively disclosed detail. Keep Inter, tabular numbers, the dark-first identity, neutral language, local ownership and explainable math. Use standard M3 anatomy and behavior to make those strengths easier to use.

### Method and limits

- Read the inventory, objective, master release contract/rulings, F06, design-system/IA documentation, previous review, implementation owners and relevant state/repository paths. Earlier prototypes and WLO-0067 are historical context, not proof of current behavior.
- Built the current debug app successfully with `./gradlew :app:assembleDebug --console=plain --quiet`, installed it on the available API 29 emulator, and inspected Weight, capture, Settings, Goals, Body fat and 200% font scale.
- Emulator: `wlo-api29`, 1080×2400, 420 dpi, existing populated fixture. Started with `-read-only -no-snapshot-save`; no weigh-in, deletion, restore or Fresh Start was committed. Temporary emulator changes do not alter the persistent AVD. Font scale restored to 1.0.
- **V** below means observed visually/UI tree and source; **S** means verified in source; **R** means a risk or proposed validation, not a reproduced failure. This is a complete inventory-level expert review, not a claim that every runtime state has passed QA.
- TalkBack speech, Switch Access, physical haptics, frame performance, Health Connect permissions, actual import/restore transactions, process-death recovery and large-window behavior remain acceptance work. No user usability study was performed.
- The production activity currently forces dark mode (`MainActivity.kt:138`). Light-theme contrast findings concern the implemented theme's readiness, not a light screen observed in the current app.

### Current visual evidence

| Evidence | What it establishes |
|---|---|
| [01 — Weight overview](WLO-0087-evidence/01-weight-overview.png) | Goal ladder precedes the chart; repeated Weight labels; Current trend row displays only “derived”; strong hero but crowded inline delta |
| [02 — Weigh-in](WLO-0087-evidence/02-weigh-in.png) | Real M3 sheet/input/picker actions; primary save is clear; tall date/time actions and tiny uppercase sheet heading compete for space |
| [03 — Goals](WLO-0087-evidence/03-goals.png) | Duplicate screen titles; populated target, pace and calorie fields have no visible labels; long form before save |
| [04 — Body selection does not respond](WLO-0087-evidence/04-body-selection-no-response.png) | After tapping Body fat, Weight remains selected and rendered |
| [05 — Weight at 200% font](WLO-0087-evidence/05-weight-font200.png) | Delta wraps awkwardly beside the hero; navigation truncates “Insights”; hierarchy consumes still more vertical space |
| [06 — Body after resume](WLO-0087-evidence/06-body-after-resume.png) | Background/resume publishes the earlier selection; three narrow tape inputs; empty-state cards precede capture |

### What should be retained

Real M3 `ListItem`, `Button`, `OutlinedTextField`, `ModalBottomSheet`, date/time pickers, navigation and top app bars are already used. Manual capture has explicit saving feedback, validation, decimal input, IME padding and meaningful stepper semantics. The post-save path has a one-shot haptic and polite announcement. Chart windows preserve time extent and offer recovery from empty windows. Logbook uses a lazy list and sticky month headers. Import/restore staging, raw-event storage, canonical trend access and safety-held forecasts are valuable foundations.

The custom swipe wrapper is a **documented exception**, not an automatic M3 violation: its release-gated, velocity-blind behavior is an explicit owner requirement and its content is a standard M3 list item. A chart canvas is likewise not wrong merely because it is custom; its semantics, data truth and accessibility must be complete.

## 2. Priority findings

P1 = correct before claiming release-quality weight UX; P2 = important experience improvement; P3 = optional refinement. Severity is about the user consequence, not how hard the change is.

### A01 · P1 · Daily-policy contradiction reaches the calculation [S]

**Evidence:** `WeighInRepository.kt:316` builds daily scalars with `minOf`; `LogbookViewModel.kt:242` independently calculates minima and marks the lowest event as “day's weight”. `MathDocsScreen.kt:49` and `WeighInUiState.LOWEST_COPY` describe minima. F06 §3/§7 names a consistent-time-window policy with median fallback; the benchmark document explicitly says its recommendation did not change production. The inventory understates this as contradictory copy.

**Impact:** The user cannot reconcile the selected reading, summary and published explanation. Changing only the wording would make the explanation false.

**Proposal:** Reconcile the release decision in the governing master doc, then make one versioned daily-selection service own the result and the selected source event(s). Implement any intended policy change with explicit recalculation/migration behavior and a one-time explanation. Keep every raw measurement. Logbook, chart, Hub and export should consume that authority, not reimplement it.

**Acceptance:** A fixture with a morning reading and a lower evening reading yields the same declared daily policy everywhere; historical changes are explained; method/version and contributing events are inspectable. This review does not silently authorize an algorithm migration.

### A02 · P1 · “Correct” destroys the original before correction succeeds [S]

**Evidence:** `WeighInViewModel.kt:452` clears the verdict/confirmation; `deleteFlagged` at 677 calls `deleteWeighIn` before opening the new sheet. Canceling that sheet leaves the original gone. The inventory's “retire” description is misleading: the current door is deletion. WLO-0067 recommended delete-and-reopen; this review supersedes that interaction recommendation.

**Proposal:** Keep the saved unusual reading until a successful replacement transaction. Open a draft with the original value and timestamp; offer the prior reliable value as a clearly labeled suggestion, never silently substitute it. “Cancel” returns to the original; “Save correction” atomically replaces it. Keep and Correct retain equal dignity, with Keep the default routine action.

**Acceptance:** Cancel, Back, process interruption and storage failure never delete the original. A successful correction updates derived values once. No double submit. Copy says “Saved reading differs from your trend” and describes uncertainty without judgment.

### A03 · P1 · Provenance sometimes replaces information instead of explaining it [V/S]

**Evidence:** `ProvenanceChip.kt` intentionally renders only a word and info icon; its `format` is used only in the accessibility description. `GoalProgressCard` passes it as the entire Current trend value. `RatiosCard` does the same for waist ratios and revealed BMI. Several callers omit `onClick`; the info icon remains even though it cannot explain anything. Confirmation suppresses provenance via `provenance = {}`. The Goals forecast omits `onExplain`. Chart `samples` come from daily scalars, despite surrounding descriptions calling them raw dots.

**Proposal:** Render `value + unit + provenance` as separate explicit responsibilities, preferably through the existing stat primitive after auditing it. A status-only badge must not pretend to be an action. Every derived number gets a working M3 assist-chip/link to its actual inputs, method, selection policy, freshness and limitations. Label daily-scalar dots correctly, or deliberately render all raw events as a separate series. Provide a legend and an accessible data view.

**Acceptance:** Sighted users can read every ratio/current value; screen readers do not announce the same value twice; every info affordance opens an explanation; daily samples are never advertised as every raw event. Math opens at the relevant explanation, rather than requiring a hunt through formulas.

### A04 · P1 · Weight/Body fat selection does not publish UI state [V/S]

**Evidence:** `uiState` is `data` (`WeighInViewModel.kt:382`), while `SectionChange` at 438 only sets the separate `section` flow. The screen receives the new section during a later reload. Reproduced: tap Body fat, no visible change; Home then resume reveals Body fat.

**Proposal:** Publish selection synchronously in render state, or combine selection with data explicitly. Keep independent scroll positions and drafts. Use `SingleChoiceSegmentedButtonRow` for the two related metric views, honoring the current same-screen segment contract. [Official component guidance](https://developer.android.com/develop/ui/compose/components/segmented-button).

**Acceptance:** One tap immediately changes selection and content without a repository refresh or lifecycle event. Rapid switching, rotation and Back restore the right section.

### A05 · P1 foundation / P2 latent light theme · Tokens do not yet form a complete M3 theme [S]

**Evidence:** `WloTheme.kt` customizes only some role pairs; container/error-container roles can fall through to library defaults. Light `onSurfaceVariant` reuses dark `#9AA6B5`; extended held/developing colors are also reused. WCAG relative-luminance calculations against white: secondary text **2.47:1**, held **1.91:1**, developing **2.56:1**; light tertiary is **5.69:1**. Dark outline against card surface is **1.35:1**—acceptable for decorative dividers, insufficient if it is the only essential input boundary.

**Proposal:** Complete semantic pairs and surface roles for both themes; distinguish informational held state from actual form error through icon, wording and semantics, while retaining the frozen no-alarm-red brand rule. Match each foreground to its intended background. Verify real rendered states; a token name alone proves nothing. [M3 theming in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3).

**Acceptance:** Normal text 4.5:1, large text/meaningful graphics 3:1; selected/invalid states identifiable without color; all container pairs and focus states covered. Dynamic color remains optional and must not recolor fixed data semantics. Do not enable light mode before this passes.

### A06 · P2 hierarchy / P1 continuity · Weight makes detail outrank the primary task [V/S]

**Evidence:** `WeightScreen` orders hero → full goal ladder → chart → all compressed history. The first viewport contains no chart. While confirmation exists, the `if (confirmation == null)` block removes hero, goal, chart, history and notice. There is no explicit receipt-entry transition in this composable despite motion claims in docs.

**Proposal:** Put the chart immediately under the trend; reduce the goal to a compact summary with one next milestone and a details action. Place logging within thumb reach. Keep the dashboard mounted after save and update a single receipt region; Done may dismiss that region but must not unlock the dashboard. An outlier decision remains persistent until handled, without a second competing success panel.

**Acceptance:** A normal repeat weigh-in does not require an extra acknowledgment to inspect history/chart. Save causes no full dashboard disappearance or lost scroll. Goal history remains available through disclosure. At normal scale on the reference compact window, chart and primary action appear before the full ladder.

### A07 · P2 · Shared styling flattens hierarchy and creates inconsistent control shapes [V/S]

**Evidence:** `WloCardHeader` uppercases 11sp labels; `WloSheet` reuses that header for a modal task title. `WloButtons` forces chip radius onto buttons, while onboarding uses default button shapes. Static `WloCard` renders a hand-styled `Surface` although standard Card variants can do the job. Multiple display typography roles map to the same hero and headline roles collapse too. `SelectChip` is used for modes, units, source choices and settings, not just filters.

**Proposal:** Standard M3 component variants, sentence-case headings, differentiated type roles, default component shape relationships, fewer borders. Keep brand changes through supported parameters. [Native Card variants](https://developer.android.com/develop/ui/compose/components/card). Use segmented controls for short exclusive sets; radio list items for long choices; exposed dropdowns for mapping; text buttons for low-emphasis help. Keep chips for real filtering or contextual assistance.

**Acceptance:** A component gallery documents anatomy, states and exception rationale. Sheets have a readable task title; grouped content has section hierarchy; forms/lists need not each sit inside a card. Do not shrink standard ListItem metrics to recover space—remove redundant rows instead.

### A08 · P2 · Goal and history affordances lack a useful destination [S]

**Evidence:** Weight exposes no Goals callback; GoalProgressCard's no-goal state offers prose only. Every compressed-history row invokes the same parameterless `onOpenLogbook`, so a month tap does not select that month.

**Proposal:** “Set a goal” / “Edit goal” on the goal summary. Pass the bucket's date interval to logbook, preserving origin and selected event on return. Empty buckets explain no readings in the interval and offer Add reading with that date, rather than opening an unrelated newest-first page.

**Acceptance:** Goal editing is one tap from Weight. Tapping a historical bucket opens that period and Up restores the overview's position. No-goal tracking stays first-class.

### A09 · P1 · Populated forms become anonymous; save state is inconsistent [V/S]

**Evidence:** Goals `EditorField`, body `NumberField`, and Profile fields use placeholder-only names. Goal date uses `KeyboardType.Decimal` and requires an ISO string. Goal/current weight can fall back to profile start weight while labeled Current trend. Logbook edit and Goals/Profile save controls lack the manual-capture saving treatment; Logbook `saveEdit` launches without an in-flight guard. Body uses three side-by-side fields on compact screens.

**Proposal:** Persistent label, explicit suffix/unit, suitable keyboard and field-associated supporting/error text for every input. Full-width compact fields; pair them only when measured space and text size permit. Use the existing M3 date picker pattern. Distinguish current trend, starting reading and unavailable. One save state machine per transactional form: editing → saving → saved/error; dirty draft survives recoverable failures.

**Acceptance:** Name, unit, value and error remain visible and accessible after typing. Date entry never depends on finding a hyphen on a numeric keyboard. Repeat taps cannot create multiple mutations; leaving a dirty form offers Keep editing/Discard, without interrupting untouched forms.

### A10 · P2 / P1 dead ends · Goal setup asks for technical decisions without sufficient guidance [S]

**Evidence:** First-run uses “Continue or skip” as one ambiguous action and “Open Weight” even when file import is selected. File import does navigate directly—retain that. Health Connect is explicitly deferred but has no equivalent completion handoff. Goals no-target state offers “No plan yet” without a Create action. The existing editor exposes target, pace, date, kcal, safety and forecast in one long block. Safety is a transactional questionnaire, not an immediate switch setting.

**Proposal:** Unit → optional goal → reading source. Name the forward action (“Continue”, “Import file”, “Open Weight”) and make Skip explicit where relevant. Use radio rows for manual/file/Health Connect/skip. Offer a goal-creation path after skipped setup. In Goals, show goal mode and target first; show pace only when relevant; show forecast prerequisites when the user requests a forecast. Keep safety screening mandatory for automated targets/dates. Persist answers and summarize them with Edit; never silently infer a safe answer.

**Acceptance:** Skip goal and skip reading both lead to a usable tracker. The next destination matches the button. No-target users can create a goal without starting diet setup. Missing profile/forecast inputs provide direct repair links and preserve the draft. Any change to goal semantics/safety goes through the governing contract.

### A11 · P1 · Delete/undo is thoughtfully constrained but incomplete for recovery [S/R]

**Evidence:** Swipe wrapper explains its justified deviation and provides a TalkBack action. Visible delete is gesture-only; no ordinary delete control appears in the edit sheet. Undo uses a fixed **4.5s** ViewModel delay while a separate composable animation starts its own countdown. The notice is inserted at the top of the month, not the deleted row's exact slot; a long month can put it off-screen. Edit save has no busy state. Logbook lacks explicit distinct loading/error/empty presentations.

**Proposal:** Preserve the approved swipe behavior. Add Delete through edit-sheet overflow or a separate text action. Anchor Undo visibly to the affected row/viewport, use one authoritative expiry and [Android's accessibility timeout recommendation](https://developer.android.com/reference/android/view/accessibility/AccessibilityManager#getRecommendedTimeoutMillis(int,%20int)). Define second-delete behavior; do not silently overwrite a user's recovery opportunity. Show an explicit delete failure and refocus the restored/neighboring row.

**Acceptance:** No velocity-only or vertical-scroll delete; interrupted swipe cancels. Touch, TalkBack, Switch Access and keyboard all reach Delete/Undo. Animation scale zero changes animation, not recovery time. Timer, actual deadline and restored state agree after recomposition/backgrounding. No dialog is required for the established reversible swipe path.

### A12 · P1 · Body measurements have inconsistent series and save promises [S/V for layout]

**Evidence:** `WeighInViewModel` flattens every BODY_FAT event to `ChartPoint(day,value)` without method/source, despite copy promising separate series. Waist/profile/tape inputs stay in cm while the master unit ruling includes length. The body segment has no visible period selector although data follows the Weight window. `BodyFatViewModel.saveToLogbook` saves tape events, then estimate and attributes separately; failure of the final append can say “nothing changed” after tape writes. Success only updates notice; the parent chart is not directly refreshed.

**Proposal:** Body overview: metric selector, explicit period, method/source legend and Add measurement. Put calculator entry before empty ratios, or open a focused measurement form. Keep Navy, RFM and imported measurements distinguishable. Use a single atomic measurement transaction (or honest partial-result recovery if truly unavoidable), block duplicate submissions, invalidate stale estimates on input changes, refresh chart immediately. Provide cm/in conversion consistently or explicitly reconcile scope before promising it.

**Acceptance:** Methods cannot masquerade as one homogeneous sequence; tape/estimate/provenance save together; notice matches actual writes. A saved record appears without leaving the screen. Missing height/required formula input links to Profile. Show BMI remains opt-in, with an actual number and a Hide action.

### A13 · P1 · CSV import is not ergonomically or semantically complete [S]

**Evidence:** `MappingRowEditor` puts eight chips in an ordinary Row, with no horizontal scroll/wrap. Weight/trend target units are fixed to kg. Report Back invokes `reset`, which discards selected bytes and state. File opening reads all bytes synchronously in the picker callback; failed reads silently do nothing. Every stage also displays a filled Close button, competing with Apply/Done and remaining present while applying.

**Proposal:** A mapping list: source column + sample value + M3 exposed dropdown for target + source-unit selector when applicable. Show parsed date and converted weight examples before commit. “Edit mapping” returns to the existing draft. One primary stage action; cancellation is secondary and follows the real transaction boundary. Run IO off the UI thread, with readable recovery for revoked/unreadable files.

**Acceptance:** All mappings reachable at 320dp and 200% text. kg/lb source selection independent of display preference; wrong-unit imports cannot proceed silently. Missing date/duplicate targets explain recovery. Back keeps the file/mapping; large file loading remains responsive; applying cannot be duplicated. Recovery-pending copy never falsely promises rollback.

### A14 · P1 trust · Fresh Start's reversal promise is not established [S/R]

**Evidence:** Vault UI says “after a relapse”, “nothing is deleted”, and “reversible from a restore”; preview counts diary entries only. `VaultAdapters.freshStartHide` archives diary rows, archives active profile and clears onboarding completion. It does not create a backup or describe what happens to the weight history under the old profile. These are materially different from a reversible visible chart marker. A restore path existing does not prove a usable backup exists.

**Proposal:** Remove judgmental relapse framing. Before redesigning the button, define the exact weight/profile/goal/history contract and recovery. Prefer an in-app reversible archive marker consistent with R-B7. If that cannot be implemented now, withhold the action from the weight release until a truthful scoped contract exists. A destructive confirmation must list counts and consequences, say which backup exists, and separate Cancel from the explicit action.

**Acceptance:** Demonstrate round-trip restoration without assuming an external backup. Preview reflects weight and goals as well as diary data. Partial failure cannot claim success. Do not reintroduce the canceled automatic return prompt.

### A15 · P2 · Navigation and adaptation are only partly resolved [V/S/R]

**Evidence:** Nested routes get both shell TopAppBar title and child WloScreenTitle (observed Goals/Settings). Weight is correctly the default now, but five destinations still include an Insights placeholder. R-D2 describes an older destination set contrary to the release contract and current shell. Rail adaptation exists; feature screens remain full-width single columns. 200% screenshot truncates a bottom-nav label.

**Proposal:** One title owner per route. Keep the current shell until its governing navigation decision is reconciled; recommended weight-release simplification is Weight plus meaningful supporting destinations, with broader features exposed only when usable. Do not invent empty tabs merely to reach a count. Wide Weight gets a supporting pane; wide logbook gets list/detail. [Android canonical layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/canonical-layouts). Keep return context from Settings, import and deep links.

**Acceptance:** No duplicate titles; no unexplained blank primary destination in the selected release configuration. Resizing preserves state and usable reading width; keyboard focus and fold occlusion tested. Navigation changes require an explicit R-D2 amendment, not an incidental UI patch.

### A16 · P2 · Settings and reminder choices add avoidable effort [V/S]

**Evidence:** AI Studio appears before weight preferences; explanatory copy discusses app architecture. Six reminder times are packed into a Row. Permission denial keeps toggle off, which is good, but there is no specific denial/system-blocked recovery presentation. Notification title always says “morning” even when evening is selected; scheduling is intentionally approximate.

**Proposal:** Order Weight preferences → Goals/Profile → Data & connections → Privacy → optional broader tools. Use one time row and M3 TimePicker, respecting 12/24h convention. Explain “Around 07:30”; show Disabled in Android with Open notification settings when appropriate. Neutral notification title “Weigh-in reminder”; preserve direct capture deep link and app-lock gate.

**Acceptance:** Any desired time is selectable; permission/status accurately reflects the OS; no repeated permission loop. Unit changes propagate without relabeling unconverted drafts. Cold/warm notification taps open exactly one capture flow; returning from lock preserves draft.

### A17 · P2 · Motion is specified more richly than it is implemented or validated [S/R]

**Evidence:** Confirmation uses static composables; chart canvas has no inspection gesture or point-level semantics. Forecast has animation, logbook has bespoke animations, and haptic primitives exist. WloMotion has a ReducedMotion spec, but defining a token is not proof all custom lifecycle/timers honor it. Compose animations may already honor system duration scaling; do not assume all fail.

**Proposal:** Adopt the motion table in §6. Prioritize state continuity, stable numbers, useful focus and feedback before expressive flourish. Do not animate weight as counting from zero or celebrate a lower number. Keep chart values fixed while animating only entrance/selection. Use frame evidence to tune transitions; no dependency upgrade solely for “Expressive”.

**Acceptance:** Reduced-motion states remain fully understandable; data commits and Undo deadlines are independent of animation; no repeated haptics on recomposition; drag/selection is interruptible; chart has touch and non-gesture equivalents. Physical-device haptic validation remains necessary.

### A18 · P1 release gate · Visual snapshots cannot establish complete accessibility [V/S/R]

**Evidence:** 200% font exposes layout strain. Source has useful semantics and 48dp controls, but also 44dp design tokens, placeholder-only fields and generic chart descriptions. Actual M3 touch expansion means a visually 32dp chip is not automatically a 32dp target. Do not report every small visual control as a failed touch target without measuring semantics/hit bounds. [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).

**Proposal:** Treat §8 as release acceptance, with recorded evidence for each state. Use native defaults, preserve scalable text, meaningful heading/selection/error semantics and explicit focus recovery. Defer visual approval until the P1 failures are corrected and tested.

## 3. Design intent: translating the requested qualities

| Quality | Concrete product behavior | Evidence of success |
|---|---|---|
| Seamless / effortless | Launch → capture → save → updated trend; draft survives interruption | Repeat log without acknowledgment detour; no lost values |
| Fluid | Stable context, interruptible transitions, immediate selection | No blank dashboard or delayed Body fat switch |
| Intuitive | Named fields, recognizable M3 controls, literal next action | First-time users can log, edit and find goals without guidance |
| Polished | Coherent type/surface roles and complete states | Same control behaves identically across capture, goals and import |
| Elegantly functional / streamlined | Chart and summary first; detail on request | Fewer repeated labels/cards, no loss of available statistics |
| Balanced / clarity | Raw reading, trend and forecast visibly distinguished | Users can explain which number was measured and which was derived |
| Organic | Reachable action, flexible layout, system input conventions | One-handed use, text scaling, keyboard and wide-window tasks work |

Expression should come chiefly from one strong number, restrained teal action emphasis, crisp chart geometry and a brief acknowledgment of completion. Avoid decorative gradients, body imagery, guilt language, persistent shimmer and animation tied to weight direction.

## 4. Screen-by-screen target experience

### Weight overview (W02/W06/W08/W15)

Compact order:

1. Standard top app bar: Weight; contextual overflow for settings/import if needed.
2. Single-choice segmented buttons: Weight | Body fat. Maintain the existing same-screen contract.
3. **Weight trend** label; large value + smaller baseline unit; separate supporting line for “7-day change −0.2 kg”. Working derived-info assist chip. “Latest reading 77.0 kg · Today, 06:30” below.
4. Chart and period control immediately below, visually part of the same story. Use a short single-choice period control; if labels no longer fit, use a labeled dropdown, not clipped segments. Default period decision should be explicit; keep 90 days until changed deliberately.
5. Compact goal summary: target/range, remaining distance when meaningful, next milestone, Edit goal. Full milestone history behind Details. No goal: quiet Set a goal text action.
6. Recent history preview, then Full logbook; retain richer compressed tiers behind disclosure.
7. A single M3 Extended FAB “Weigh in” in Scaffold's action slot, above navigation/insets. Reserve list bottom padding. For windows/large text where a FAB obscures content, use a standard filled button in a bottom action region. Do not retain a second competing filled Weigh in inside the hero.

Expanded: primary pane contains trend/chart/history; supporting pane contains goal details and selected reading. Use the window's capacity, not device identity. At intermediate widths, a rail and bounded content are reasonable; two panes only when both are usable.

### Capture and result (W03–W05)

M3 ModalBottomSheet with normal task title, labeled numeric field and unit suffix. Initial focus should not force a keyboard when a prefill permits zero-typing capture. Keep ±0.1 controls close to the number, with full accessible names. Present date/time as a concise labeled row; wrap vertically when needed. Use existing native dialogs. Keep the primary save visible above IME with scrollable content; full-screen form is a fallback for constrained height.

After save, dismiss keyboard/sheet and update the overview in place. Show a persistent compact saved receipt near the hero with reading/time and an Edit action; keep trend/raw distinction and sparse-data explanation. The user can continue immediately. Outlier state uses this same region with Keep/Correct, never two contradictory completion panels.

### History and logbook (W08–W11)

M3 LazyColumn + ListItem; sticky month heading includes count and clearly named summary definition. Date/time/source in readable slots, weight aligned consistently; selection marker refers to the actual daily policy. No card around each row. A bucket link opens the relevant period. Editing reuses capture components and transaction states. Keep the custom swipe exception and inline undo; add a discoverable non-gesture delete path. Expanded list/detail avoids opening another sheet over an already visible detail pane.

### Goals and forecast (W16/W17)

One top app bar title. First section: mode, target/range and current-weight provenance. Next: pace or maintenance controls as relevant. Safety section: plain questions, radio choices with explicit unanswered state; summary plus Edit once answered. Forecast section shows its eligibility and direct prerequisite actions. Optional date and energy assumptions belong here, with persistent labels; never pretend a date is reliable because a user typed it.

Forecast remains range-first: labeled expected trajectory, three bands, goal reference, arrival range, data-quality state and working explanation. Keep developing/held/withheld distinct; a blank chart is not a state. Save goal is the single primary action; “Creates a new version” is supporting text. Version history is secondary and opens a before/after preview before Restore. No safety thresholds or eligibility logic change as part of styling.

### Body composition (W12–W14)

One Body fat section with metric choice (Body fat % / Waist), visible time period, method/source-aware chart, Add measurement. Empty state should lead directly to capture instead of stacking empty cards. Calculator uses labeled single-column tape inputs on compact windows, method explanation and height/profile repair link. Estimate → review → save is appropriate because the value is derived; after estimate, Save becomes primary and Recalculate secondary. Ratios appear only with usable data; render numbers with provenance. BMI remains hidden until requested and can be hidden again.

### Setup, preferences and data (W01/S01–S09)

First run offers a short optional route to goals and a literal import/capture handoff. Hub links reuse the same capture and canonical trend; no duplicated entry implementation. Settings prioritizes the weight ritual and direct repair links. Data Vault groups Connect/import, Export, Backup/restore and separately Restart/archive. Use M3 list items and stage-based forms; describe user consequences rather than Room, store internals or routing structure. Keep system file picker and permission sheets.

## 5. Component and theme handoff

| Purpose | Standard component / role | Required behavior |
|---|---|---|
| Root/nested frame | Scaffold + TopAppBar; navigation bar/rail | One title owner; state-preserving navigation; insets |
| Weight/body view; short exclusive modes | SingleChoiceSegmentedButtonRow + SegmentedButton | Selected state/label, immediate response; alternate layout when constrained |
| Long source/profile/safety choices | ListItem with RadioButton and selectable-group semantics | Whole row selects; one meaningful focus target; question context announced |
| CSV mapping | ExposedDropdownMenuBox with labeled field | Complete choice list, sample values, independent source-unit control |
| Logging action | ExtendedFloatingActionButton, or filled Button in constrained layout | One primary action; visible focus/pressed/disabled state |
| Capture/goal fields | OutlinedTextField | Persistent label, suffix, proper keyboard, supporting/error text |
| Date/time | M3 DatePicker/TimePicker dialogs with input alternatives | Locale presentation; draft retained; focus returns to invoking control |
| Static grouped summary | Filled Card where containment helps; otherwise section + list | Use standard Card variant, no compulsory outline |
| Provenance | AssistChip when actionable; plain status text/badge otherwise | Value separately visible; no dead info icon |
| Help/details | TextButton or labeled navigation ListItem | Help does not compete with Save |
| Quality/error | Inline icon + text + recovery action; dialog only for decisions | Distinct semantics; text not hue alone |
| Saving | Standard progress indicator + button label/state | Single-flight; no layout jump; honest completion |
| Undo | TextButton in anchored inline status; standard progress primitive if needed | Shared deadline, accessible timeout, action reachable |
| Charts | Custom Canvas is justified; M3 controls around it | Legend, data alternative, source/method and selected point semantics |
| Swipe | Existing documented custom gesture wrapping ListItem | Retain velocity-blind release gate; add non-gesture path |

Theme: `surface` for page, `onSurface` for primary text, `onSurfaceVariant` for supporting text, `surfaceContainerLow/High` for meaningful containment, `outlineVariant` for optional separators, complete `primary/onPrimary` and container pairs for emphasis. Fixed data series and WLO quality roles remain separate from wallpaper colors. Use an accessible WLO error pair without introducing red contrary to R-D1. Normal body and labels use distinct M3 typography roles with Inter; numeric additions retain tabular figures. Proposed spacing rhythm is 16dp screen margins and 8/16/24dp group separation, while component internal metrics remain native defaults. These are WLO proposal tokens, not a claim that every screen must use a prescribed M3 margin.

Amend DESIGN-SYSTEM's 44dp interactive-row guidance and web 24dp floor: Android interactive areas target at least 48dp. Do not force chip visual height to 48dp when the component already supplies a sufficient non-overlapping target. Remove “haptics become the primary feedback” as an accessibility assumption: visible and spoken feedback remain necessary even with haptics disabled.

## 6. Micro-interaction contract

Durations below are proposed product targets, not exact universal M3 constants. Prefer native component transitions; tune custom motion on measured devices.

| Trigger | Response and motion | Haptic / semantics | Reduced motion / interruption |
|---|---|---|---|
| Press action | Native ripple/pressed state immediately | Accessible action name; no extra buzz for every tap | Native state remains visible |
| Change segment/range | Selection immediate; brief 150–200ms content crossfade if useful | Announce selected view/window once | Instant state; never wait for animation to publish selection |
| Open capture | Native sheet transition; preserve origin context | Focus title/first useful control; do not auto-open IME for prefill | Native duration scaling; Back closes with draft rules |
| Step weight | Exact displayed 0.1 adjustment; stable-width number | Existing unit-aware action name; optional subtle tick | Immediate value, no digit roll needed |
| Save | Disable duplicate submit; Saving label/progress; update after persistence | One light confirmation haptic, one polite final announcement | Same lifecycle without motion |
| Normal receipt | Brief fade/size transition in a stable region | “Saved [reading]. Weight trend [value/state].” | Static final receipt; no acknowledgment gate |
| Unusual reading | Persistent descriptive state; no shake/alarm animation | Same kind feedback regardless of direction | Keep/Correct always visible |
| Correct | Draft editing, then replace on success | Announce corrected value; Cancel preserves original | No premature mutation |
| Inspect chart | Direct selected marker and exact date/value/source; no global rescale per finger move | Keyboard previous/next and accessible list equivalent | Static marker; final selection announced, not every animation frame |
| Change forecast input | Debounce computation; label preview; preserve scroll | Announce final changed forecast state only | No repeated band bloom/shimmer |
| Delete drag | Finger-following row; explicit armed cue before release | Existing threshold tick; no velocity shortcut | Gesture remains direct; cancel restores row |
| Delete result/Undo | Inline persistent fact until shared expiry; short structural transition | Announce deletion + available Undo once; recover focus | Recovery time unchanged; omit draining animation if necessary |
| Import/restore | Measurable progress if known, otherwise honest indeterminate state | Announce stage changes, not each row | Transaction independent of animation/navigation |

No full-screen celebration, confetti, ambient shimmer or changing sound based on gain/loss. The signature moment is a quiet, unmistakable successful save.

## 7. Full inventory coverage and disposition

V = runtime sample plus source; S = source review. Each row's acceptance inherits §§4–6 and §8.

| ID | Surface | Coverage / recommendation |
|---|---|---|
| W01 | Setup | S; A09/A10; explicit skip and handoff, source radio list, preserve draft |
| W02 | Weight overview | V; A03–A08; chart-first hierarchy, visible values, reachable logging |
| W03 | Manual sheet | V; A02/A09; retain native pickers/validation; improve title/reach and interruption |
| W04 | Confirmation | S; A03/A06/A17; keep useful trend receipt, dashboard remains usable |
| W05 | Outlier | S; A02; draft correction, cancellation retains reading |
| W06 | Trend | S; A01/A03/A17; truthful dots, legend/data access; WLO-0054 owns advanced tuner |
| W07 | Math | S; A01/A03; plain-language actual-input explanation before formulas |
| W08 | Compressed history | S; A08; bucket-to-period navigation and disclosed tiers |
| W09 | Logbook | S; A01/A11; selected-policy markers; explicit load/empty/error |
| W10 | Edit sheet | S; A09/A11; save state, persistent labels, visible delete alternative |
| W11 | Delete/undo | S; A11/A17; preserve exception, accessible deadline and anchored recovery |
| W12 | Body segment | V; A04/A12; immediate selection, method-aware charts and period |
| W13 | Calculator | V layout/S save; A09/A12; labeled form, atomic save, profile repair |
| W14 | Ratios | S; A03/A12; show numerical results, provenance, BMI hide/reveal |
| W15 | Goal progress | V; A03/A08; compact next step and edit/create path |
| W16 | Goals editor | V; A09/A10; labeled input hierarchy, eligibility and versions |
| W17 | Forecast | S; A03/A10/A17; range/state-first, working assumptions explainer |
| S01 | Hub | S; A03/A06; canonical unit/number parity, shared capture, actual dot semantics |
| S02 | Settings | V; A07/A16; weight-first ordering, time picker, true permission state |
| S03 | Profile | S; A09/A12; labeled facts/length units, meaningful validation and save |
| S04 | Health Connect | S; A13; preserve permission granularity, add update/retry/return-to-weight actions |
| S05 | File import | S; A13; dropdown mapping, units, retained draft, IO/errors |
| S06 | Export/backup/restore | S; A13/A14; retain SAF/staging, remove technical copy, scope/result clarity |
| S07 | Manual Fresh Start | S; A14; reconcile reversible contract before exposure |
| S08 | Reminder | S; A16; neutral copy, approximate time, direct capture and lock return |
| S09 | Adaptive shell | V compact/S expanded; A15/A18; single title, usable destinations, supporting panes |

Journey checks:

| Journey | Proposed completion criterion |
|---|---|
| F1 First launch | Unit required; goal/reading optional; Finish lands where its label promises; all Back steps preserve input |
| F2 Fast weigh-in | Open, adjust/type, Save; feedback without blocking dashboard; kg/lb and comma/dot; no duplicate save |
| F3 Outlier | Keep unchanged, or Correct draft → atomic replacement; cancel/error retains original |
| F4 Understand trend | Explain measurement vs daily scalar vs trend; inspect period/data; advanced preview never changes canonical headline |
| F5 Correct history | Bucket opens its period; edit retains context; delete is explicit and recoverable with any supported input method |
| F6 Goal | Create/edit from Weight; all three modes; safety and quality gates stay honest; restore version shows diff |
| F7 Body | Profile repair → method/measurements → estimate → save → refreshed method-specific series; ratios visible |
| F8 Health Connect | Availability/permission → import → actionable receipt → updated Weight; partial access described per metric |
| F9 File history | Pick → samples/mapping/units → report → commit → Weight; backward navigation retains draft |
| F10 Daily ritual | Unit preferences propagate; time picker accepts desired time; OS permission and reminder state agree |
| F11 Hub glance | Same canonical trend/unit as Weight, same capture route, correct sparse gate and direct explanation |
| F12 Protect/port/restart | Lock preserves draft; selected-file export verified; restore parity; Fresh Start reversal demonstrated |

Non-surfaces from the inventory remain excluded: no widget, scale OCR, automatic Fresh Start, discreet weight mode, custom-metric builder, Bluetooth pairing or milestone celebration is proposed for reinstatement.

## 8. Acceptance and evaluation plan

### State matrix

| Family | Required fixtures and expected behavior |
|---|---|
| Loading/error | First load, cached refresh error, retry; stale values explicitly distinguished from empty data; no false zero |
| Trend | 0/1/2/established readings, same-day multiples, flat series, long lapse, empty selected window, multi-year history; no fake extrapolation or collapsed time |
| Capture | kg/lb, comma/dot, malformed/blank/out-of-range input, future/backdated time, keyboard, date/time dialogs; local error + input preserved |
| Persistence | Save/error/retry/double tap; edit/delete/undo; background, rotation, process recreation, app lock; exact transaction and draft outcomes |
| Goals | No targets, missing facts, each goal mode, held/unanswered safety, developing/held/available forecast, invalid date, restore version; visible recovery and no false precision |
| Body | Missing sex/height, Navy/RFM, required hip input, invalid tape, stale estimate, mixed methods, save/attribute failure, measurement refresh |
| Data | HC unavailable/update/denial/partial/full/revoked; CSV mapping/units/duplicates/skips; unreadable file, failed commit, recovery pending; backup missing and restore error |

### Device, accessibility and performance matrix

- Compact widths 320/360/412dp; landscape with IME; 599/600 and 839/840dp boundaries plus an expanded window. These are chosen test cases; use current window APIs rather than hard-coded device categories.
- Font scales 1.0, 1.3, 2.0; long labels, RTL and comma-decimal locale; kg/lb and length-unit contract. Numeric text must reflow as complete meaningful groups; do not ellipsize the primary value to preserve decoration.
- TalkBack: reading order, field labels/errors, modal focus, one save announcement, chart data access, selected state, Delete/Undo and restored focus. Repeat with Switch Access and hardware keyboard. UIAutomator trees are not a substitute for listening to TalkBack.
- Measure actual targets and contrast in enabled/selected/focused/error states; no overlapping expanded targets. Verify visible focus and non-color cues.
- Animator scale zero plus haptics disabled; then physical-device haptics enabled. Same state and recovery time in every case.
- Frame trace on a named midrange device: target smooth 60Hz interaction (about 16.7ms frame budget); record jank distribution, not just average. Profile long-list scrolling, chart-window changes, sheet/IME and large import. Emulator screenshots do not validate performance.

### User-task validation (proposed, not performed)

Run five formative sessions with numbers-oriented users, including a large-text or assistive-technology user. Test first reading, repeat reading, correction, explain trend, find/edit goal and import history. Proposed acceptance targets: at least 4/5 complete each core task without coaching; repeat entry median ≤10s; everyone can distinguish latest reading from trend and understand that forecast is a range; nobody loses a reading by canceling correction. Record task completion, mis-taps, errors and comments locally with consent; no production telemetry is needed. These small-sample targets guide iteration, not statistical claims of universal usability.

## 9. Delivery sequence and tracker

The review is complete; implementation remains proposed. New tasks are in backlog, with scope and acceptance criteria. WLO-0054 remains the owner of advanced smoothing, including its unresolved question. Do not duplicate it or silently overrule R-A2's visible-tuner requirement.

1. **Truth and recovery:** A01–A04, transaction failures, field labels, CSV mapping. These are not contingent on a visual redesign.
2. **Shared M3 foundations:** semantic theme, typography, control map and one-title rule. Approve a small component gallery before changing every feature.
3. **Primary loop:** Weight hierarchy, capture/result, goal access and logbook continuity. Compare concrete before/after screens at normal and large text.
4. **Adjacent flows:** goals/profile/body forms, imports/backup/reminders and adaptive supporting panes.
5. **Finish and prove:** restrained motion, full state matrix, physical-device and formative usability evidence. No “polished” sign-off from source inspection alone.

| Ticket | Proposed work | Findings |
|---|---|---|
| [WLO-0088](../tasks/WLO-0088-P1_Reconcile_Weight_Daily_Scalar_Policy_And_Truthful/spec.md) | P1: Reconcile weight daily-scalar policy and truthful provenance | A01, A03 |
| [WLO-0089](../tasks/WLO-0089-P1_Make_Weigh_In_Correction_Transactional_And_Preserve/spec.md) | P1: Make weigh-in correction transactional and preserve dashboard continuity | A02, A06 |
| [WLO-0090](../tasks/WLO-0090-P1_Repair_Weight_Section_State_And_Body_Measurement/spec.md) | P1: Repair weight section state and body-measurement integrity | A04, A12 |
| [WLO-0091](../tasks/WLO-0091-P1_Rebase_WLO_Theme_And_Shared_Controls_On_Material_3/spec.md) | P1: Rebase WLO theme and shared controls on Material 3 | A05, A07 |
| [WLO-0092](../tasks/WLO-0092-P2_Redesign_Weight_Overview_Hierarchy_And_Goal_Entry_Points/spec.md) | P2: Redesign Weight overview hierarchy and goal entry points | A06, A08 |
| [WLO-0093](../tasks/WLO-0093-P1_Make_Goals_And_Profile_Forms_Labeled_And_Recoverable/spec.md) | P1: Make goals and profile forms labeled and recoverable | A09, A10 |
| [WLO-0094](../tasks/WLO-0094-P1_Make_Logbook_Editing_Deletion_And_Undo_Accessible/spec.md) | P1: Make logbook editing, deletion and undo accessible | A11 |
| [WLO-0095](../tasks/WLO-0095-P1_Repair_CSV_Mapping_And_Data_Movement_Recovery_UX/spec.md) | P1: Repair CSV mapping and data-movement recovery UX | A13, A14 |
| [WLO-0096](../tasks/WLO-0096-P2_Align_Weight_First_Navigation_And_Adaptive_Layouts/spec.md) | P2: Align weight-first navigation and adaptive layouts | A15 |
| [WLO-0097](../tasks/WLO-0097-P2_Simplify_Reminder_Settings_And_First_Run_Handoffs/spec.md) | P2: Simplify reminder, settings and first-run handoffs | A10, A16 |
| [WLO-0098](../tasks/WLO-0098-P2_Add_Accessible_Trend_Exploration_And_Restrained_Motion/spec.md) | P2: Add accessible trend exploration and restrained motion | A03, A17 |
| [WLO-0099](../tasks/WLO-0099-P1_Validate_Weight_UX_Against_The_Full_Review_Acceptance/spec.md) | P1: Validate weight UX against the full review acceptance matrix | A18; all |

### Contract amendments to make explicit

- **Daily policy:** resolve master R-B5, F06 release language, benchmark recommendation and live minima together. Do not just update the explainer.
- **Navigation:** R-D2 is stale relative to §2.0. Any release-shell simplification must amend the master first.
- **Smoother disclosure:** R-A2 says visible; WLO-0054 proposes collapsing it and has an open question. The recommendation is to disclose advanced controls deliberately, subject to that ruling being reconciled. No silent removal.
- **Chart implementation:** R-D4 mentions Vico for standard charts, whereas the live trend is custom Canvas. Record the accepted exception or a measured replacement decision; rewriting it just for library conformity is not the highest-value work.
- **M3 metrics:** update older density/motion instructions to the later standard-components-first rule; preserve R-D1/D3 brand constraints and the documented swipe exception.
- **Fresh Start:** R-B7's reversible marker and current profile retirement are different experiences. Resolve the contract before claiming reversibility.

## 10. Material 3 self-audit

Score: 0 incorrect/missing; 1 partially correct; 2 ready. These are current implementation scores from this review, not usability-study results. The proposed design is a handoff and has not earned a production-readiness score.

| Category | Score | Concrete reason / exit condition |
|---|---|---|
| Task clarity | 1 | Weigh in is identifiable; goal and import routes still indirect/ambiguous |
| Information hierarchy | 1 | Strong hero; chart buried, repeated titles, goal detail too prominent |
| Component semantics | 0 | Placeholder-only forms; non-actionable info marks; provenance used as the only visible value |
| Token discipline | 1 | Central theme exists; incomplete role pairs and mixed control styling |
| Adaptive behavior | 1 | Bar/rail shell exists; content panes/reflow are not complete |
| States & feedback | 0 | Body selection fails to render; correction deletes before completion; partial-save promise can be false |
| Accessibility | 0 | Filled fields lose names; numerical values absent visually; full assistive-input validation outstanding |
| Expressive restraint | 1 | No excessive spectacle; repetitive boxes and competing emphasis still weaken hierarchy |

**5/16; not ready for approval.** This is not a verdict that the app must be rebuilt. Preserve the sound M3 components and data architecture, correct the broken contracts, then simplify the presentation. Any 0 in semantics, states or accessibility prevents approval under the review skill.

## 11. Source index and external guidance

Implementation references below resolve the symbols cited in findings. Line numbers refer to baseline `66fd2c5`; use symbols after subsequent edits.

| Source | Review anchors |
|---|---|
| [WeightScreen.kt](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/WeightScreen.kt) | WeightScreen 102; GoalProgressCard 347; confirmation 442; trend 555; tuner 646; history 690; capture 737; body 873; ratios 941 |
| [WeighInViewModel.kt](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/WeighInViewModel.kt) | uiState 382; SectionChange 438; CorrectFlagged 452; deleteFlagged 677; body points 772 |
| [WeighInRepository.kt](../../core/data/src/commonMain/kotlin/app/wlo/core/data/WeighInRepository.kt) | dailyScalars 316; lowestOfDay; currentTrend |
| [LogbookScreen.kt](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/LogbookScreen.kt) | LogbookScreen; EditSheetContent; LogbookRow; InlineUndoRow; UndoCountdown |
| [LogbookViewModel.kt](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/LogbookViewModel.kt) | flushMonth 240; saveEdit 322; deleteWeighIn 384; UNDO_WINDOW_MS 445 |
| [BodyFatScreen.kt](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/BodyFatScreen.kt) | BodyFatCalculatorCard; NumberField |
| [BodyFatViewModel.kt](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/state/BodyFatViewModel.kt) | saveToLogbook 199 |
| [MathDocsScreen.kt](../../feature/f06-weight/src/main/kotlin/app/wlo/feature/f06/weight/ui/MathDocsScreen.kt) | daily policy wording 49 |
| [GoalsEditorScreen.kt](../../feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/ui/GoalsEditorScreen.kt) | GoalsEditorScreen; EditorField; GoalForecastPreview; ForecastCard |
| [GoalsEditorViewModel.kt](../../feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/state/GoalsEditorViewModel.kt) | reload 320; starting-weight fallback; no-target state |
| [WeightFirstOnboardingScreen.kt](../../feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/ui/WeightFirstOnboardingScreen.kt) | forward action and FirstWeightSource choice |
| [WeightGoalControls.kt](../../feature/f01-onboarding/src/main/kotlin/app/wlo/feature/f01/onboarding/ui/WeightGoalControls.kt) | SafetyAnswerControl |
| [WloTheme.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloTheme.kt) | darkScheme; lightScheme; lightExtendedColors |
| [WloTypography.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloTypography.kt) | wloTypography; wloMaterialTypography |
| [WloCard.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloCard.kt) | static Surface branch |
| [WloCardHeader.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloCardHeader.kt) | uppercase label heading |
| [WloButtons.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloButtons.kt) | chip shape applied to both button variants |
| [WloChips.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloChips.kt) | SelectChip wraps FilterChip |
| [WloHeroStat.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloHeroStat.kt) | inline value/unit/delta; maxLines/ellipsis |
| [ProvenanceChip.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/ProvenanceChip.kt) | format used in contentDescription, not visible numeric text |
| [WloTrendChart.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloTrendChart.kt) | WeightChartCanvas; fixed chart height; canvas description |
| [WloForecastCard.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloForecastCard.kt) | optional onExplain; chart and arrival states |
| [WloSwipeRevealRow.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloSwipeRevealRow.kt) | documented release-gated, velocity-blind exception |
| [WloMotion.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloMotion.kt) | tokens; reduced motion declaration |
| [WloHaptics.kt](../../core/designsystem/src/main/kotlin/app/wlo/core/designsystem/WloHaptics.kt) | ViewWloHaptics |
| [WloApp.kt](../../app/src/main/kotlin/app/wlo/app/navigation/WloApp.kt) | nested TopAppBar; OnboardingGatedSurface; rail/shell |
| [WloTabs.kt](../../app/src/main/kotlin/app/wlo/app/navigation/WloTabs.kt) | current five-destination set |
| [MainActivity.kt](../../app/src/main/kotlin/app/wlo/app/MainActivity.kt) | forced dark WloTheme at 138 |
| [HubScreen.kt](../../feature/f10-daily-hub/src/main/kotlin/app/wlo/feature/f10/hub/ui/HubScreen.kt) | TrendCard; shared weight actions |
| [SettingsScreen.kt](../../app/src/main/kotlin/app/wlo/app/ui/settings/SettingsScreen.kt) | reminder choices; OS permission callback; preferences ordering |
| [ProfileFactsScreen.kt](../../app/src/main/kotlin/app/wlo/app/ui/settings/ProfileFactsScreen.kt) | placeholder fields; validation/save |
| [WeighInReminder.kt](../../app/src/main/kotlin/app/wlo/app/notification/WeighInReminder.kt) | TITLE/BODY; approximate periodic scheduling |
| [VaultDashboardScreen.kt](../../feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/ui/VaultDashboardScreen.kt) | Health Connect state/actions; Fresh Start text/confirmation |
| [VaultAdapters.kt](../../app/src/main/kotlin/app/wlo/app/di/VaultAdapters.kt) | freshStartPreview 419; freshStartHide 428 |
| [ImportScreen.kt](../../feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/ui/ImportScreen.kt) | MappingRowEditor; picker callback; Back/reset; always-present Close |
| [ExportImportViewModels.kt](../../feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/state/ExportImportViewModels.kt) | reset 323 discards file and state |
| [ExportScreen.kt](../../feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/ui/ExportScreen.kt) | format selection; SAF destination; result |
| [BackupControlsScreen.kt](../../feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/ui/BackupControlsScreen.kt) | folder/passphrase preconditions; backup state |
| [RestoreWizardScreen.kt](../../feature/f13-vault/src/main/kotlin/app/wlo/feature/f13/vault/ui/RestoreWizardScreen.kt) | staging/confirmation; applying/recovery copy |

### Official guidance checked for this review

- Short exclusive selections are supported by `SingleChoiceSegmentedButtonRow` and `SegmentedButton`; see [Android segmented buttons](https://developer.android.com/develop/ui/compose/components/segmented-button). This is the proposed replacement for generic selection-chip rows, not a requirement to replace valid chart filters indiscriminately.
- Cards contain coherent content and have standard filled/elevated/outlined variants; see [Android Card](https://developer.android.com/develop/ui/compose/components/card). This supports using native card anatomy and removing unnecessary containment.
- M3 Compose exposes semantic color/typography/shape systems; see [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3). WLO-specific palette/type choices remain product decisions.
- Native Compose defaults include minimum touch-target support; actual hit areas must be checked, including expansion; see [Accessibility API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
- Custom content needs meaningful state, descriptions and accessibility actions; see [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics). This is relevant to charts, transactional states and the custom swipe wrapper.
- Time-limited controls should use the user's recommended timeout; see [AccessibilityManager.getRecommendedTimeoutMillis](https://developer.android.com/reference/android/view/accessibility/AccessibilityManager#getRecommendedTimeoutMillis(int,%20int)). The proposed Undo deadline should use this without tying storage lifetime to animation.
- Supporting-pane and list-detail layouts give additional space a purpose; see [Android canonical layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/canonical-layouts).
- Modal bottom sheets are a supported native Compose pattern; see [Android bottom sheets](https://developer.android.com/develop/ui/compose/components/bottom-sheets). Retain native behavior and verify lifecycle/focus rather than re-creating sheet chrome.

The Material site [segmented-button page](https://m3.material.io/components/segmented-buttons/overview) required JavaScript in the research tool; implementation assertions above rely on the accessible official Android documentation and the supplied M3 skill. No unverified Expressive API or new dependency version is prescribed. Clinical threshold/formula validity is outside this UI review; existing numeric health claims must retain provenance and undergo their own evidence review, not be presented as newly validated here.
