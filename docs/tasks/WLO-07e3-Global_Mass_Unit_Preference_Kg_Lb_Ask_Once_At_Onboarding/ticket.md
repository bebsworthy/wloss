---
id: WLO-07e3
was: WLO-0052

title: 'Global mass unit preference (kg|lb): ask once at onboarding, Settings-only switch, honor everywhere'

status: done
closed: 2026-09-15T22:25:06Z
status_changed: 2026-09-15T22:25:06Z

theme: weight-core

release: 1

created: 2026-09-14T16:51:44Z

blocks: [WLO-1ynj, WLO-6c82]

related: [WLO-1ynj, WLO-2b52, WLO-2zdk, WLO-35f0, WLO-5d13, WLO-crnh, WLO-d4a6]

parent:

rank:
---

# Description

Owner ruling (2026-09-14): mass unit is a GLOBAL user preference — kg or lb, asked exactly once (onboarding), changeable only in Settings. No data surface ever offers a unit switch. Today the plumbing exists (MassUnit, profiles.unitPreference, SettingsStore mirror) but nothing can set it, onboarding never asks, and F01/F06 hardcode kg; only the Hub honors the preference.
