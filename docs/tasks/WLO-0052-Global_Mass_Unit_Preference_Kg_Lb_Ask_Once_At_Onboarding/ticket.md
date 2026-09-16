---
id: WLO-0052
title: 'Global mass unit preference (kg|lb): ask once at onboarding, Settings-only switch, honor everywhere'
status: done
theme: weight-core
release: 1
created: 2026-09-14T16:51:44Z
modified: 2026-09-15T22:25:06Z
closed: 2026-09-15T22:25:06Z
revision: 5f8591b744e4f885
blocks: [WLO-0051, WLO-0074]
related: [WLO-0038, WLO-0051, WLO-0053, WLO-0055, WLO-0062, WLO-0079, WLO-0081]
---

# Description

Owner ruling (2026-09-14): mass unit is a GLOBAL user preference — kg or lb, asked exactly once (onboarding), changeable only in Settings. No data surface ever offers a unit switch. Today the plumbing exists (MassUnit, profiles.unitPreference, SettingsStore mirror) but nothing can set it, onboarding never asks, and F01/F06 hardcode kg; only the Hub honors the preference.
