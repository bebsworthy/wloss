---
id: WLO-0171
title: Implement atomic recipe save and meal acquisition commits
status: todo
theme:
release:
created: 2026-09-21T10:10:09Z
modified: 2026-09-21T10:10:11Z
closed:
revision: 13df12f18f379a5b
blocks: [WLO-0173, WLO-0174, WLO-0175, WLO-0177]
related: [WLO-0167]
---

# Description

Save recipe drafts and selected meal items in one idempotent transaction using the existing agenda and F02 ownership, with explicit portions and conflict recovery.
