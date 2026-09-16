package app.wlo.feature.f10.hub.di

import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.documents.DocumentCodec
import app.wlo.core.model.WeightGoalSafetyInput
import app.wlo.feature.f10.hub.state.HubViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * F10 graph (ARCHITECTURE §2.2 feature shape): the Hub state holder. The Hub
 * computes no science — its spine doors (projection, weigh-ins, diary,
 * targets) are bound by the composition root.
 */
public val f10HubModule: Module =
    module {
        viewModel {
            val documents = get<JsonDocumentStore>()
            HubViewModel(
                clock = get(),
                profiles = get(),
                dayProjection = get(),
                targets = get(),
                weighIns = get(),
                readGoalSafetyInput = { profileId ->
                    documents.readText("weight/goal-safety-v1/$profileId")?.let { text ->
                        runCatching {
                            DocumentCodec.json.decodeFromString(WeightGoalSafetyInput.serializer(), text)
                        }.getOrNull()
                    }
                },
                diary = get(),
                planner = get(),
                massUnit = get<app.wlo.core.datastore.SettingsStore>().massUnit,
            )
        }
    }
