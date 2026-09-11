---
id: WLO-0004
title: 'Owner feedback amendments: event-level weigh-ins, manual-first, gut rhythm, photo retention'
status: done
theme:
release:
created: 2026-09-11T00:09:32Z
modified: 2026-09-11T00:09:34Z
closed: 2026-09-11T00:09:33Z
revision: 0120364b5ee8ffa7
blocks: []
related: []
---

# Description

Owner review of FEATURES.md + specs (4 comments), applied 2026-09-11:
1. Multiple weigh-ins/day are normal data -> R-B8: event-level storage, day scalar = derived view (lowest-of-day). F06 patched; post-bathroom re-weigh psychology recorded and tied to F09.
2. Manual entry always first-class -> R-U15: assists never gate. F06 weigh-in flows patched.
3. No daily gut expectation -> R-U13: gut/capture never in day-completeness; gap reminders baseline-relative + default off. F09/F10 patched.
4. Photo retention opt-in default off -> R-U14: F02/F09 discard-at-save default; F13 storage dashboard [v1] + SAF offload [v1.x]; F08 silhouette is the explicit exception (photos are the feature). F02/F08/F13 patched.
