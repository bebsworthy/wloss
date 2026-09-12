package app.wlo.core.testing

/**
 * Loads a fixture JSON file from the test classpath. Conventions: engine
 * golden scenarios live under `golden/`, versioned-document fixtures under
 * `documents/` (ARCHITECTURE §2.6, ADR-004 rule 4).
 */
public object GoldenFixtures {
    /** Reads `/<path>` from the test runtime classpath, e.g. `golden/bmi_scenarios.json`. */
    public fun load(path: String): String {
        val stream =
            GoldenFixtures::class.java.classLoader.getResourceAsStream(path)
                ?: error("fixture not found on test classpath: $path")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
