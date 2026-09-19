# Flexible meal-planning mockup — WLO-0129

Interactive entry: [Flow 11](../../11-flexible-meal-planning.html).

## Scope and handoff

HTML/CSS/JavaScript interaction study; no Compose changes. Local Inter and existing
WLO colors, with the updated accessible outline/text tokens. Native HTML form
semantics stand in for Material 3 controls. Import, generation and nutrition use
prepared sample recipes, disclosed in the study shell. There are no external calls
or persistent app writes. Application navigation and shopping are contextual only.

Implemented: first-use date scope; dinner-only defaults; optional usual breakfast;
flexible meals/dates; suggestion review; isolated swaps; source-input preview and
failure recovery; AI draft fixture; recipe text editing (ingredient edits hold
nutrition); separate batch/personal portion scaling; saved daily agenda; optional
logging/replacement; move/repeat and basic leftover references; travel range and Undo.
Future work includes actual import/AI, food search/photo capture, complete batch
allocation, persistent library management and full recurrence scheduling. Simple
moves/replacements involving linked leftovers ask the user to update the dependencies
first. A flexible range previews conversion of dependent future leftovers into
independent cooking meals. This is not the final dependency-resolution design.

## Browser verification — 18 September 2026

- First-use → dinner suggestions: five weekday dinners, other meals flexible.
- Usual breakfast + dinners: ten meals; lunches remain flexible.
- Import fixture: swap one dinner, review ingredients and add without affecting others.
- Scaling: four batch servings and 1.5 personal servings yield 915 kcal for the
  610 kcal sample recipe; numeric ingredients double independently.
- Failed URL fixture preserves input and exposes pasted-text recovery.
- Ingredient correction can be saved with unknown nutrition; no false zero total.
- AI draft adds a meal to an otherwise flexible trip; logging remains optional.
- Logging transfers 620 kcal from planned to logged once; Undo restores the plan.
- Leftover repeat into one flexible lunch preserves the dinner and other dates.
- Travel 28 September–4 October shows a compact flexible week, without a prominent
  generate-week completion task. A particular meal can still be added.
- Narrow 390 px viewport: no document/content horizontal overflow; the seven-day
  navigator scrolls locally to preserve touch sizes. Checked visible screen/action
  buttons have at least 48 px targets. Native focus and selection are exposed in
  the browser accessibility tree. Reduced-motion media rule disables the spinner.
- JavaScript syntax validation passed; observed browser error log was empty.

Screenshots: [saved week](saved-week.png), [travel week](travel-week.png).
These are prototype captures, not app/mockup pixel comparisons. No clinical
validation, actual importer accuracy test, TalkBack session or end-user usability
study was performed.

## Self-audit (0 missing, 1 partial, 2 ready for this interaction review)

| Category | Score | Evidence / limit |
|---|---|---|
| Task clarity | 2 | Single primary action per stage; flexible week is a finished choice. |
| Hierarchy | 2 | Planned meals precede a collapsed flexible-meal group. |
| Component semantics | 2 | Native buttons, select/radio/date/text inputs, details and status regions. |
| Tokens | 2 | Shared WLO semantics, locally bundled Inter; corrected accessible text/outline values. |
| Adaptive behavior | 2 | Study sidebars collapse at narrow widths; phone content and dates remain usable. |
| States/feedback | 1 | Core drafts, failure, empty, logging and Undo covered; production recurrence/batch handling deferred. |
| Accessibility | 1 | Keyboard semantics/focus, targets and narrow overflow checked; full assistive-tech and 200% text audit remain. |
| Expressive restraint | 2 | Quiet surfaces and rows; only the loading indicator animates. |
