---
id: WLO-0052
title: 'Global mass unit preference (kg|lb): ask once at onboarding, Settings-only switch, honor everywhere'
status: todo
theme:
release:
created: 2026-09-14T16:51:44Z
modified: 2026-09-14T16:57:37Z
closed:
revision: 7cb54f2d4c7ac21a
blocks: []
related: [WLO-0051]
---

# Description

Owner ruling (2026-09-14): mass unit is a GLOBAL user preference — kg or lb, asked exactly once (onboarding), changeable only in Settings. No data surface ever offers a unit switch. Today the plumbing exists (MassUnit, profiles.unitPreference, SettingsStore mirror) but nothing can set it, onboarding never asks, and F01/F06 hardcode kg; only the Hub honors the preference.
