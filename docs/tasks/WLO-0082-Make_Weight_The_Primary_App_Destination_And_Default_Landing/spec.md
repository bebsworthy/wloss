# Objective
Align the app shell and deep-link behavior with the WLO-0068 Weight-first IA using standard Material 3 navigation components.

# Scope
- Compact top-level order: Weight, Hub, Plan, Insights, More.
- Launch, onboarding completion, app-icon entry, and weight notification/widget intents land on Weight unless an explicit supported deep link says otherwise.
- Move Archive, Digestion, Exercise, Data Vault, AI, and Settings behind More without renaming or weakening discretion gates.
- Use NavigationBar on compact widths and NavigationRail at ≥600dp; nested surfaces use TopAppBar with Up and hide top-level chrome.
- Preserve destination state and deep links; no second navigation axis/drawer.

# Acceptance
Weight is visibly primary and the default landing route; all five destinations and nested Up behavior pass compact and API 29 emulator coverage; existing deep links remain deterministic; standard M3 navigation components and accessibility semantics are verified; architecture/lint/formatting gates pass.


# Implementation evidence (2026-09-16)

- Weight is the NavHost start destination and first compact/adaptive destination; the top-level order is Weight, Hub, Plan, Insights, More.
- The shell continues to use standard Material 3 NavigationBar/NavigationRail and TopAppBar. Nested routes hide top-level chrome and expose an accessible Up action.
- More uses standard Material 3 ListItem rows for Archive, Digestion, Exercise, Data Vault, AI, and Settings; existing deep links remain registered.
- Top-level destination state is preserved with saveState/restoreState, and route dispatch was split into a small top-level surface instead of growing the cross-feature router beyond lint limits.
- Verification passed: app unit tests, androidTest compilation, app ktlint, app detekt, architecture checks, and repository diff formatting.
- API 29 focused device acceptance passed all 5 WloShellTest cases and all 4 WloDeepLinkTest cases, covering default Weight launch, fresh deep-link gating, explicit Hub/digestion links, all five destinations, saved Plan state, nested chrome, and Up navigation.
