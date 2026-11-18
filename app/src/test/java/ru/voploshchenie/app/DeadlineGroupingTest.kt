package ru.voploshchenie.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.voploshchenie.app.domain.model.CatalogDate
import ru.voploshchenie.app.domain.model.CatalogItem
import ru.voploshchenie.app.domain.usecase.groupDeadlines
import java.time.LocalDate
import java.time.YearMonth

class DeadlineGroupingTest {
    private val today = LocalDate.parse("2026-10-23")

    private fun item(id: String, vararg dates: CatalogDate, text: String = "") = CatalogItem(
        id, "course", id, direction = "", spheres = emptyList(), audience_text = "",
        dates = dates.toList(), date_text = text, url = "", url_kind = "",
    )

    @Test fun removesExpiredDayWithinCurrentMonthButKeepsTodayAndLaterDates() {
        val groups = groupDeadlines(listOf(
            item("expired", CatalogDate("apply_deadline", "2026-10-01", "day")),
            item("today", CatalogDate("apply_deadline", "2026-10-23", "day")),
            item("future", CatalogDate("apply_deadline", "2026-10-25", "day")),
        ), today)

        assertEquals(listOf("today", "future"), groups.single().entries.map { it.item.id })
        assertEquals(YearMonth.of(2026, 10), groups.single().month)
    }

    @Test fun skipsExpiredStartAndSelectsFutureEnd() {
        val result = groupDeadlines(listOf(item("multi",
            CatalogDate("start", "2026-10-01", "day"),
            CatalogDate("end", "2026-11-05", "day"),
        )), today)

        assertEquals(CatalogDate("end", "2026-11-05", "day"), result.single().entries.single().date)
        assertEquals(YearMonth.of(2026, 11), result.single().month)
    }

    @Test fun groupsDayAndMonthAcrossYearsWithoutInventingMonthForYearPrecision() {
        val groups = groupDeadlines(listOf(
            item("dec", CatalogDate("apply_deadline", "2026-12-31", "day")),
            item("jan", CatalogDate("start", "2027-01", "month")),
            item("year", CatalogDate("end", "2028", "year"), text = "2028"),
            item("none", text = "Дата уточняется"),
        ), today)

        assertEquals(listOf("2026-12", "2027-01", "2028", null),
            groups.map { it.month?.toString() ?: it.year?.toString() })
        assertEquals(listOf("year"), groups[2].entries.map { it.item.id })
        assertEquals(listOf("none"), groups.last().entries.map { it.item.id })
        assertNull(groups.last().month)
    }

    @Test fun keepsCurrentMonthAndYearUntilTheirKnownPeriodEnds() {
        val groups = groupDeadlines(listOf(
            item("month", CatalogDate("apply_deadline", "2026-10", "month")),
            item("year", CatalogDate("start", "2026", "year")),
            item("past-year", CatalogDate("start", "2025", "year")),
        ), today)

        assertEquals(2, groups.size)
        assertEquals(YearMonth.of(2026, 10), groups.first().month)
        assertEquals(2026, groups.last().year)
        assertEquals(listOf("year"), groups.last().entries.map { it.item.id })
    }

    @Test fun picksNearestUsableDateForPreferredKindAndKeepsUndatedRecords() {
        val groups = groupDeadlines(listOf(
            item("multi", CatalogDate("start", "2026-11-20", "day"),
                CatalogDate("start", "2026-11-05", "day"), CatalogDate("end", "2026-10-30", "day")),
            item("bad", CatalogDate("apply_deadline", "nope", "day")),
        ), today)

        assertEquals(CatalogDate("start", "2026-11-05", "day"), groups.first().entries.first().date)
        assertEquals(listOf("bad"), groups.last().entries.map { it.item.id })
    }
}
