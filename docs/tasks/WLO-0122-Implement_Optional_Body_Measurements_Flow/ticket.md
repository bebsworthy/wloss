---
id: WLO-0122
title: Implement optional body measurements flow
status: done
theme:
release:
created: 2026-09-16T23:28:08Z
modified: 2026-09-16T23:47:05Z
closed: 2026-09-16T23:47:05Z
revision: 8aaa37f45ce1b78b
blocks: []
related: []
---

# Description

# WLO-0122 — optional body measurements

Implements the WLO-0121 flow with standard Material 3 controls and the existing chart component.

- Weight exposes Body measurements outside chart settings, including for an empty weight history.
- The overview lists only recorded metrics, with individual dates, values and source labels. Chest, waist, hips, left/right thighs and left/right arms are independent optional metrics.
- A metric detail shows a single source/method series, actual period-filtered points and history. Change uses the first and last readings in that period; body-fat changes use percentage points. No goal or good/bad classification is inferred.
- Record measurements uses a calendar date, optional decimal inputs, a labelled checkbox picker and a full-page form. Blank fields are omitted. Previous readings are supporting text, never prefilled measurements. Usual fields are remembered per profile after a save attempt. Unsaved entries prompt before leaving; failed writes retain the form.
- Body fat can be entered as a scale reading or another manual reading. Health Connect body-fat events and previous tape estimates are read from the existing store. Tape estimation remains a secondary destination. There is no new scale connection implementation.
- Manual weigh-in has an optional body-fat field for entering both readings from a scale. Both commit atomically and retries do not duplicate either.
- Session persistence uses existing CUSTOM/metric EAV events in cm and BODY_FAT events in percent; no database schema migration. Atomic session writes use a profile-scoped operation marker. Blank/unselected fields generate no events. Existing backup/export paths retain the events and sidecars.

Validation: core:data JVM tests (81), F06 unit tests (46), app unit tests (24); debug assembly, ktlint, detekt, architecture checks and Android lint. New tests cover session rollback, retry idempotency, invalid values, every circumference independently, and paired weight/body-fat writes. Emulator flow checks cover entry, optional fields, rejected zero, keep/discard edits, checkbox picker and back navigation. No measurement events were added to the user's emulator profile during smoke testing. This is not an end-to-end test against a physical scale or a new Health Connect provider.

Remaining scope: source labels reflect available metadata; historical records without a device identity cannot identify a specific scale. Direct circumference entry currently uses cm. Tape calculation retains the existing method-specific screen. Overview entry uses a concise descriptive subtitle; latest values are shown on the measurements page.

Final device pass also verified the Material date picker opens/cancels, 2× system font size (restored afterward), and absence of runtime crashes. The final layout avoids applying the status-bar inset twice.
