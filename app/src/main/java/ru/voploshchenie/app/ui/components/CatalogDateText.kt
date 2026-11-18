package ru.voploshchenie.app.ui.components

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import ru.voploshchenie.app.domain.model.CatalogDate

internal fun formatCatalogDate(date: CatalogDate, fallback: String = date.date): String = try {
    val russian = Locale.forLanguageTag("ru")
    when (date.precision) {
        "day" -> LocalDate.parse(date.date).format(DateTimeFormatter.ofPattern("d MMMM yyyy", russian))
        "month" -> YearMonth.parse(date.date).format(DateTimeFormatter.ofPattern("LLLL yyyy", russian))
        "year" -> date.date
        else -> fallback
    }
} catch (_: DateTimeParseException) {
    fallback
}
