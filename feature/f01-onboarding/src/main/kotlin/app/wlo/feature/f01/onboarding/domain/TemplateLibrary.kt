package app.wlo.feature.f01.onboarding.domain

import app.wlo.core.documents.DietTemplate

/**
 * The shipped template library door (F01 §3): the wizard consumes templates
 * through this seam so the composition root can back it with Android assets
 * (or a test fixture) without the feature touching platform IO. Loading is
 * lazy — the gallery fills on first wizard composition, never at graph build.
 */
public fun interface TemplateLibrary {
    /** All shipped templates; empty only when the library itself failed to load. */
    public fun load(): List<DietTemplate>
}
