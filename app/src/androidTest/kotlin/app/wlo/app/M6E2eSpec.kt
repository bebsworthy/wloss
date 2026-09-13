package app.wlo.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.wlo.core.common.ClockPort
import app.wlo.core.common.WloResult
import app.wlo.core.consent.ConsentChain
import app.wlo.core.consent.ConsentDecision
import app.wlo.core.consent.ConsentEntry
import app.wlo.core.data.DiaryRepository
import app.wlo.core.data.FoodRepository
import app.wlo.core.data.MeasurementRepository
import app.wlo.core.data.NewCustomFood
import app.wlo.core.data.NewDiaryEntry
import app.wlo.core.data.NewMeasurement
import app.wlo.core.data.NewProfile
import app.wlo.core.data.PantryRepository
import app.wlo.core.data.ProfileRepository
import app.wlo.core.data.RecipeRepository
import app.wlo.core.data.ShoppingListRepository
import app.wlo.core.data.TargetsWriteOutcome
import app.wlo.core.data.TargetsWriters
import app.wlo.core.database.ConsentLedgerEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.model.EntryVia
import app.wlo.core.model.MeasurementKind
import app.wlo.core.model.MeasurementSource
import app.wlo.core.model.Sex
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone

/**
 * The wipe-restore E2E's shared contract (scripts/e2e-wipe-restore.sh): the
 * SEEDER writes exactly this data through the REAL repositories, and the
 * RESTORE-ASSERT test expects exactly this logical state after a real
 * uninstall + reinstall (the backup file is the only survivor). M2–M5
 * surfaces: profile (M2) + measurements/diary/revisions/foods (M3/M4) +
 * recipe/list/pantry (M5) + the consent ledger chain (M6).
 *
 * PLAN/PLAN_SLOTS note: whole-plan generation is an engine deal (F03's
 * planner, seeded recipes required); the plan tables round-trip through the
 * JVM suite and the assembler, so the E2E keeps its seed deterministic.
 */
public object M6E2eSpec {
    public const val PASSPHRASE: String = "wlo-e2e-passphrase"

    // Spot values asserted after restore.
    public const val BIRTH_YEAR: Int = 1991
    public const val HEIGHT_CM: Double = 168.0
    public const val START_WEIGHT_KG: Double = 78.4
    public const val SPOT_WEIGH_IN_KG: Double = 76.9
    public const val FOOD_NAME: String = "E2E Lentils"
    public const val FOOD_KCAL: Double = 116.0
    public const val LIST_ITEM_NAME: String = "e2e olive oil"
    public const val PANTRY_ITEM_NAME: String = "e2e olive oil"
    public const val RECIPE_NAME: String = "E2E Stew"

    // Exact counts (what the seeder writes = what restore must land).
    public const val EXPECT_PROFILES: Int = 1
    public const val EXPECT_MEASUREMENTS: Int = 3 // incl. one double-weigh-in day (R-B8)
    public const val EXPECT_DIARY_ENTRIES: Int = 2
    public const val EXPECT_DIARY_REVISIONS: Int = 1 // one corrected entry
    public const val EXPECT_FOODS: Int = 1
    public const val EXPECT_RECIPES: Int = 1
    public const val EXPECT_LIST_ITEMS: Int = 1
    public const val EXPECT_PANTRY_ITEMS: Int = 1
    public const val EXPECT_CONSENT_ENTRIES: Int = 2 // grant food-photo, then grant insights-chat

    private fun koin(): org.koin.core.Koin =
        org.koin.core.context.GlobalContext
            .get()

    /** Seeds the full spec; returns the profile id (the seeder asserts counts). */
    public fun seed(): String =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val clock = koin().get<ClockPort>()
            val zone = TimeZone.currentSystemDefault()
            val now = clock.now()
            val today =
                app.wlo.core.common.DayBoundary
                    .epochDay(now, zone)

            val profiles = koin().get<ProfileRepository>()
            val measurements = koin().get<MeasurementRepository>()
            val foods = koin().get<FoodRepository>()
            val diary = koin().get<DiaryRepository>()
            val list = koin().get<ShoppingListRepository>()
            val pantry = koin().get<PantryRepository>()
            val recipes = koin().get<RecipeRepository>()
            val writers = koin().get<TargetsWriters>()
            val db = koin().get<WloDatabase>()

            val created =
                profiles.create(
                    NewProfile(sex = Sex.FEMALE, birthYear = BIRTH_YEAR, heightCm = HEIGHT_CM, startWeightKg = START_WEIGHT_KG),
                    now,
                )
            val profileId = (created as WloResult.Ok).value.id

            // R-B8: three weigh-ins, two of them the same day.
            measurements.append(NewMeasurement(profileId, today - 2, MeasurementKind.WEIGHT, 78.4, MeasurementSource.MANUAL, now))
            measurements.append(
                NewMeasurement(profileId, today - 1, MeasurementKind.WEIGHT, SPOT_WEIGH_IN_KG, MeasurementSource.SCALE, now),
            )
            measurements.append(NewMeasurement(profileId, today - 1, MeasurementKind.WEIGHT, 77.2, MeasurementSource.SCALE, now))

            // Food + two diary entries, one of them corrected (→ 1 revision row).
            val foodId =
                (
                    foods.createCustomFood(
                        NewCustomFood(
                            profileId = profileId,
                            name = FOOD_NAME,
                            kcalPer100g = FOOD_KCAL,
                            proteinGPer100g = 9.0,
                            carbGPer100g = 20.0,
                            fatGPer100g = 0.4,
                            macrosVerified = true,
                        ),
                        now,
                    ) as WloResult.Ok
                ).value.id
            val e1 =
                diary.logEntry(
                    NewDiaryEntry(
                        profileId = profileId,
                        dayEpochDay = today,
                        mealSlot = app.wlo.core.model.MealSlot.LUNCH,
                        foodItemId = foodId,
                        quantity = 150.0,
                        unit = "g",
                        enteredVia = EntryVia.MANUAL_SEARCH,
                    ),
                    now,
                )
            val e1Id = (e1 as WloResult.Ok).value.id
            diary.logEntry(
                NewDiaryEntry(
                    profileId = profileId,
                    dayEpochDay = today,
                    mealSlot = app.wlo.core.model.MealSlot.DINNER,
                    foodItemId = foodId,
                    quantity = 200.0,
                    unit = "g",
                    enteredVia = EntryVia.MANUAL_SEARCH,
                ),
                now,
            )
            diary.editEntry(
                e1Id,
                app.wlo.core.data
                    .EditDiaryEntry(mealSlot = app.wlo.core.model.MealSlot.LUNCH, quantity = 180.0, unit = "g"),
                now,
            )

            // M5 pipeline: list row (creates the canonical grocery item),
            // pantry stock over the same item, one recipe referencing it.
            list.addItem(profileId = profileId, name = LIST_ITEM_NAME, qty = 250.0, unit = "ml", at = now)
            val grocery = koin().get<app.wlo.core.data.GroceryRepository>()
            val groceryId =
                (grocery.search(profileId, "olive", 1) as WloResult.Ok).value.firstOrNull()?.id
                    ?: error("grocery seed item missing")
            pantry.upsert(
                app.wlo.core.data.NewPantryItem(
                    profileId = profileId,
                    groceryItemId = groceryId,
                    name = PANTRY_ITEM_NAME,
                    qty = 500.0,
                    unit = "g",
                ),
                now,
            )
            recipes.create(
                app.wlo.core.data.NewRecipe(
                    profileId = profileId,
                    name = RECIPE_NAME,
                    servingsBase = 2.0,
                    ingredients =
                        listOf(
                            app.wlo.core.model.RecipeIngredient(
                                groceryItemId = groceryId,
                                name = PANTRY_ITEM_NAME,
                                qty = 100.0,
                                unit = "g",
                            ),
                        ),
                    steps = listOf("simmer"),
                    nutrition =
                        app.wlo.core.model.NutritionPerServing(
                            kcal = 350.0,
                            proteinG = 8.0,
                            carbG = 60.0,
                            fatG = 4.0,
                            fiberG = 3.0,
                        ),
                ),
                now,
            )

            // Consent ledger (Room rows + real hash chain) — the M6 PART B
            // wiring preview: PART B swaps the in-memory ledger for this store.
            val capabilities =
                listOf(
                    app.wlo.core.model.ConsentCapability.FOOD_PHOTO,
                    app.wlo.core.model.ConsentCapability.INSIGHTS_CHAT,
                )
            var prev = ConsentEntry.GENESIS_PREV_HASH
            var seq = 1L
            for (cap in capabilities) {
                val hash =
                    ConsentChain.hashOf(
                        ConsentEntry(
                            seq = seq,
                            atEpochMs = 1_750_000_000_000 + seq,
                            capability = cap,
                            decision = ConsentDecision.GRANT,
                            prevHashHex = prev,
                            hashHex = "",
                        ),
                    )
                db.consentLedger().append(
                    ConsentLedgerEntity(
                        seq = seq,
                        profileId = profileId,
                        capability = cap.wireName,
                        decision = "grant",
                        atEpochMs = 1_750_000_000_000 + seq,
                        prevHashHex = prev,
                        hashHex = hash,
                    ),
                )
                prev = hash
                seq++
            }

            // Targets v1 from the shipped default template (the wizard's Start write).
            val assets = context.assets
            val templates: List<app.wlo.core.documents.DietTemplate> =
                app.wlo.core.documents.OnboardingTemplates.load { path ->
                    assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            val template = templates.firstOrNull { it.isDefault } ?: templates.first()
            val targetsDoc =
                app.wlo.core.documents.DietTemplateApplier.toTargetsDocument(
                    template,
                    app.wlo.feature.f01.onboarding.domain.FinishOnboarding.applierContext(
                        sex = Sex.FEMALE,
                        birthYear = BIRTH_YEAR,
                        heightCm = HEIGHT_CM,
                        currentWeightKg = START_WEIGHT_KG,
                        goalWeightKg = 74.0,
                        pacePctPerWeek = 0.5,
                        formulaTdeeKcal = null,
                    ),
                )
            val write = writers.studio().writeFirst(profileId = profileId, document = targetsDoc)
            check(write is TargetsWriteOutcome.Written) { "targets write failed" }

            assertCounts(db)
            profileId
        }

    /** Post-seed (and post-restore) count assertions against the raw DAOs. */
    public fun assertCounts(db: WloDatabase): Unit =
        runBlocking {
            check(db.profiles().all().size == EXPECT_PROFILES) { "profiles ${db.profiles().all().size}" }
            check(db.measurementEvents().all().size == EXPECT_MEASUREMENTS) { "measurements ${db.measurementEvents().all().size}" }
            check(db.diaryEntries().all().size == EXPECT_DIARY_ENTRIES) { "diary ${db.diaryEntries().all().size}" }
            check(db.diaryEntryRevisions().all().size == EXPECT_DIARY_REVISIONS) { "revisions ${db.diaryEntryRevisions().all().size}" }
            check(db.foodItems().countAll() == EXPECT_FOODS) { "foods ${db.foodItems().countAll()}" }
            check(db.recipes().all().size == EXPECT_RECIPES) { "recipes ${db.recipes().all().size}" }
            check(db.listItems().all().size == EXPECT_LIST_ITEMS) { "list ${db.listItems().all().size}" }
            check(db.pantryItems().all().size == EXPECT_PANTRY_ITEMS) { "pantry ${db.pantryItems().all().size}" }
            check(db.consentLedger().all().size == EXPECT_CONSENT_ENTRIES) { "consent ${db.consentLedger().all().size}" }
        }
}
