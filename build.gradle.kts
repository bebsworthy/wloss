// WLO root — convention plugins live in build-logic; this file only wires the
// architecture enforcement (ARCHITECTURE.md §2.3 / ADR-005).
plugins {
    id("wlo.architecture-check")
}
