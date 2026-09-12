/*
 * WLO design system — DESIGN-SYSTEM.md §1–§7 as Compose.
 *
 * NOTICE: bundled typeface is Inter (c) The Inter Project Authors, licensed
 * under the SIL Open Font License 1.1 — see OFL.txt in this module and
 * src/main/res/font/inter_*.ttf (R-D3).
 */
plugins {
    id("wlo.android.library")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    // Forecast-card date ticks render from epoch days (kotlinx-datetime).
    implementation(libs.kotlinx.datetime)

    // D6: the provenance-chip components are the only sanctioned rendering
    // path for DerivedValue, so the domain type is part of this module's API.
    api(project(":core:model"))
}
