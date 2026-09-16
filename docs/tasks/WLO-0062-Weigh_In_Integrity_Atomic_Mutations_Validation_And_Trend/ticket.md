---
id: WLO-0062
title: 'Weigh-in integrity: atomic mutations, validation, and trend-suffix repair'
status: done
theme:
release:
created: 2026-09-15T18:20:51Z
modified: 2026-09-15T18:58:54Z
closed: 2026-09-15T18:58:54Z
revision: 68bff3c1c4201c0f
blocks: []
related: [WLO-0052, WLO-0054, WLO-0057]
---

# Description

Implemented repository-owned, transactionally atomic weigh-in append/delete/replace/restore.

- All raw event, EAV attributes, persisted EWMA trend suffix, and day projections are mutated within one Room write transaction.
- LogbookViewModel now calls repository replace/restore doors instead of coordinating multiple repository writes.
- Public typed WeighInAttribute keys replace feature dependencies on Room implementation constants.
- Canonical kg validation rejects non-finite, non-positive, and values outside the inclusive 30–300 kg plausibility rail before storage.
- Backdated insert/delete and cross-day replacement rebuild every affected persisted trend/projection day, including clearing a now-empty source day.
- Failure-injection JVM tests prove append/delete/replace rollback; tests also cover invalid values and compare persisted suffixes against fresh EWMA calculations.

Verification:
- `./gradlew :core:data:ktlintCheck :core:data:jvmTest --console=plain -Pkotlin.incremental=false` — PASS (66 tests, 0 failures).
- Earlier focused suite for WeighInIntegrityTest, WeighInEditReplaceTest, WeighInDeleteTest, FoodDiaryWeighInTest, and CurrentTrendTest — PASS.
- Scoped `git diff --check` — PASS.
- `./gradlew :feature:f06-weight:compileDebugKotlin --console=plain -Pkotlin.incremental=false` — PASS.
- `./gradlew :app:compileDebugKotlin --console=plain -Pkotlin.incremental=false` — PASS.

Scope boundary: display-unit conversion remains owned by WLO-0052 and smoother preview/reload behavior remains owned by WLO-0054. This repository door accepts canonical kilograms.
