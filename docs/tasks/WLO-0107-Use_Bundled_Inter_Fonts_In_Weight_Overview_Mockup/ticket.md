---
id: WLO-0107

title: Use bundled Inter fonts in weight overview mockup

status: done
closed: 2026-09-16T17:05:42Z
status_changed: 2026-09-16T17:05:42Z

theme:

release:

created: 2026-09-16T17:05:02Z

blocks: []

related: []

parent:

rank:
---

# Description

Updated WLO-0103 weight-overview.html to load the exact bundled Inter static font files through local @font-face rules, replacing the host OS font stack. Hero remains Medium 500 and supporting copy Regular 400. Normalized intermediate 550/650 weights to available Semibold 600. Documented the local font dependency and saved inter-overview.png.

Verified in Chromium at 412px: Regular/Medium/Semibold successfully loaded, hero computed weight 500, no horizontal overflow, no failed requests or JS errors. Bold is declared but not needed by the initial view. No app code changed.
