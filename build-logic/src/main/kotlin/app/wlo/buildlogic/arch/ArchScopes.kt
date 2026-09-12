package app.wlo.buildlogic.arch

/**
 * Scope metadata for the arch checks (kept as plain data so both the real
 * build and the TestKit fixtures exercise identical rules).
 */
public object ArchScopes {
    /** Projects allowed to see restricted impls (D1) and declare INTERNET (D4). */
    public const val COMPOSITION_ROOT: String = ":app"

    /** D6 heuristic applies to projects that ship UI. */
    public fun isUiScoped(projectPath: String): Boolean =
        projectPath == ":app" || projectPath.startsWith(":feature:")

    /** D7 clock-ban applies to this project's sources. */
    public const val ENGINES: String = ":core:engines"
}
