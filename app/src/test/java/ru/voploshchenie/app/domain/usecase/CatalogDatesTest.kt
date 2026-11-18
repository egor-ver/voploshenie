package ru.voploshchenie.app.domain.usecase

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import ru.voploshchenie.app.domain.model.CatalogDate
import ru.voploshchenie.app.domain.model.CatalogItem

class CatalogDatesTest {
    private val today = LocalDate.of(2026, 10, 10)
    private fun item(id: String, date: CatalogDate? = null) = CatalogItem(id, "course", id,
        direction = "", spheres = emptyList(), audience_text = "", dates = listOfNotNull(date), url = "", url_kind = "program")

    @Test fun exactDayMonthAndYearCloseOnlyAfterTheirLastDay() {
        for ((precision, value, end) in listOf(
            Triple("day", "2026-10-10", today),
            Triple("month", "2026-10", LocalDate.of(2026, 10, 31)),
            Triple("year", "2026", LocalDate.of(2026, 12, 31)),
        )) {
            val item = item(precision, CatalogDate("start", value, precision))
            assertFalse(opportunityClosed(item, end))
            assertTrue(opportunityClosed(item, end.plusDays(1)))
        }
    }

    @Test fun malformedAndUnknownDatesStayUndatedAndDoNotCloseAnOpportunity() {
        val future = item("upcoming", CatalogDate("start", "2026-10-11", "day"))
        for (date in listOf(CatalogDate("start", "2026-02-30", "day"),
            CatalogDate("start", "2026-13", "month"), CatalogDate("start", "bad", "year"),
            CatalogDate("start", "2026", "unknown"))) {
            val invalid = item("undated", date)
            assertNull(date.datePeriod())
            assertFalse(opportunityClosed(invalid, today))
            assertEquals(listOf(future, invalid), CatalogSelection(listOf(invalid, future)).byNearestDate(today))
        }
        assertFalse(opportunityClosed(item("empty"), today))
    }

    @Test fun activePeriodUsesTodayAndAnyUpcomingDateKeepsOpportunityOpen() {
        val month = item("month", CatalogDate("start", "2026-10", "month"))
        val year = item("year", CatalogDate("start", "2026", "year"))
        val future = item("future", CatalogDate("start", "2026-10-11", "day"))
        assertEquals(listOf(month, year, future), CatalogSelection(listOf(future, year, month)).byNearestDate(today))
        val mixed = month.copy(dates = month.dates + CatalogDate("end", "2025-01-01", "day"))
        assertFalse(opportunityClosed(mixed, today))
    }
}
