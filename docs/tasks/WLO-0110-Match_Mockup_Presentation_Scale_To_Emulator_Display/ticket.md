---
id: WLO-0110
title: Match mockup presentation scale to emulator display
status: done
theme:
release:
created: 2026-09-16T17:59:43Z
modified: 2026-09-16T18:02:06Z
closed: 2026-09-16T18:02:06Z
revision: 8ef34df005f9efe9
blocks: []
related: []
---

# Description

Updated the original WLO-0103 weight-overview.html to display its phone viewport at the owner-provided 470 × 1045 screen points. The internal logical width is 411.43dp (1080px / 2.625 emulator density); uniform CSS zoom scales typography, spacing and graphics together. The viewport height is fixed and main content scrolls internally while header/actions/navigation remain in the phone viewport. Removed desktop decorative border/radius so the measured width is screen content, excluding a frame.

Added an external display-width control for later emulator resizing, with a persisted setting and 470 default. The proportional height follows the provided 470:1045 ratio. No Android code or typography tokens changed.

Verified actual browser rectangle: width 470, height 1045. Checked local font loading, changing width to 480 and reload persistence, and period selection; no JavaScript errors. Capture: docs/tech/WLO-0110-evidence/470x1045.png. Browser zoom affects on-screen sizing: use 100% browser zoom for these desktop-point dimensions. The existing local-file tab must be reloaded; the browser automation provider denied access to file URLs, so it was not refreshed automatically.
