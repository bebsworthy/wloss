# WLO-0119 — More and Settings implementation

17 September 2026. Implements the navigation and settings restructuring requested after WLO-0118.

## Delivered

- More contains Your profile and Settings; unavailable Archive/Digestion/Exercise entries are omitted. Existing legacy routes remain registered.
- Diet plan is reached from Plan; the weight goal remains in Weight. Settings no longer advertises these product workflows.
- Settings has Everyday use, Privacy and Data groups built with native M3 surfaces/list rows. Weight units use a radio-choice dialog. Reminder, app lock and diagnostics have focused detail routes.
- AI and Data & backup have Settings as their administrative home. AI separates local model management, cloud blocking and individual permissions. Demo and inert provider forms are removed from ordinary navigation. Activity history remains reachable.
- Data & backup leads with Backup and Restore, followed by Export/Import and Storage. Backup retains sole ownership of automatic scheduling. Health Connect has its own Settings destination.
- Storage explicitly identifies local attachments. Deletion requires confirmation and explains that it is permanent. The split Health Connect and Storage routes retain the vault's secure-screenshot classification, covered by route tests.
- Profile fields use the page layout without an outlined enclosing card. IA and master feature navigation rulings are updated.

## Verification

Debug assembly, app/F12/F13 unit tests, vault JVM tests, ktlint, detekt, architecture checks, Android lint and Android test compilation. Device smoke checks on emulator-5554 (API 29, 1080×2400): More → Settings; unit dialog selects pounds then restores kilograms; app lock without device credentials; reminder detail; Health Connect detail; AI; Data & backup → Storage; Back navigation. Settings inspected at normal and 2× font scale, restored to normal. Screenshots in WLO-0119-evidence; protected data screens intentionally have no captures.

## Boundaries and remaining deeper work

The diet setup workflow itself remains the existing implementation; this change relocates it, it does not provide a new existing-plan editor. Import/export/restore/backup transaction screens and their transaction engines are retained. This pass does not certify the full WLO-0118 failure-state matrix (revoked folders, interrupted restore, notification denial on API 33+, model downloading, TalkBack). AI/data overview subtitles describe destination scope; current operational states are shown in the detail surfaces. No data migration or consent grant is performed by this change. No backup, restore or deletion was run against the emulator's user data.

## Profile navigation regression fix

The owner found that More → Your profile crashed: `app/profile` had a screen dispatch branch but no NavHost destination. Registered it alongside the other app detail routes. Debug assembly, app unit tests, ktlint and detekt pass. Installed the corrected APK and verified More → Your profile shows the actual editable facts form, toolbar Up returns to More, reopening works, and system Back returns to More. No profile fields changed. Screenshot: `WLO-0119-evidence/profile.png`.
