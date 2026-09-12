package app.wlo.feature.f02.food.state

import app.wlo.core.model.DerivedValue
import app.wlo.core.model.DiaryRevision
import app.wlo.core.model.MealSlot
import app.wlo.core.model.Provenance

/*
 * The diary day's render state (R-B1: F02 owns the diary; R-B8: event-level
 * rows, day-level rendering). Entry kcal reach the UI as [DerivedValue] —
 * the chip renders them (D6) and taps open the "how we got here" sheet.
 */

/** One diary entry row. */
public data class EntryRowUi(
    public val id: String,
    public val title: String,
    /** The quantity line ("150 g", "1 serving", "500 ml water"). */
    public val subtitle: String,
    /** The kcal the diary math computed — D6-typed for the chip. */
    public val kcal: DerivedValue<Double>,
    public val isDrink: Boolean,
    public val revision: Int,
    public val edited: Boolean,
)

/** One meal-slot section (empty slots are absent — content-rendered, R-D14). */
public data class SlotUi(
    public val slot: MealSlot,
    public val entries: List<EntryRowUi>,
    public val kcal: Double,
)

/**
 * The F02 §3 day-status marker: Logged / Skipped / Fasted — one tap in the day
 * header, never a gap-shame. v1 keeps the choice for the session; persisting
 * the marker for F07's solve lands with the engine's check-in UI (FLAGGED in
 * the M3 report).
 */
public enum class DayStatusUi(
    public val label: String,
) {
    LOGGED("logged"),
    SKIPPED("skipped"),
    FASTED("fasted"),
}

/** One frozen revision of the entry (the correction audit, R-B8). */
public data class RevisionUi(
    public val revision: DiaryRevision,
    public val kcal: DerivedValue<Double>,
)

/** The open provenance sheet: the entry, its math, and its history. */
public data class EntryDetailUi(
    public val entry: EntryRowUi,
    public val rows: List<Pair<String, String>>,
    public val revisions: List<RevisionUi>,
    /** True while the inline correct-amount editor is open. */
    public val editing: Boolean = false,
    public val editText: String = "",
)

/** The diary day's full state. */
public data class DiaryUiState(
    public val dayEpochDay: Long,
    public val dayLabel: String,
    public val slots: List<SlotUi>,
    public val totals: DerivedValue<Double>,
    public val macroLine: String,
    public val dayStatus: DayStatusUi?,
    public val openEntry: EntryDetailUi? = null,
    public val notice: NoticeUi? = null,
) {
    public companion object {
        public fun loading(dayEpochDay: Long): DiaryUiState =
            DiaryUiState(
                dayEpochDay = dayEpochDay,
                dayLabel = "",
                slots = emptyList(),
                totals = DerivedValue(0.0, Provenance.Derived("loading", emptyList())),
                macroLine = "",
                dayStatus = null,
            )
    }
}
