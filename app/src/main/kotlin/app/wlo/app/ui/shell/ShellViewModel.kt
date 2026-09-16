package app.wlo.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.wlo.core.common.getOrNull
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.TargetsRepository
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.feature.f01.onboarding.domain.FinishOnboarding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The shell gate: which surface the Hub route renders right now. */
public sealed interface ShellState {
    /** First emissions in flight — render a calm blank frame, never a flash. */
    public data object Loading : ShellState

    /** No completion flag or legacy completed plan: first-run setup owns top-level routes. */
    public data object Fresh : ShellState

    /** Onboarding is complete; a diet plan is optional. */
    public data object Onboarded : ShellState
}

/**
 * Cold-start decision: the explicit completion flag is authoritative for the
 * weight-first flow. A profile with legacy Targets remains accepted so an
 * existing installation is not sent back through onboarding after upgrade.
 */
public class ShellViewModel(
    profiles: ProfileRepository,
    targets: TargetsRepository,
    documents: JsonDocumentStore,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val profileState: Flow<Pair<Boolean, Boolean>> =
        profiles
            .observeActive()
            .flatMapLatest { profileResult ->
                val profile = profileResult.getOrNull()
                if (profile == null || profile.archivedAt != null) {
                    flowOf(false to false)
                } else {
                    targets
                        .observeCurrent(profile.id)
                        .map { targetsResult -> true to (targetsResult.getOrNull() != null) }
                }
            }

    public val state: StateFlow<ShellState> =
        combine(profileState, documents.observeFlag(FinishOnboarding.FLAG_COMPLETE)) {
            (profileExists, hasTargets),
            complete,
            ->
            when {
                (profileExists && hasTargets) || complete -> ShellState.Onboarded
                else -> ShellState.Fresh
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ShellState.Loading)
}
