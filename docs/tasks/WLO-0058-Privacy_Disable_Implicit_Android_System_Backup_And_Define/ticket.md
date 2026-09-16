---
id: WLO-0058
title: 'Privacy: disable implicit Android system backup and define transfer policy'
status: done
theme:
release:
created: 2026-09-15T18:20:51Z
modified: 2026-09-15T18:41:06Z
closed: 2026-09-15T18:41:06Z
revision: e1fc0044542d0dfb
blocks: []
related: [WLO-0057]
---

# Description

Implemented a fail-closed Android platform-backup policy.

- `android:allowBackup="false"` is explicit in the application manifest.
- Android 11-and-lower full-backup rules exclude all credential- and device-protected storage domains.
- Android 12+ data-extraction rules independently exclude those domains from both cloud backup and device-to-device transfer.
- The `checkMergedManifest` architecture gate validates debug and release merged manifests plus both rule files.
- Policy documentation now identifies the user-selected SAF flow as WLO's only data-transfer path.

Verification:

- `./gradlew -p build-logic check --console=plain` — passed.
- `./gradlew :app:checkMergedManifest --console=plain` — passed; both variants and all nine domains validated.
- `./gradlew :app:assembleDebug :app:assembleRelease --console=plain` — passed.
- Packaged APK inspection with `aapt2 dump xmltree/resources` confirms `allowBackup=false`, both rule references, and both XML resources.
- API 29 emulator package flags omit `ALLOW_BACKUP`; `bmgr backupnow app.wlo` refuses the backup. The emulator's Backup Manager itself reported disabled, so the package-manager flag and packaged/merged policy checks are the authoritative runtime/build evidence.
