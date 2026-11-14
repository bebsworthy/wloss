---
id: WLO-0171

title: Implement atomic recipe save and meal acquisition commits

status: todo
closed:
status_changed: 2026-09-21T10:10:11Z

theme:

release:

created: 2026-09-21T10:10:09Z

blocks: [WLO-0173, WLO-0174, WLO-0175, WLO-0177]

related: [WLO-0167]

parent:

rank: 11272192
---

# Description

Save recipe drafts and selected meal items in one idempotent transaction using the existing agenda and F02 ownership, with explicit portions and conflict recovery.
