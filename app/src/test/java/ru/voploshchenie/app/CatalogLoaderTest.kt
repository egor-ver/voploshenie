package ru.voploshchenie.app

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.voploshchenie.app.data.local.asset.CatalogLoader
import ru.voploshchenie.app.domain.model.CatalogDate
import ru.voploshchenie.app.domain.model.CatalogItem
import ru.voploshchenie.app.ui.components.formatCatalogDate

class Q02RefactorTest {
    @Test fun catalogDtoPreservesEveryFieldAndDatePrecision() {
        val source = """[{
            "id":"course", "type":"course", "title":"Title", "organizer":"Organizer",
            "direction":"Direction", "spheres":["it","finance"], "format":"remote", "city":"City",
            "audience_text":"Audience", "dates":[{"kind":"start","date":"2026-10","precision":"month"}],
            "date_text":"Original date", "is_rolling":true, "url":"https://example.org", "url_kind":"official",
            "unknown_field":"ignored"
        }]"""
        val expected = CatalogItem("course", "course", "Title", "Organizer", "Direction", listOf("it", "finance"),
            "remote", "City", "Audience", listOf(CatalogDate("start", "2026-10", "month")),
            "Original date", true, "https://example.org", "official")
        assertEquals(listOf(expected), CatalogLoader.parse(source))
    }

    @Test fun catalogDefaultsKeepUndatedItemsAndNullableFields() {
        val source = """[{
            "id":"course", "type":"course", "title":"Title", "direction":"Direction",
            "spheres":[], "audience_text":"Audience", "url":"https://example.org", "url_kind":"official"
        }]"""
        assertEquals(listOf(CatalogItem("course", "course", "Title", direction = "Direction", spheres = emptyList(),
            audience_text = "Audience", url = "https://example.org", url_kind = "official")), CatalogLoader.parse(source))
        assertEquals(emptyList<CatalogItem>(), CatalogLoader.parse("[]"))
    }

    @Test(expected = SerializationException::class)
    fun catalogMissingRequiredFieldsRemainsAnError() {
        CatalogLoader.parse("""[{"id":"incomplete"}]""")
    }

    @Test fun dateFormattingPreservesPrecisionAndCallerFallback() {
        val dates = listOf(
            CatalogDate("start", "2026-10-25", "day") to "25 октября 2026",
            CatalogDate("start", "2026-10", "month") to "октябрь 2026",
            CatalogDate("start", "2026", "year") to "2026",
        )
        dates.forEach { (date, expected) -> assertEquals(expected, formatCatalogDate(date)) }
        for (date in listOf(CatalogDate("start", "invalid", "day"), CatalogDate("start", "2026-13", "month"),
            CatalogDate("start", "raw", "unknown"))) {
            assertEquals(date.date, formatCatalogDate(date))
            assertEquals("Original text", formatCatalogDate(date, "Original text"))
        }
    }
}
