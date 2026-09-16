---
id: WLO-0056
title: 'M3 list conformance sweep: all list surfaces onto ListItem/WloListRow per the AGENTS.md M3-first rule'
status: done
theme:
release:
created: 2026-09-14T22:39:41Z
modified: 2026-09-15T21:28:00Z
closed: 2026-09-15T21:28:00Z
revision: dbac24dbddfa7bef
blocks: []
related: [WLO-0055, WLO-0057]
---

# Description

Audit (2026-09-15), per the owner's M3-first ruling: every repeated-row surface checked against material3 ListItem / the design system's WloListRow wrapper.

**Already compliant (WloListRow, WLO-0031):** Settings, Zoo, Vault dashboard, AI studio, Body-fat method registry, Plan + plan sheets, Shopping list, Onboarding goals editor, F02 diary entries / food log / capture, F06 logbook rows + history buckets ( ListItem directly).

**Converted in this ticket:** F06 History card buckets (hand-rolled Row → two-line ListItem: headline range, supporting weigh-in count, trailing Δ + closing weight); F12 receipt ledger (custom Column → ListItem with leading badge, headline host, supporting ledger meta line); F13 backup recent-files (bare Text lines → WloListRow); F13 restore section previews (SpaceBetween rows → WloListRow with value slot); F10 explainer sheet label/value rows (custom Row → WloListRow; WloStatRow not applicable — it requires a DerivedValue, explainer values are plain strings); debug egress monitor counters + receipts (→ WloListRow / ListItem).

**Checked, not lists — no change:** pantry suggestion/unit pickers (chips), F13 import field-mapping (form rows), staged-warnings (notice paragraphs), RecipeEdit tags (chips), MathDocs (prose cards), onboarding editor layout.

**Documented custom (justified impossibility):** WloSwipeRevealRow — stock M3 swipe-to-dismiss cannot be made release-gated/velocity-blind (WLO-0050).
