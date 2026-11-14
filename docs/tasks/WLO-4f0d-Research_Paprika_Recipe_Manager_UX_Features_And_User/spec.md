# Paprika UX and feature research

Research Paprika's recipe acquisition, personal editing, retrieval, cooking, planning, grocery/pantry and portability workflows. Distinguish official capabilities, qualitative user feedback and WLO design proposals. Check present product direction separately from historical reviews.

## Deliverables and completion evidence

- `docs/research/paprika-ux-deep-dive.md`: extensive source-linked study, feature/interaction inventory, longitudinal and recent feedback, limitations, Paprika 3 versus Paprika 4 beta boundary, WLO implications and concrete validation scenarios.
- `docs/research/paprika.md`: corrected overview linking the detailed study.
- Narrow corrections in the research syntheses for pantry behavior, pricing scope and attribution of competing-vendor UX criticism.
- Official platform manuals/support/changelogs checked against editorial and community observations; version-specific contradictions identified explicitly.
- Documentation links and whitespace checked. Desk research only; no hands-on Paprika testing or import benchmark claimed.

## Findings

The strongest pattern is preserving user effort through capture, adaptation, retrieval and repeat cooking. Keep recipe collection usable when nutrition is incomplete, without presenting incomplete calculations as complete. Practical placekeeping, scaling, multiple dishes and readable exports deserve attention alongside acquisition.

Paprika 4's official July 2026 documentation introduces optional subscriptions for new features and household sub-accounts; beta-only or community-reported functionality is labelled separately. Older claims about preset-only scaling, unavoidable cooking-state loss, automatic stock depletion and absent export/trials were corrected.

This research does not amend frozen WLO release scope, implement features, introduce a backend or change the free-forever model.


## Follow-up: specification revision assessment

Compared the Paprika findings with F03/F04/F13, recipe-creation-ux.md, WLO-w0vs and WLO-e6yf. Recommendations for discussion, not accepted scope changes:

1. Relax the recipe UX proposal's mandatory intelligible-yield requirement for keeping a recipe: permit an explicitly incomplete library reference, while holding per-serving nutrition and portion-dependent actions until yield is resolved. Never treat truncated AI generation as a complete recipe.
2. Make the shared preview's hierarchy contextual: collection emphasizes recipe content; meal acquisition emphasizes the selected portion and nutrition. Keep one editor and explicit save behavior.
3. Specify multiline ingredient entry/paste with original wording and section preservation, progressive food matching and corrections that retain user edits.
4. Define minimum library retrieval and repeat-use behavior (ingredient/name/source search, recent/favorites, notes and variations); avoid requiring taxonomy setup.
5. Consider basic cooking support as a deliberate follow-on to the future recipe feature, replacing vague Blue Sky treatment with a bounded session contract. F03 currently explicitly defers cook mode; do not insert partial implementation without a scope decision.
6. Clarify linked-component recipes: stable identity, whether references are navigation-only or counted ingredients, and prevention of nutrition double-counting. Rich text links alone do not establish nested recipe calculations.

Already covered: incomplete nutrition, recipe versions, historical snapshots, yield/batch/personal-portion distinction, provenance, manual fallback, export, and shopping-state reconciliation. WLO-w0vs/WLO-e6yf foundations remain sound.

Factual cleanups remain in legacy feature-doc competitor rationale: Paprika scaling limits, unconditional cook-state loss, schema-only import attribution and URL-only capture. Correct the evidence without changing WLO's frozen release boundary. No functional spec was amended in this assessment.
