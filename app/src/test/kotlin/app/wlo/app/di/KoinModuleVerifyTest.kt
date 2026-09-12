package app.wlo.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import app.wlo.feature.f02.food.state.DiaryViewModel
import app.wlo.feature.f02.food.state.FoodLogViewModel
import app.wlo.feature.f06.weight.state.WeighInViewModel
import org.koin.dsl.module
import org.koin.test.verify.ParameterTypeInjection
import org.koin.test.verify.verify
import kotlin.test.Test

/**
 * Compile-safety for the composition root: every definition's dependencies —
 * including the F01 module included in [appModule] — must be resolvable inside
 * the graph. External inputs: the Android context (`get<Context>()`). The
 * DataStore type [SettingsStoreFactory] wraps is created internally from a
 * file path, so it is not an external input; the clock is bound in-graph.
 */
public class KoinModuleVerifyTest {
    @Test
    public fun wholeGraphIsInternallyConsistent() {
        val everything =
            module {
                includes(appModule, platformModule)
            }
        everything.verify(
            listOf(Context::class, DataStore::class),
            listOf(
                ParameterTypeInjection(WeighInViewModel::class, listOf(Boolean::class)),
                ParameterTypeInjection(FoodLogViewModel::class, listOf(Boolean::class)),
                ParameterTypeInjection(DiaryViewModel::class, listOf(Long::class, String::class)),
            ),
        )
    }
}
