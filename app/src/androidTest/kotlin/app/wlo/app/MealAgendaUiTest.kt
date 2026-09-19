package app.wlo.app

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.wlo.core.common.WloResult
import app.wlo.core.common.getOrNull
import app.wlo.core.data.AgendaFood
import app.wlo.core.data.AgendaItem
import app.wlo.core.data.MealAgendaRepository
import app.wlo.core.data.NewProfile
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.TargetsWriteOutcome
import app.wlo.core.data.TargetsWriters
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.documents.Cadence
import app.wlo.core.documents.Energy
import app.wlo.core.documents.FiberTarget
import app.wlo.core.documents.Goal
import app.wlo.core.documents.MacroSplit
import app.wlo.core.documents.Macros
import app.wlo.core.documents.TargetsDocument
import app.wlo.core.model.Sex
import app.wlo.feature.f01.onboarding.domain.FinishOnboarding
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Production repositories + production Plan route, with isolated visual-test profile. */
@RunWith(AndroidJUnit4::class)
public class MealAgendaUiTest {
    @Test public fun agendaCalendarAcquisitionSwipeAndOverage(): Unit =
        runBlocking {
            val koin = GlobalContext.get()
            val profile =
                koin
                    .get<ProfileRepository>()
                    .create(
                        NewProfile(sex = Sex.FEMALE, birthYear = 1990, heightCm = 168.0, startWeightKg = 82.0),
                        Instant.fromEpochMilliseconds(-System.currentTimeMillis()),
                    ).getOrNull()!!
            koin.get<JsonDocumentStore>().writeFlag(FinishOnboarding.FLAG_COMPLETE, true)
            val target =
                TargetsDocument(
                    Goal(74.0, -0.5),
                    Energy(cadence = Cadence.DAILY, budgetKcal = 1900.0, floorKcal = 1500.0),
                    Macros(
                        MacroSplit.Custom(
                            proteinG = 120.0,
                            proteinPct =
                                480.0 / 19,
                            carbPct = 880.0 / 19,
                            fatPct = 540.0 / 19,
                        ),
                        90.0,
                    ),
                    fiber = FiberTarget(25.0),
                )
            check(koin.get<TargetsWriters>().studio().writeFirst(profile.id, target) is TargetsWriteOutcome.Written)
            val repo = koin.get<MealAgendaRepository>()
            val day = LocalDate(2026, 9, 22).toEpochDays().toLong()
            val foods =
                listOf(
                    AgendaFood("Tomato salad", "bowl", 90.0, 2.0, 8.0, 6.0, 3.0),
                    AgendaFood("Lemon chickpeas & couscous", "serving", 620.0, 24.0, 86.0, 20.0, 14.0),
                    AgendaFood("Plain yogurt", "pot (125 g)", 80.0, 5.0, 7.0, 3.5, 0.0),
                    AgendaFood("Banana", "medium banana", 105.0, 1.3, 27.0, 0.4, 3.0),
                    AgendaFood("Bread", "slice (35 g)", 90.0, 3.0, 17.0, 1.0, 2.0),
                    AgendaFood("Wine", "glass (150 ml)", 125.0, 0.0, 4.0, 0.0, 0.0),
                    AgendaFood("Milk", "glass (200 ml)", 100.0, 7.0, 10.0, 3.5, 0.0),
                )
            for (fixtureDay in day - 1..day) {
                foods.forEachIndexed { i, food ->
                    check(
                        repo.add(
                            profile.id,
                            AgendaItem(
                                "${profile.id}-$fixtureDay-lunch-$i",
                                fixtureDay,
                                "lunch",
                                food,
                                if (i ==
                                    4
                                ) {
                                    2.0
                                } else {
                                    1.0
                                },
                            ),
                        ) is WloResult.Ok,
                    )
                }
            }
            repo.add(
                profile.id,
                AgendaItem(
                    "${profile.id}-breakfast",
                    day,
                    "breakfast",
                    AgendaFood(
                        "Yogurt, oats & berries",
                        kcal = 380.0,
                        protein = 22.0,
                        carbs = 48.0,
                        fat = 11.0,
                        fiber = 7.0,
                    ),
                    1.0,
                ),
            )
            repo.add(
                profile.id,
                AgendaItem(
                    "${profile.id}-dinner",
                    day,
                    "dinner",
                    AgendaFood(
                        "Tomato & white bean pasta",
                        kcal = 650.0,
                        protein = 25.0,
                        carbs = 92.0,
                        fat = 20.0,
                        fiber = 13.0,
                    ),
                    1.0,
                ),
            )
            repo.add(profile.id, AgendaItem("${profile.id}-dinner-yogurt", day, "dinner", foods[2], 1.0))
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val intent =
                Intent(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    MainActivity::class.java,
                ).setData(Uri.parse("wlo://plan"))
            val scenario = ActivityScenario.launch<MainActivity>(intent)
            assertTrue(device.wait(Until.hasObject(By.desc("Choose date")), 10000))
            device.findObject(By.desc("Choose date")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Tuesday, September 22, 2026")), 5000))
            device.findObject(By.text("Tuesday, September 22, 2026")).click()
            assertTrue(device.wait(Until.hasObject(By.text("2,410")), 10000))
            device.takeScreenshot(File("/sdcard/agenda-overplanning.png"))
            device.findObject(By.desc("Add food to Breakfast")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Add food or drink")), 5000))
            assertNotNull(device.findObject(By.text("Search foods and recipes")))
            device.takeScreenshot(File("/sdcard/agenda-add.png"))
            device.pressBack()
            // Collapse breakfast, leaving the lunch fixture near the top for review.
            device.findObject(By.text("Breakfast")).click()
            val row = device.findObject(By.text("Tomato salad"))
            assertNotNull(row)
            row.parent.swipe(Direction.LEFT, 0.75f) // Reveal only; the food must remain.
            assertNotNull(device.findObject(By.text("Tomato salad")))
            assertTrue(device.wait(Until.hasObject(By.desc("Replace Tomato salad")), 5000))
            device.takeScreenshot(File("/sdcard/agenda-actions.png"))
            device.findObject(By.desc("Replace Tomato salad")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Replace food")), 5000))
            val portion = device.findObject(By.clazz("android.widget.EditText").text("1"))
            assertNotNull(portion)
            portion.text = "2"
            scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.text("Replace food")), 5000))
            assertNotNull(device.findObject(By.clazz("android.widget.EditText").text("2")))
            device.findObject(By.text("Replace")).click()
            assertTrue(device.wait(Until.hasObject(By.text("2,500")), 5000))
            device.findObject(By.text("Tomato salad")).click()
            device.findObject(By.desc("Replace Tomato salad")).click()
            device.findObject(By.clazz("android.widget.EditText").text("2")).text = "1"
            device.findObject(By.text("Replace")).click()
            assertTrue(device.wait(Until.hasObject(By.text("2,410")), 5000))
            device.findObject(By.text("Breakfast")).click()
            SystemClock.sleep(250)
            device.takeScreenshot(File("/sdcard/agenda-final.png"))
            // Keep the production app open on this state for the overlay capture.
        }
}
