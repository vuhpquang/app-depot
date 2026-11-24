package com.vuhpquang.appdepot.ui

import com.vuhpquang.appdepot.android.HistoryRow
import com.vuhpquang.appdepot.core.AppState
import java.time.LocalDate
import java.time.ZoneId

/**
 * Home's "Update Stage": this phone's apps with an update sit on the hero card ([stage]); everything
 * else stays in [sections]. No stage while searching, so a search result is never hidden on it.
 */
data class StagedSections(val stage: List<ProductRow>, val sections: HomeSections)

fun stageSplit(sections: HomeSections, showStage: Boolean): StagedSections {
    if (!showStage) return StagedSections(emptyList(), sections)
    val (stage, rest) = sections.local.partition { it.row?.state is AppState.UpdateAvailable }
    return StagedSections(stage, sections.copy(local = rest))
}

/** Where a card sits in a connected group: decides which corners are large (outer) and small (inner). */
enum class GroupPosition { Single, First, Middle, Last }

fun groupPosition(index: Int, size: Int): GroupPosition = when {
    size <= 1 -> GroupPosition.Single
    index == 0 -> GroupPosition.First
    index == size - 1 -> GroupPosition.Last
    else -> GroupPosition.Middle
}

/** One day of history, newest first (rows keep their order). */
data class HistoryDay(val day: LocalDate, val rows: List<HistoryRow>)

fun historyDays(rows: List<HistoryRow>, zone: ZoneId): List<HistoryDay> {
    val days = LinkedHashMap<LocalDate, MutableList<HistoryRow>>()
    rows.forEach { r -> days.getOrPut(r.at.atZone(zone).toLocalDate()) { mutableListOf() } += r }
    return days.map { (d, r) -> HistoryDay(d, r) }
}

enum class DayLabel { Today, Yesterday, Date }

fun dayLabel(day: LocalDate, today: LocalDate): DayLabel = when (day) {
    today -> DayLabel.Today
    today.minusDays(1) -> DayLabel.Yesterday
    else -> DayLabel.Date
}
