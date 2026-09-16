---
id: WLO-0065
title: 'Architecture hardening: honest KMP posture, resolved dependency checks, fatal lint'
status: done
theme:
release:
created: 2026-09-15T18:20:51Z
modified: 2026-09-15T20:25:22Z
closed: 2026-09-15T20:25:22Z
revision: 73467644be44eeb1
blocks: []
related: [WLO-0029, WLO-0057]
---

# Description

Implemented architecture hardening from WLO-0057. Documentation now states the honest JVM-family KMP posture; Android KMP modules execute host tests. The architecture gate scans resolved production artifacts and rejects transitive Ktor/OkHttp/Retrofit outside :core:network, backed by 23 passing build-logic self-tests. Android lint is fatal and notification-permission handling is explicit. Detekt complexity and broad-exception rules are enabled with pragmatic thresholds and local reasoned suppressions; cancellation is preserved at network/vault boundaries. Verification: detekt, ktlintCheck, checkArchitecture, :app:lintDebug, all JVM tests, and :app:assembleDebug pass together. API 29 focused app-lock, restore (2/2), M3 screen (2/2), and weigh-in (6/6) tests pass. The aggregate 65-test run reached 64/65; its sole remaining failure is the pre-existing wall-clock-sensitive F10HubMealsTest expecting a daytime-only action during the night phase.
