package app.wlo.core.vault

import app.wlo.core.database.AisleCorrectionEntity
import app.wlo.core.database.ConsentLedgerEntity
import app.wlo.core.database.DiaryEntryEntity
import app.wlo.core.database.DiaryEntryRevisionEntity
import app.wlo.core.database.FoodItemEntity
import app.wlo.core.database.GroceryItemEntity
import app.wlo.core.database.HealthConnectImportLogEntity
import app.wlo.core.database.HealthConnectRecordEntity
import app.wlo.core.database.HealthConnectSyncStateEntity
import app.wlo.core.database.ListItemEntity
import app.wlo.core.database.MeasurementEventAttrEntity
import app.wlo.core.database.MeasurementEventEntity
import app.wlo.core.database.PantryItemEntity
import app.wlo.core.database.PlanSlotEntity
import app.wlo.core.database.PlanVersionEntity
import app.wlo.core.database.ProfileEntity
import app.wlo.core.database.ProvenanceEntity
import app.wlo.core.database.RecipeEntity
import app.wlo.core.database.TargetsVersionEntity
import app.wlo.core.database.WloDatabase
import app.wlo.core.datastore.JsonDocumentStore
import app.wlo.core.datastore.SettingsStore

/**
 * Reads every store into a [BackupPayload] — "what to assemble is common and
 * unit-tested" (ARCHITECTURE §2.4). Append-only tables are dumped verbatim
 * (R-B8: hidden/archived rows ride along — hide-not-delete must survive
 * restore); derived/audit tables are excluded per the [BackupSchema] ruling.
 */
public class SnapshotAssembler(
    private val db: WloDatabase,
    private val settings: SettingsStore,
    private val documents: JsonDocumentStore,
) {
    public suspend fun assemble(options: BackupOptions = BackupOptions()): BackupPayload {
        val attrRows = db.measurementEventAttrs().all().groupBy(MeasurementEventAttrEntity::eventId)
        val revisionRows = db.diaryEntryRevisions().all().groupBy(DiaryEntryRevisionEntity::entryId)
        return BackupPayload(
            profiles = db.profiles().all().map { it.toRow() },
            measurements =
                db.measurementEvents().all().map { event ->
                    event.toRow(
                        attrs =
                            attrRows[event.id].orEmpty().map {
                                MeasurementAttrRow(attr = it.attr, valueText = it.valueText, valueReal = it.valueReal)
                            },
                    )
                },
            diary =
                db.diaryEntries().all().map { entry ->
                    entry.toRow(
                        revisions =
                            revisionRows[entry.id].orEmpty().map { it.toRow() },
                    )
                },
            targets = db.targetsVersions().all().map { it.toRow() },
            provenance = db.provenance().all().map { it.toRow() },
            consentLedger = db.consentLedger().all().map { it.toRow() },
            foodItems = db.foodItems().all().map { it.toRow() },
            recipes = db.recipes().all().map { it.toRow() },
            groceryItems = db.groceryItems().all().map { it.toRow() },
            plans = db.planVersions().all().map { it.toRow() },
            planSlots = db.planSlots().all().map { it.toRow() },
            listItems = db.listItems().all().map { it.toRow() },
            pantryItems = db.pantryItems().all().map { it.toRow() },
            aisleCorrections = db.aisleCorrections().all().map { it.toRow() },
            healthConnect =
                HealthConnectSection(
                    records = db.healthConnect().allRecords().map { it.toRow() },
                    syncStates = db.healthConnect().allSyncStates().map { it.toRow() },
                    logs = db.healthConnect().allLogs().map { it.toRow() },
                ),
            settings = SettingsSection(values = settings.exportKnownSettings()),
            documents = DocumentsSection(values = documents.snapshotDocuments()),
            // R-U18: blobs ride ONLY the explicit includeVault opt-in. The
            // partition reader is PART B's (photo retention, M4) — v1 assembles
            // an empty vault section either way, keeping the wire shape stable.
            vaultBlobs = if (options.includeVault) collectVaultBlobs() else emptyList(),
        )
    }

    /** PART B: enumerate partition blobs (VaultFileStore) for opted-in bundles. */
    private suspend fun collectVaultBlobs(): List<VaultBlobRow> = emptyList()
}

internal fun HealthConnectRecordEntity.toRow(): HealthConnectRecordRow =
    HealthConnectRecordRow(
        recordId,
        profileId,
        measurementEventId,
        dataOriginPackage,
        clientRecordId,
        clientRecordVersion,
        recordingMethod,
        lastModifiedAtEpochMs,
        capturedAtEpochMs,
        zoneOffsetSeconds,
        metric,
        canonicalValue,
    )

internal fun HealthConnectSyncStateEntity.toRow(): HealthConnectSyncStateRow =
    HealthConnectSyncStateRow(profileId, metric, changeToken, lastSyncAtEpochMs)

internal fun HealthConnectImportLogEntity.toRow(): HealthConnectLogRow =
    HealthConnectLogRow(id, profileId, atEpochMs, outcome, inserted, updated, deleted, skipped, conflicts, retryable, detail)

// --- entity → row mappers (mechanical; field names pinned by the row types) --

internal fun ProfileEntity.toRow(): ProfileRow =
    ProfileRow(
        id = id,
        sex = sex,
        birthYear = birthYear,
        heightCm = heightCm,
        startWeightKg = startWeightKg,
        activityLevel = activityLevel,
        unitPreference = unitPreference,
        createdAtEpochMs = createdAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
        weightPolicyTimeZoneId = weightPolicyTimeZoneId,
        weightPolicyVersion = weightPolicyVersion,
    )

internal fun MeasurementEventEntity.toRow(attrs: List<MeasurementAttrRow>): MeasurementRow =
    MeasurementRow(
        id = id,
        profileId = profileId,
        dayEpochDay = dayEpochDay,
        kind = kind,
        valueReal = valueReal,
        unit = unit,
        source = source,
        capturedAtEpochMs = capturedAtEpochMs,
        note = note,
        hiddenAtEpochMs = hiddenAtEpochMs,
        hiddenReason = hiddenReason,
        attrs = attrs,
    )

internal fun DiaryEntryEntity.toRow(revisions: List<DiaryRevisionRow>): DiaryEntryRow =
    DiaryEntryRow(
        id = id,
        profileId = profileId,
        dayEpochDay = dayEpochDay,
        mealSlot = mealSlot,
        foodItemId = foodItemId,
        textHint = textHint,
        quantity = quantity,
        unit = unit,
        computedKcal = computedKcal,
        itemJson = itemJson,
        computedProteinG = computedProteinG,
        computedCarbG = computedCarbG,
        computedFatG = computedFatG,
        computedFiberG = computedFiberG,
        enteredVia = enteredVia,
        provenanceScalar = provenanceScalar,
        revision = revision,
        createdAtEpochMs = createdAtEpochMs,
        editedAtEpochMs = editedAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
        hiddenAtEpochMs = hiddenAtEpochMs,
        hiddenReason = hiddenReason,
        revisions = revisions,
    )

internal fun DiaryEntryRevisionEntity.toRow(): DiaryRevisionRow =
    DiaryRevisionRow(
        id = id,
        entryId = entryId,
        revision = revision,
        mealSlot = mealSlot,
        foodItemId = foodItemId,
        textHint = textHint,
        quantity = quantity,
        unit = unit,
        computedKcal = computedKcal,
        itemJson = itemJson,
        computedProteinG = computedProteinG,
        computedCarbG = computedCarbG,
        computedFatG = computedFatG,
        computedFiberG = computedFiberG,
        enteredVia = enteredVia,
        editedAtEpochMs = editedAtEpochMs,
    )

internal fun TargetsVersionEntity.toRow(): TargetsVersionRow =
    TargetsVersionRow(
        id = id,
        profileId = profileId,
        version = version,
        documentJson = documentJson,
        writtenBy = writtenBy,
        createdAtEpochMs = createdAtEpochMs,
        supersededAtEpochMs = supersededAtEpochMs,
    )

internal fun ProvenanceEntity.toRow(): ProvenanceRow =
    ProvenanceRow(
        profileId = profileId,
        dayEpochDay = dayEpochDay,
        scalar = scalar,
        method = method,
        formulaVersion = formulaVersion,
        inputsHash = inputsHash,
        computedAtEpochMs = computedAtEpochMs,
    )

internal fun ConsentLedgerEntity.toRow(): ConsentLedgerRow =
    ConsentLedgerRow(
        seq = seq,
        profileId = profileId,
        atEpochMs = atEpochMs,
        capability = capability,
        decision = decision,
        prevHashHex = prevHashHex,
        hashHex = hashHex,
    )

internal fun FoodItemEntity.toRow(): FoodItemRow =
    FoodItemRow(
        id = id,
        profileId = profileId,
        name = name,
        brand = brand,
        aliases = aliases,
        kcalPer100g = kcalPer100g,
        proteinGPer100g = proteinGPer100g,
        carbGPer100g = carbGPer100g,
        fatGPer100g = fatGPer100g,
        fiberGPer100g = fiberGPer100g,
        servingPresetsJson = servingPresetsJson,
        source = source,
        macrosVerified = macrosVerified,
        verifiedAtEpochMs = verifiedAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
    )

internal fun RecipeEntity.toRow(): RecipeRow =
    RecipeRow(
        recipeId = recipeId,
        version = version,
        profileId = profileId,
        name = name,
        servingsBase = servingsBase,
        cuisine = cuisine,
        slotsJson = slotsJson,
        tagsJson = tagsJson,
        fodmapTagsJson = fodmapTagsJson,
        ingredientsJson = ingredientsJson,
        stepsJson = stepsJson,
        kcalPerServing = kcalPerServing,
        proteinGPerServing = proteinGPerServing,
        carbGPerServing = carbGPerServing,
        fatGPerServing = fatGPerServing,
        fiberGPerServing = fiberGPerServing,
        nutritionBasis = nutritionBasis,
        source = source,
        license = license,
        rating = rating,
        lastPlannedAtEpochMs = lastPlannedAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
    )

internal fun GroceryItemEntity.toRow(): GroceryItemRow =
    GroceryItemRow(
        id = id,
        profileId = profileId,
        name = name,
        aisle = aisle,
        defaultUnit = defaultUnit,
        densityGPerMl = densityGPerMl,
        gramsPerPiece = gramsPerPiece,
        aliasesJson = aliasesJson,
        createdAtEpochMs = createdAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
    )

internal fun PlanVersionEntity.toRow(): PlanVersionRow =
    PlanVersionRow(
        id = id,
        profileId = profileId,
        version = version,
        startDayEpochDay = startDayEpochDay,
        endDayEpochDay = endDayEpochDay,
        seed = seed,
        settingsJson = settingsJson,
        reportJson = reportJson,
        createdAtEpochMs = createdAtEpochMs,
        supersededAtEpochMs = supersededAtEpochMs,
    )

internal fun PlanSlotEntity.toRow(): PlanSlotRow =
    PlanSlotRow(
        id = id,
        planId = planId,
        profileId = profileId,
        dayEpochDay = dayEpochDay,
        mealSlot = mealSlot,
        recipeId = recipeId,
        recipeVersion = recipeVersion,
        recipeName = recipeName,
        servings = servings,
        state = state,
        replacedByEntryId = replacedByEntryId,
        successorSlotId = successorSlotId,
        replacesSlotId = replacesSlotId,
        parentSlotId = parentSlotId,
        isCookEvent = isCookEvent,
        batchServings = batchServings,
        kcalPerServing = kcalPerServing,
        proteinGPerServing = proteinGPerServing,
        carbGPerServing = carbGPerServing,
        fatGPerServing = fatGPerServing,
        fiberGPerServing = fiberGPerServing,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        itemJson = itemJson,
        sortOrder = sortOrder,
    )

internal fun ListItemEntity.toRow(): ListItemRow =
    ListItemRow(
        id = id,
        profileId = profileId,
        listId = listId,
        groceryItemId = groceryItemId,
        name = name,
        qty = qty,
        unit = unit,
        aisle = aisle,
        state = state,
        checkedAtEpochMs = checkedAtEpochMs,
        deltaQty = deltaQty,
        sourcesJson = sourcesJson,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
    )

internal fun PantryItemEntity.toRow(): PantryItemRow =
    PantryItemRow(
        id = id,
        profileId = profileId,
        groceryItemId = groceryItemId,
        name = name,
        qty = qty,
        unit = unit,
        expiryEpochDay = expiryEpochDay,
        addedAtEpochMs = addedAtEpochMs,
        lastPurchasedAtEpochMs = lastPurchasedAtEpochMs,
        purchaseCount = purchaseCount,
        isStaple = isStaple,
        outOfStock = outOfStock,
        updatedAtEpochMs = updatedAtEpochMs,
        archivedAtEpochMs = archivedAtEpochMs,
    )

internal fun AisleCorrectionEntity.toRow(): AisleCorrectionRow =
    AisleCorrectionRow(
        profileId = profileId,
        groceryItemId = groceryItemId,
        aisle = aisle,
        updatedAtEpochMs = updatedAtEpochMs,
    )

/**
 * Reads every known document key out of the documents DataStore. JsonDocumentStore
 * is key-addressed (no enumeration API by design), so the assembler walks the
 * registered keys; a new document owner registers its keys here (one list).
 */
public suspend fun JsonDocumentStore.snapshotDocuments(): Map<String, String> =
    buildMap {
        for (key in TEXT_DOCUMENT_KEYS) {
            readText(key)?.let { put(key, it) }
        }
        for (key in FLAG_DOCUMENT_KEYS) {
            put(key, readFlag(key).toString())
        }
    }

/** Known JsonDocumentStore keys (text docs). Owner noted per entry. */
public val TEXT_DOCUMENT_KEYS: List<String> =
    listOf(
        "correction-cache", // CorrectionCacheStore (R-B6)
        "onboarding/draft", // FinishOnboarding (F01 wizard draft)
    )

/** Known JsonDocumentStore keys (boolean marks). */
public val FLAG_DOCUMENT_KEYS: List<String> =
    listOf(
        "onboarding/complete", // FinishOnboarding (F01 shell gate)
    )
