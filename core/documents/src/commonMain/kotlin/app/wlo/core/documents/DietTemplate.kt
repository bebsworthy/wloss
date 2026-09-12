package app.wlo.core.documents

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/**
 * A shipped diet template (F01 §3 "Shipped template library, all plain JSON in
 * the same schema"). No template is privileged: all ship as inspectable data
 * (this file's twin JSON resource), all editable, all excludable from the
 * gallery. A template is a PARTIAL plan: the applier fills the personal fields
 * (goal, budget, floor) deterministically — see [DietTemplateApplier].
 */
@Serializable
public data class DietTemplate(
    public val id: String,
    public val name: String,
    public val summary: String,
    public val isDefault: Boolean = false,
    /** Preset name carried into [MacroSplit.Preset] when there is no explicit split. */
    public val macroPreset: String? = null,
    /** Explicit percent split (wins over [macroPreset] when present). */
    public val macroSplit: ExplicitSplit? = null,
    /** Keto-style carb-limit ring (grams). */
    public val carbCapG: Double? = null,
    /** High-protein floor ring (grams). */
    public val proteinFloorG: Double? = null,
    public val cadence: Cadence = Cadence.DAILY,
    /** Surfaces this template pins (rings/timers/cards), consumed by F02/F10. */
    public val surfaces: List<String> = emptyList(),
    public val exclusions: List<String> = emptyList(),
    /** Soft authored hints (F03 renders, never enforces). */
    public val hints: List<String> = emptyList(),
    /** IF timing rule for the IF variants (F10 timer surfaces). */
    public val eatingWindow: EatingWindow? = null,
    /** The template's own fiber target default (R-B3: F09 supplies 25–30 g). */
    public val fiberTargetG: Double = 25.0,
    /** The template's water target default (ml). */
    public val waterTargetMl: Double = 2_000.0,
) {
    /** Explicit percent split — a template-scoped mirror of [MacroSplit.Custom]. */
    @Serializable
    public data class ExplicitSplit(
        public val proteinPct: Double,
        public val carbPct: Double,
        public val fatPct: Double,
    )
}

/**
 * Root of the shipped-library resource: the JSON file may carry provenance
 * comments alongside [templates] (unknown keys are ignored per ADR-004).
 */
@Serializable
public data class DietTemplateLibrary(
    public val templates: List<DietTemplate> = emptyList(),
)

/**
 * Loads the shipped template library. JSON ships as a plain resource in this
 * module (`templates/onboarding_templates.json`); the reader is injectable so
 * platform fronts (Android assets, desktop classpath) can feed it without
 * touching the applier — the default reader is the JVM classloader, which
 * covers every JVM test and the desktop purity target.
 */
public object OnboardingTemplates {
    public const val RESOURCE_PATH: String = "templates/onboarding_templates.json"

    /** Reads the raw JSON; inject an asset-backed reader on other platforms. */
    public fun interface Reader {
        public fun read(path: String): String
    }

    public fun load(reader: Reader = defaultReader): List<DietTemplate> =
        DocumentCodec.json
            .decodeFromString(serializer<DietTemplateLibrary>(), reader.read(RESOURCE_PATH))
            .templates

    public val defaultReader: Reader =
        Reader { path ->
            OnboardingTemplates::class.java.classLoader
                .getResourceAsStream(path)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: error("template resource not found: $path")
        }
}
