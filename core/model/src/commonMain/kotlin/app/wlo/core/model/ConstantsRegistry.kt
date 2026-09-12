package app.wlo.core.model

/**
 * Versioned constants registry (R-A1, DRY anchor §2.5): the single owner of
 * physiological constants and formula versions. F07's "Algorithms" page renders
 * from these values — they are never duplicated in engines or copy.
 *
 * Seed for M1; entries grow with the engines that need them. Bump [VERSION]
 * whenever a value or formula version changes.
 */
public object ConstantsRegistry {
    /** Registry schema/content version (R-A1). */
    public const val VERSION: Int = 1

    /** Energy equivalence of body-fat mass (R-A1: 7,700 kcal per kg). */
    public const val KCAL_PER_KG_FAT: Double = 7_700.0

    /** Atwater factors used by F07 energy math. */
    public const val KCAL_PER_G_PROTEIN: Double = 4.0
    public const val KCAL_PER_G_CARB: Double = 4.0
    public const val KCAL_PER_G_FAT: Double = 9.0

    /** Formula version stamped into [app.wlo.core.model.Provenance.Derived]. */
    public const val BMI_FORMULA_VERSION: String = "bmi/quetelet-v1"
}
