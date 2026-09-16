# WLO-0118 — More and settings review

16 September 2026. Review of supplied screenshots, navigation wiring and UI source. This is an information-architecture/design proposal, not a runtime verification of every import, restore, consent or security operation. No application changes in this ticket.

## Decision

More is secondary feature navigation. Settings controls app behavior, permissions, privacy and data administration. The problem is not that Settings contains navigation rows — nested settings are appropriate — but that those rows currently lead to product workflows mixed with preferences and technical infrastructure.

Move AI and Data Vault exclusively under Settings as their canonical administrative homes. Remove goal, diet plan and profile from Settings. Preserve contextual shortcuts: importing weight from weight history or downloading a model from food capture can still link to the same destination.

## Inventory and disposition

| Existing surface | Current behavior / issue | Proposed home and correction |
|---|---|---|
| More | Flat, equally weighted list; duplicates AI/Vault in Settings | Profile entry; working secondary features; separate Settings row |
| Archive | Navigation currently opens PlaceholderScreen; subtitle uses implementation jargon | Keep feature ownership in More when usable. Until then omit release navigation; preserve explicit unavailable response for old links. Explain body-outline history in ordinary language; respect existing naming ruling |
| Digestion | PlaceholderScreen | Same availability treatment; future feature under More, not Settings |
| Exercise | stub/exercise | Same availability treatment; future feature under More |
| Profile | Personal facts edited from Settings | More → Your profile; contextual links from calculation explanations. It is local profile data, not an account |
| Goal | Settings shortcut to the new inline sheet | Weight goal remains in Weight. Remove Settings shortcut; retain legacy route compatibility |
| Diet Plan Studio | Settings opens OnboardingScreen | Plan → Diet preferences (or Diet plan if that accurately names all fields). Existing-user entry should edit the current plan, not feel like restarting onboarding |
| Weight unit | Whole card, heading, paragraph, chips | One settings row, current value “Kilograms (kg)”; native single-choice dialog |
| App lock | Large card with repeated credential explanation and timeout chips | Settings → App lock. Summary Off / After 1 minute / Device lock required. Detail owns switch and timeout; actionable Android security link if unavailable |
| Weigh-in reminder | Card and slogan label; dependent time/permission handling inline | One row with Off / time / Blocked by Android summary; detail owns enable, time picker and permission repair |
| Data Vault | Already inside Settings as well as More; storage before backup | Settings → Data & backup; operational summary rows, not marketing introduction |
| Health Connect | Buried in Vault under file-oriented functions | Settings → Health Connect, one canonical permissions/import status surface; deep-link from related tracking feature if needed |
| AI Studio | Settings and More; long cards for every category | Settings → AI. Capability status list, on-device models, separate cloud policy, activity details |
| Model zoo | AI/category links and contextual capture link | AI → On-device models; retain contextual download links. Plain names, installed size, readiness and download/remove state |
| Receipt log | Technical egress evidence | AI → Activity history; plain summary of sent/blocked operations, details on demand; preserve evidence |
| Preview consent sheet | Demo exposed as ordinary navigation | Developer/debug UI only |
| Bring your own key | Inert v1.x UI and disabled key field | Do not show a nonfunctional setup form in normal release settings. Add provider setup when operational |
| Crash diagnostics | Lives inside AI, custom endpoint input, hardcoded “Off.” prose | Privacy/diagnostics detail separate from AI feature configuration. State-driven copy; advanced endpoint belongs in developer/advanced detail, not default overview |
| Backup controls | Folder/passphrase/automatic backup/history | Data & backup → Backup; one owner for schedule setting and prerequisite setup |
| Restore | Dedicated staged wizard | Data & backup → Restore backup; retain validation, review, progress, cancellation and recovery |
| Export | Format selection and file writer | Data & backup → Export data; explain purpose before JSON/CSV technical detail |
| Import | File selection, mapping, staged preview, apply/results | Data & backup → Import data; preserve preview and duplicate/error evidence; default infer columns only when trustworthy |
| Storage/Reclaim | Leads with 0 B photo storage and cryptographic prose | Data & backup → Storage. Show actual scope (attachments vs total app data), size and what cleanup affects. No prominent disabled action for empty storage |

## Proposed release hierarchy

Weight → inline weight goal / weight history / chart settings
Plan → Diet preferences, alongside existing planning destinations
More → Your profile; Settings

Archive, Digestion and Exercise join More when their screens work. Do not retain a directory of unavailable features merely to fill the page; a two-item More is acceptable during release development. No change to the four-tab shell is required by this review.

Settings (one concise overview):

- Everyday use: Weight unit; Weigh-in reminder.
- Privacy: App lock; AI; Diagnostics only if usable for this build (otherwise advanced/debug).
- Data: Data & backup; Health Connect.

Each overview row has a meaningful current value/state. Do not add an extra one-item “Units” screen. Only use a category screen when several controls or an explanation justify it. No speculative Appearance/Language/Sound pages until the app exposes actual choices. Respect system text size and theme behavior.

Data & backup: Backup [Not set up / last success / needs attention]; Restore backup; Export data; Import data; Storage [attachment size]. Restore and import are distinct workflows. Backup setup gets an actionable “Set up backup”; its overview does not lead with a disabled automatic-backup switch. Keep automatic backup ownership in Backup, rather than on both dashboard and controls screen.

AI: named capability rows with actual readiness and processing location; On-device models; Cloud AI access; Activity history. Readiness, permission and processing location are separate facts. “Cloud access off” must not imply that local AI is disabled. Global cloud block overrides per-capability grants without erasing them or granting new consent. Preserve independent per-capability consent and revocation behavior. Do not replace it with one blanket opt-in.

## Why the present layouts feel cluttered

1. Wrong hierarchy: product tasks under Settings, infrastructure beside feature navigation, duplicate routes, unfinished features advertised as working ones.
2. No difference in emphasis: every row/card looks equally important, even an empty storage bucket or inactive key field.
3. Excess containment: large outlined cards, repeated section titles and inset rows consume space without clarifying relationships.
4. Excess explanation: “One screen, then the two deep surfaces” describes implementation structure. “The facts the math reads”, “Model zoo”, “receipt log”, “reclaim” and “Room commits atomically” describe internal concepts rather than user decisions.
5. Status is missing or buried: users need Kilograms, Off, Not backed up, Last backup date, Permission needed. Persistent paragraphs explain capabilities instead.
6. Inconsistent labels: AI versus AI Studio; Data Vault versus Data vault; Backup versus Backup controls. Row labels should match destination titles.
7. False availability and ambiguous Off: placeholder pages and inert controls appear alongside working features; local readiness can be confused with cloud permission.
8. Introductory blanket claims (“Everything here lives on this device”, “Nothing can leave the device”) need a scope consistent with user-selected exports, model downloads and optional network capabilities. Replace blanket claims with precise status at the relevant control; this review does not audit the underlying security implementation.
9. Diagnostics text literally starts “Off.” independently of the enabled state. Rewrite it from state, not static reassurance.

## Android-settings visual direction

Use the supplied reference for grouped lists, aligned labels, restrained surfaces, short status lines and progressively disclosed detail. Do not copy an account/email header: WLO has no account. A local “Your profile” row belongs in More. Do not add a search field merely because Android's much larger settings hierarchy has one.

Soft filled grouping surfaces are compatible with the page-based Weight design: they group related rows. They should not become one outlined card per setting. Use standard M3 ListItem, Surface, Switch, radio-choice dialog, TimePicker and TopAppBar. Use the app's Inter family, established supporting-text roles and consistent gutters; preserve native touch targets. Use icons only where they improve scanning, not an arbitrary rainbow per row. Thin separators within long groups are optional; separation between groups should carry most of the structure.

A switch changes a boolean. A chevron opens details. A choice row displays its current selection and opens a radio dialog. Avoid a switch and chevron with competing click actions in one undifferentiated row. Transactions such as import/restore keep explicit review and confirmation; ordinary setting toggles persist immediately with failure feedback.

Official basis: https://developer.android.com/design/ui/mobile/guides/patterns/settings (accessed 16 September 2026): app behavior preferences, contextual placement, grouped lists, current-state summaries, appropriate choice controls, dependency handling and list-detail adaptation. The supplied OEM screenshot is inspiration, not an exact Material 3 specification.

## Delivery sequence and acceptance

1. Navigation: move canonical AI/Vault under Settings; move Profile to More and diet editing to Plan; remove goal shortcut from Settings; remove release stubs. Update IA and route tests together. Keep deep links functional and Back/Up returning to the invoking surface; do not delete data or features in a route cleanup.
2. Settings overview: compact groups/rows with real summaries. Remove intro paragraphs, routine per-setting cards and inaccessible/dead actions. Snapshot at the same viewport as Weight.
3. Data & backup/Health Connect: reorganize administrative destinations, eliminate duplicate backup schedule control, clarify storage scope and recovery states. Preserve grants, backup folders, keys, import/restore journals and existing safety confirmations.
4. AI: separate local capability readiness from cloud permission, consolidate model navigation, remove demos/inert setup forms from release UI, move diagnostics and humanize activity history.
5. Deep flows: edit current diet plan without misleading onboarding framing; streamline profile facts; simplify backup, restore, import and export copy while retaining necessary review/confirmation/recovery.

Verification must include stale/revoked folder access; backup failure; restore/import cancel and validation failure; empty storage; Health Connect unavailable/denied/partial access; missing or downloading model; global block plus category consent; app lock without device credentials; notification permission denied; saved-value persistence and failed writes. Test large text, narrow and wide windows, TalkBack row roles, focus restoration, keyboard and non-color status. Use the agent comparison tool for rendered fidelity, not manually prepared owner screenshots.

Current audit: task hierarchy 0, information hierarchy 0, component semantics 1, token consistency 1, states/recovery 1, restraint 0. Accessibility/adaptation require device verification. No production approval inferred from source/screenshot review.

Scope note: this review inventories every current More destination and major child route. Detailed runtime correctness testing of each child flow is outstanding; findings above distinguish observed source behavior from proposed design. No new product settings or capabilities are assumed.
