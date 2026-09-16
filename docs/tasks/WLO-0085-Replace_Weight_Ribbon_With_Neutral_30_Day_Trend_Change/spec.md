# Objective
Remove the existing shifted 30-day reference line and shaded progress ribbon, retaining only a neutral textual 30-day canonical-trend change when sufficient history exists.

# Scope
- Delete ribbon/reference rendering and parameters from WloTrendChart.
- Derive the 30-day change from the canonical 30-day trend window.
- Render plain neutral text on the Weight trend card; no goal coloring, animation, scrubber, or preference.
- Hide the value until the canonical window spans 30 calendar days.
- Update focused unit and reliability tests.

# Acceptance
The chart contains only raw dots and the canonical trend line. A qualifying series shows a unit-correct neutral 30-day change; sparse history shows no invented value.


## Verification
- `:core:data:jvmTest`, `:feature:f06-weight:testDebugUnitTest`, and `:core:designsystem:compileDebugKotlin` pass.
- Touched-module ktlint checks, design-system/F06 detekt, `checkArchitecture`, and `git diff --check` pass.
- The chart API no longer accepts or renders a shifted reference series; sparse canonical history returns no 30-day value.

- `:core:designsystem:lintDebug` and `:feature:f06-weight:lintDebug` pass.
