package app.wlo.feature.f13.vault

/**
 * F13 route contracts (ARCHITECTURE §2.2 `routes.kt` slot). The Data Vault is
 * Settings → Data Vault (IA.md §1); its route names match
 * `:core:vault`'s [SecureSurfaces] forward registrations (FLAG_SECURE on all
 * vault surfaces — IA §6 discreet mode), which is why the wizard routes carry
 * the `/vault/…` prefix shape PART A pinned.
 */
public object F13Routes {
    /** The vault dashboard: storage partitions + backup posture + wizards' entry points. */
    public const val VAULT: String = "f13/vault"

    /** Backup controls: SAF folder, passphrase setup, backup-now, auto toggle. */
    public const val BACKUP: String = "f13/vault/backup-setup"

    /** The staged-restore wizard (pick → report → confirm → apply). */
    public const val RESTORE: String = "f13/vault/restore"

    /** The export wizard (JSON bundle vs per-metric CSV). */
    public const val EXPORT: String = "f13/vault/export"

    /** The import surface (JSON bundle re-import + generic CSV with mapping). */
    public const val IMPORT: String = "f13/vault/import"
}
