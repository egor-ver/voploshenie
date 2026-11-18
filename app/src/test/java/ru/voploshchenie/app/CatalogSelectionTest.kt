package ru.voploshchenie.app

import ru.voploshchenie.app.data.local.asset.CatalogLoader
import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.domain.model.CatalogItem
import ru.voploshchenie.app.domain.model.CatalogDate
import ru.voploshchenie.app.domain.model.CatalogSphereResult
import ru.voploshchenie.app.domain.model.CatalogResult
import ru.voploshchenie.app.domain.model.CatalogRecommendation
import ru.voploshchenie.app.domain.usecase.CatalogSelection
import ru.voploshchenie.app.domain.usecase.matchPercent
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.scoring.ScoringEngine
import ru.voploshchenie.app.domain.usecase.opportunityClosed
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
class C02CatalogTest {
    private val items = listOf(
        item("a", "course", "Аналитика", "Школа", listOf("it"), listOf(CatalogDate("apply_deadline", "2026-05", "month"))),
        item("b", "course", "Разработка", "Университет", listOf("it"), emptyList()),
        item("c", "event", "Форум", "Школа", listOf("business"), listOf(CatalogDate("start", "2026-04-20", "day"))),
        item("d", "course", "Данные", "Академия", listOf("business"), listOf(CatalogDate("start", "2026", "year"))),
        item("e", "course", "Стажировка", "Лаборатория", listOf("engineering"), listOf(CatalogDate("start", "2026-04", "month"))),
    )
    private val result = CatalogResult(
        spheres = listOf(CatalogSphereResult("it", 72), CatalogSphereResult("business", 85), CatalogSphereResult("engineering", 40)),
        topSpheres = listOf("it", "business", "engineering"),
    )

    @Test fun matchUsesHighestMatchingSphereAndZeroForNoMatch() {
        assertEquals(85, matchPercent(items[3], result))
        assertEquals(0, matchPercent(items[2].copy(spheres = listOf("biology")), result))
    }

    @Test fun filtersByTypeAndCaseInsensitiveTitleOrOrganizer() {
        val repository = CatalogSelection(items)
        assertEquals(listOf("a", "b", "d", "e"), repository.filter(type = "course").map { it.id })
        assertEquals(listOf("b"), repository.filter(query = "университет").map { it.id })
        assertEquals(listOf("a", "c"), repository.filter(query = "школа").map { it.id })
        assertEquals(items.map { it.id }, repository.filter(query = "  ").map { it.id })
    }

    @Test fun nearestDateIgnoresPastDatesAndPutsMissingDatesLast() {
        val order = CatalogSelection(items).byNearestDate(LocalDate.of(2026, 4, 1)).map { it.id }
        assertEquals(listOf("d", "e", "c", "a", "b"), order)
    }

    @Test fun currentMonthAndYearDatesRemainUpcoming() {
        val today = LocalDate.of(2026, 10, 10)
        val month = items[0].copy(dates = listOf(CatalogDate("start", "2026-10", "month")))
        val year = items[1].copy(dates = listOf(CatalogDate("start", "2026", "year")))
        val futureMonth = items[2].copy(dates = listOf(CatalogDate("start", "2026-11", "month")))
        val result = CatalogSelection(listOf(futureMonth, year, month)).byNearestDate(today)
        assertEquals(listOf(month.id, year.id, futureMonth.id), result.map { it.id })
    }

    @Test fun pastExactDaySortsAfterUpcomingDate() {
        val today = LocalDate.of(2026, 10, 10)
        val pastDay = items[0].copy(dates = listOf(CatalogDate("start", "2026-04-20", "day")))
        val futureDay = items[1].copy(dates = listOf(CatalogDate("start", "2026-10-11", "day")))
        val result = CatalogSelection(listOf(pastDay, futureDay)).byNearestDate(today)
        assertEquals(listOf(futureDay.id, pastDay.id), result.map { it.id })
    }

    @Test fun interestOrderIsDescendingAndStableForTies() {
        assertEquals(listOf("c", "d", "a", "b", "e"), CatalogSelection(items).byInterests(result).map { it.id })
    }

    @Test fun courseAudienceRangesArePrioritizedWithoutDiscardingUnparsedItems() {
        val range = items[0].copy(audience_text = "1–4 курс, магистратура")
        val senior = items[1].copy(audience_text = "старшие курсы/выпускники")
        val unparsed = items[2].copy(audience_text = "студенты")
        assertEquals(listOf("a", "c", "b"), CatalogSelection(listOf(unparsed, senior, range)).byCourse("3 курс бакалавриата/специалитета").map { it.id })
        assertEquals(listOf("c", "b", "a"), CatalogSelection(listOf(unparsed, senior, range)).byCourse("курс вне формата").map { it.id })
    }

    @Test fun courseMatchingKeepsStagesAndEnumerationsSeparate() {
        val items = listOf(
            item("enum", "course", "Перечисление", null, listOf("it")).copy(audience_text = "1, 3, 5 курс"),
            item("specialty", "course", "Специалитет", null, listOf("it")).copy(audience_text = "4–5 специалитета"),
            item("master", "course", "Магистратура", null, listOf("it")).copy(audience_text = "магистратура"),
            item("graduate", "course", "Аспирантура", null, listOf("it")).copy(audience_text = "аспирантура"),
            item("unknown", "course", "Без данных", null, listOf("it")).copy(audience_text = "студенты"),
        )
        assertEquals(listOf("enum", "specialty", "master", "graduate", "unknown"),
            CatalogSelection(items).byCourse("3 курс бакалавриата/специалитета").map { it.id })
        assertEquals(listOf("master", "enum", "specialty", "graduate", "unknown"),
            CatalogSelection(items).byCourse("2 курс магистратуры").map { it.id })
        assertEquals(listOf("graduate", "enum", "specialty", "master", "unknown"),
            CatalogSelection(items).byCourse("аспирантура").map { it.id })
    }

    @Test fun recommendationsFillFromSecondAndThirdSphereWithoutDuplicatesAndMarkExtras() {
        val result = result.copy(topSpheres = listOf("it", "business", "engineering"))
        val picks = CatalogSelection(items).recommendations(result)
        assertEquals(listOf("a", "b", "c", "d", "e"), picks.map { it.item.id })
        assertEquals(listOf(null, null, "business", "business", "engineering"), picks.map { it.fromSphere })
        assertEquals(emptyList<CatalogRecommendation>(), CatalogSelection(items).recommendations(result.copy(topSpheres = emptyList())))
    }

    @Test fun catalogRecommendationsMatchReferenceAndCalculatedBiologyAndTelecomProfiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = CatalogLoader.load(context)
        val repository = CatalogSelection(catalog)
        val reference = CatalogResult(emptyList(), listOf("it", "finance", "engineering"))
        assertEquals(112, repository.recommendations(reference).size)

        val config = ConfigLoader.load(context)
        fun calculatedProfile(primary: String, top: List<String>): CatalogResult {
            val interests = config.questions.getValue("interests")
            val consistency = config.questions.getValue("consistency")
            val tasks = config.questions.getValue("tasks")
            val scores = (0..5000).asSequence().map { seed ->
                val random = Random(seed)
                val pairs = interests.associate { q -> q.id to listOfNotNull(q.type_a, q.type_b).random(random) }
                val answers = TestAnswers(
                    pairs = pairs,
                    checks = consistency.associate { q ->
                        val linked = interests.first { it.id == q.linked }
                        val selected = pairs.getValue(linked.id)
                        q.id to if (selected == q.type_a) requireNotNull(q.type_a) else requireNotNull(q.type_b)
                    },
                    tasks = tasks.associate { q -> q.id to q.options.random(random).id },
                    interest = config.spheres.flatMap { sphere -> sphere.questions.map { it to if (sphere.id == primary) 5 else 0 } }.toMap(),
                    valuesTop3 = listOf("VAL01", "VAL02", "VAL03", "VAL04", "VAL05", "VAL06", "VAL07", "VAL08", "VAL09", "VAL10").shuffled(random).take(3),
                )
                ScoringEngine.compute(answers, config)
            }.first { it.topSpheres.take(top.size) == top }
            return CatalogResult(scores.spheres.map { CatalogSphereResult(it.id, it.percent) }, scores.topSpheres)
        }

        val biology = repository.recommendations(calculatedProfile("biology", listOf("biology", "finance", "it")))
        assertEquals(112, biology.size)
        assertEquals(38, biology.count { it.isOther })
        assertEquals(73, biology.count { it.fromSphere != null })
        assertEquals(biology.size, biology.map { it.item.id }.toSet().size)
        assertTrue(biology.all { it.fromSphere == null || it.fromSphere in listOf("finance", "it") })

        val telecom = repository.recommendations(calculatedProfile("telecom", listOf("telecom")))
        assertEquals(112, telecom.size)
        assertTrue(telecom.take(3).all { "telecom" in it.item.spheres && it.fromSphere == null && !it.isOther })
        assertTrue(telecom.drop(3).all { "telecom" !in it.item.spheres })
    }

    @Test fun threePrimaryItemsStillIncludeSecondAndThirdSpheres() {
        val picks = CatalogSelection(items + item("f", "event", "Ит", null, listOf("it"))).recommendations(result)
        assertEquals(listOf("a", "b", "f", "c", "d", "e"), picks.map { it.item.id })
        assertEquals(listOf(null, null, null, "business", "business", "engineering"), picks.map { it.fromSphere })
    }

    @Test fun recommendationsAppendOtherItemsAndDeduplicateOverlappingSpheresAndIds() {
        val primary = items[0].copy(spheres = listOf("it", "business"))
        val secondary = items[2].copy(spheres = listOf("business", "engineering"))
        val other = items[1].copy(id = "other", spheres = listOf("biology"))
        val catalog = listOf(other, items[4], secondary, primary, primary)
        val picks = CatalogSelection(catalog).recommendations(result)
        assertEquals(listOf("a", "c", "e", "other"), picks.map { it.item.id })
        assertEquals(listOf(null, "business", "engineering", null), picks.map { it.fromSphere })
        assertEquals(listOf(false, false, false, true), picks.map { it.isOther })
        assertEquals(listOf("a", "other", "e", "c"),
            CatalogSelection(catalog).recommendations(result.copy(topSpheres = listOf("it"))).map { it.item.id })
    }

    @Test fun similarRequiresSameTypeAndAtLeastOneSharedSphere() {
        assertEquals(listOf("b"), CatalogSelection(items).similar(items[0]).map { it.id })
    }

    @Test fun registrationClosesOnlyAfterEveryKnownDeadlinePassed() {
        val item = items[0].copy(dates = listOf(CatalogDate("apply_deadline", "2026-10-10", "day")))
        assertFalse(opportunityClosed(item, LocalDate.parse("2026-10-10")))
        assertTrue(opportunityClosed(item, LocalDate.parse("2026-10-11")))
        assertFalse(opportunityClosed(item.copy(dates = emptyList()), LocalDate.parse("2026-10-11")))
        val allDates = item.copy(dates = listOf(
            CatalogDate("apply_deadline", "2027-06-30", "day"),
            CatalogDate("start", "2027-06-01", "day"),
            CatalogDate("end", "2027-06-30", "day"),
        ))
        assertTrue(opportunityClosed(allDates, LocalDate.parse("2027-07-01")))
        assertFalse(opportunityClosed(allDates, LocalDate.parse("2027-06-30")))
    }

    @Test fun realCatalogParsesAndEveryEntryHasSpheres() {
        val catalog = CatalogLoader.load(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(112, catalog.size)
        assertTrue(catalog.all { it.spheres.isNotEmpty() })
    }

    private fun item(id: String, type: String, title: String, organizer: String?, spheres: List<String>, dates: List<CatalogDate> = emptyList()) =
        CatalogItem(id, type, title, organizer, "направление", spheres, null, null, "аудитория", dates, "", false, "https://example.org", "program")
}
