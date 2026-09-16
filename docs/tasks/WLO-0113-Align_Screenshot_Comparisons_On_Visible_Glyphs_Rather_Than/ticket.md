---
id: WLO-0113
title: Align screenshot comparisons on visible glyphs rather than text boxes
status: done
theme:
release:
created: 2026-09-16T18:30:02Z
modified: 2026-09-16T18:31:19Z
closed: 2026-09-16T18:31:19Z
revision: 6d023de837664125
blocks: []
related: []
---

# Description

Corrected the agent tool's anchor method: default glyph mode detects the first contiguous foreground-ink column group inside the selected browser/Android text regions. It aligns visible glyph top-lefts rather than text-box corners. Scale still comes from screen width; differing glyph sizes remain visible. Explicit box mode remains available. No OCR is claimed; high-contrast matching first characters are required, and ambiguous/small/empty ink fails.

Reran the live comparison using mockup #hero and Android text 77.1, anchoring the first 7. Reference ink origin (30,162); candidate (70,317); scale 470/1080; translation (−0.463,24.046). Four tests pass, including a test showing that leading and a taller second glyph do not affect selection. Visually inspected the resulting overlay. Updated agent instructions and command example. Output: docs/tech/WLO-0113-evidence/overlay.png. App and mockup remain unchanged.
