package app.wlo.buildlogic

import org.gradle.api.Project

/** Android + JVM targets shared by every convention plugin (ADR-002, ADR-005 §3). */
public object AndroidConfig {
    /** AGP 9.4 maximum supported API level (verified 2026-09-12); Compose 1.12.x requires >= 37. */
    public const val COMPILE_SDK: Int = 37
    public const val TARGET_SDK: Int = 37

    /** ADR-002: minSdk = API 29. */
    public const val MIN_SDK: Int = 29

    /** ADR-005 §3: application id `app.wlo`, packages `app.wlo.*`. */
    public const val APPLICATION_ID: String = "app.wlo"

    public const val JVM_TARGET: Int = 17

    /** `:core:model` -> `app.wlo.core.model`; `:feature:f02-food` -> `app.wlo.feature.f02.food`. */
    public fun namespaceFor(projectPath: String): String =
        "app.wlo" + projectPath
            .split(':')
            .filter { it.isNotBlank() }
            .joinToString(prefix = ".", separator = ".") { part -> part.replace('-', '.') }
}

/** Root package namespace for source code. */
public const val WLO_PACKAGE_ROOT: String = "app.wlo"

/** Convenience for precompiled plugins. */
public fun Project.isAppProject(): Boolean = path == ":app"
