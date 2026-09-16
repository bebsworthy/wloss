# Problem

The main manifest leaves Android Auto Backup enabled by default. App-private Room, DataStore, and document files can therefore participate in system cloud backup outside WLO's consent, receipt, and encrypted-export boundaries.

# Scope

- Decide and document cloud-backup versus device-transfer policy for this local-first health app.
- Set explicit manifest attributes and API-appropriate backup/data-extraction rules; default recommendation is no cloud backup and no implicit transfer until intentionally specified.
- Add a merged-manifest architecture check covering release and debug variants.
- Verify included/excluded domains using Android backup tooling where supported.
- Amend user/privacy documentation so platform backup behavior is accurate.

# Acceptance

- No WLO database, settings, document, vault, or attachment data can enter Android cloud backup implicitly.
- The merged manifest and extraction rules are tested.
- Any allowed device-to-device transfer is explicit, documented, and consistent with the threat model.

# Evidence

WLO-0057 report, P0 Android system backup finding.
