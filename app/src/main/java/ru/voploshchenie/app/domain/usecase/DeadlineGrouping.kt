package ru.voploshchenie.app.domain.usecase

import ru.voploshchenie.app.domain.model.CatalogDate
import ru.voploshchenie.app.domain.model.CatalogItem
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal data class Deadline(val item: CatalogItem, val date: CatalogDate?, val month: YearMonth?, val year: Int? = null)
internal data class DeadlineGroup(val month: YearMonth?, val entries: List<Deadline>, val year: Int? = null)

internal fun groupDeadlines(items: List<CatalogItem>, today: LocalDate): List<DeadlineGroup> {
    val deadlines = items.mapNotNull { item ->
        val valid = item.dates.filter(::isValid)
        val selected = listOf("apply_deadline", "start", "end").firstNotNullOfOrNull { kind ->
            valid.filter { it.kind == kind && !isExpired(it, today) }.minByOrNull { sortDate(it) }
        }
        when {
            selected != null -> makeDeadline(item, selected)
            valid.isEmpty() || valid.any { !isExpired(it, today) } -> Deadline(item, null, null)
            else -> null
        }
    }
    val months = deadlines.filter { it.month != null }.groupBy { it.month }
        .map { (month, entries) -> DeadlineGroup(month, entries) }
    val years = deadlines.filter { it.year != null }.groupBy { it.year }
        .map { (year, entries) -> DeadlineGroup(null, entries, year) }
    val dated = (months + years).sortedWith(compareBy<DeadlineGroup> { it.month?.year ?: it.year }.thenBy { it.month?.monthValue ?: 13 })
    val undated = deadlines.filter { it.month == null && it.year == null }
    return dated + listOfNotNull(undated.takeIf { it.isNotEmpty() }?.let { DeadlineGroup(null, it) })
}

private fun isValid(date: CatalogDate): Boolean = when (date.precision) {
    "day" -> runCatching { LocalDate.parse(date.date) }.isSuccess
    "month" -> runCatching { YearMonth.parse(date.date) }.isSuccess
    "year" -> date.date.toIntOrNull()?.let { it in 1..9999 } == true
    else -> false
}

private fun isExpired(date: CatalogDate, today: LocalDate): Boolean = when (date.precision) {
    "day" -> LocalDate.parse(date.date).isBefore(today)
    "month" -> YearMonth.parse(date.date).isBefore(YearMonth.from(today))
    "year" -> date.date.toInt() < today.year
    else -> false
}

private fun sortDate(date: CatalogDate): Long = when (date.precision) {
    "day" -> LocalDate.parse(date.date).toEpochDay()
    "month" -> YearMonth.parse(date.date).atDay(1).toEpochDay()
    "year" -> LocalDate.of(date.date.toInt(), 1, 1).toEpochDay()
    else -> Long.MAX_VALUE
}

private fun makeDeadline(item: CatalogItem, date: CatalogDate): Deadline = when (date.precision) {
    "day" -> Deadline(item, date, YearMonth.from(LocalDate.parse(date.date)))
    "month" -> Deadline(item, date, YearMonth.parse(date.date))
    "year" -> Deadline(item, date, null, date.date.toInt())
    else -> Deadline(item, null, null)
}
