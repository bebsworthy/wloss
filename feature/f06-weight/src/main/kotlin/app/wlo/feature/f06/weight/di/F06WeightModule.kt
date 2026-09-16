package app.wlo.feature.f06.weight.di

import app.wlo.core.data.TargetsWriters
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.datastore.SettingsStore
import app.wlo.core.documents.DocumentCodec
import app.wlo.core.model.WeightGoalSafetyInput
import app.wlo.feature.f06.weight.state.BodyFatViewModel
import app.wlo.feature.f06.weight.state.BodySectionUi
import app.wlo.feature.f06.weight.state.GoalProgressLoader
import app.wlo.feature.f06.weight.state.GoalTargetEditor
import app.wlo.feature.f06.weight.state.LogbookDeletionRecoveryStore
import app.wlo.feature.f06.weight.state.LogbookViewModel
import app.wlo.feature.f06.weight.state.WeighInViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * F06 graph (ARCHITECTURE §2.2 feature shape): the weigh-in and body-fat
 * state holders. Inputs arrive through spine doors (:core:data repositories)
 * bound by the composition root.
 */
public val f06WeightModule: Module =
    module {
        factory {
            val writers = get<TargetsWriters>()
            GoalTargetEditor(get(), get()) { id, version, document ->
                if (version == null) {
                    writers.studio().writeFirst(id, document)
                } else {
                    writers.studio().writeRevision(id, version, document)
                }
            }
        }
        factory {
            val documents = get<JsonDocumentStore>()
            GoalProgressLoader(
                clock = get(),
                targets = get(),
                dayProjection = get(),
                readGoalSafetyInput = { profileId ->
                    documents.readText("weight/goal-safety-v1/$profileId")?.let { text ->
                        runCatching {
                            DocumentCodec.json.decodeFromString(WeightGoalSafetyInput.serializer(), text)
                        }.getOrNull()
                    }
                },
            )
        }
        viewModel { (initialSheetOpen: Boolean, initialSection: BodySectionUi) ->
            WeighInViewModel(
                clock = get(),
                profiles = get(),
                weighIns = get(),
                measurements = get(),
                goalProgressLoader = get(),
                goalTargetEditor = get(),
                massUnits = get<SettingsStore>().massUnit,
                initialSheetOpen = initialSheetOpen,
                initialSection = initialSection,
                savedStateHandle = get(),
            )
        }
        viewModel {
            BodyFatViewModel(
                clock = get(),
                profiles = get(),
                measurements = get(),
                settings = get(),
                savedStateHandle = get(),
            )
        }
        viewModel {
            LogbookViewModel(
                clock = get(),
                profiles = get(),
                weighIns = get(),
                measurements = get(),
                settings = get(),
                recovery = LogbookDeletionRecoveryStore(get()),
                savedStateHandle = get(),
            )
        }
    }
