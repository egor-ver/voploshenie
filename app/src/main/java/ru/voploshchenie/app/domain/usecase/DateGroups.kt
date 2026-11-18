package ru.voploshchenie.app.domain.usecase

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import ru.voploshchenie.app.domain.model.CatalogItem

/** Группа списка «по дедлайну»: «Идёт сейчас», «Ближайший месяц», месяц, год, «Круглогодично», «Дата уточняется». */
sealed interface DateGroup {
    data object Ongoing : DateGroup
    data object NextMonth : DateGroup
    data class Month(val month: YearMonth) : DateGroup
    data class Year(val year: Int) : DateGroup
    data object Rolling : DateGroup
    data object Unknown : DateGroup
}

/** Ближайшая актуальная дата возможности (начало периода, но не раньше сегодняшнего дня). */
internal fun nearestDate(item: CatalogItem, today: LocalDate): LocalDate? =
    item.dates.mapNotNull { date -> date.datePeriod()?.let { (start, end) -> if (end < today) null else maxOf(start, today) } }.minOrNull()

internal fun dateGroup(item: CatalogItem, today: LocalDate): DateGroup {
    val nearest = nearestDate(item, today) ?: return if (item.is_rolling) DateGroup.Rolling else DateGroup.Unknown
    if (nearest <= today) return DateGroup.Ongoing
    if (ChronoUnit.DAYS.between(today, nearest) <= 30) return DateGroup.NextMonth
    // Позиция, у которой известен только год, показывается как «2027 год», а не «Январь 2027».
    val yearOnly = item.dates.any { it.precision == "year" && it.datePeriod()?.first == nearest }
    return if (yearOnly) DateGroup.Year(nearest.year) else DateGroup.Month(YearMonth.from(nearest))
}

/** Элементы, собранные в группы в порядке появления; внутри группы порядок сохраняется. */
internal fun groupByDate(items: List<CatalogItem>, today: LocalDate): List<Pair<DateGroup, List<CatalogItem>>> {
    val groups = LinkedHashMap<DateGroup, MutableList<CatalogItem>>()
    items.forEach { groups.getOrPut(dateGroup(it, today)) { mutableListOf() }.add(it) }
    return groups.map { (group, list) -> group to list }
}
