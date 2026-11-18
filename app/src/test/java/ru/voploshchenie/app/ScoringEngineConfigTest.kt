package ru.voploshchenie.app

import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.scoring.ExperienceAnswer
import ru.voploshchenie.app.domain.scoring.NeedsAnswers
import ru.voploshchenie.app.domain.scoring.ScoringResult
import ru.voploshchenie.app.domain.scoring.ScoringEngine
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScoringEngineConfigTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val config by lazy { ConfigLoader.load(context) }
    private val reference = TestAnswers(
        pairs = config.questions.getValue("interests").mapIndexed { index, q ->
            q.id to requireNotNull(if (index + 1 in listOf(1, 6, 7, 8, 9, 10)) q.type_a else q.type_b)
        }.toMap(),
        tasks = mapOf(
            "TSK01" to "в", "TSK02" to "в", "TSK03" to "б", "TSK04" to "а",
            "TSK05" to "в", "TSK06" to "б", "TSK07" to "а", "TSK08" to "а",
            "TSK09" to "в", "TSK10" to "а",
        ),
        interest = (1..14).associate { "SPH%02d".format(it) to listOf(3, 5, 2, 3, 2, 4, 3)[(it - 1) % 7] },
        valuesTop3 = listOf("VAL10", "VAL07", "VAL01"),
        valuesLeast2 = listOf("VAL08", "VAL04"),
        needs = NeedsAnswers(top3 = listOf("NEED02", "NEED01", "NEED08")),
    )

    @Test fun reference13MatchesEveryTableCell() {
        val result = ScoringEngine.compute(reference, config)
        assertEquals("I", reference.pairs.getValue("INT09"))
        assertEquals(mapOf("R" to 1, "I" to 4, "A" to 2, "S" to 1, "E" to 3, "C" to 4), result.riasec)
        assertEquals(mapOf("logic" to 2, "numbers" to 1, "text" to 2, "space" to 0, "people" to 1), result.domains)
        val expected = listOf(
            listOf("it", 20, 18, 10, 6, 3, 57, 80, 1),
            listOf("finance", 12, 22, 9, 2, 1, 46, 65, 2),
            listOf("engineering", 16, 15, 4, 4, 1, 40, 56, 3),
            listOf("biology", 8, 15, 11, 4, 2, 40, 56, 4),
            listOf("telecom", 12, 15, 8, 0, 1, 36, 51, 5),
            listOf("business", 12, 14, 7, 2, 1, 36, 51, 6),
            listOf("public_service", 8, 19, 10, -2, 0, 35, 49, 7),
        )
        assertEquals(expected, result.spheres.mapIndexed { index, row ->
            listOf(row.id, row.scoreInterest, row.scoreRiasec, row.scoreAbility, row.scoreValues,
                row.scoreNeeds, row.total, row.percent, index + 1)
        })
        assertEquals(listOf("it", "finance", "engineering"), result.topSpheres)
        assertEquals(config.version, result.configVersion)
    }

    @Test fun minimumRatingsAndWrongTasksGiveNonnegativePercentages() {
        val answers = reference.copy(
            interest = reference.interest.mapValues { 1 },
            tasks = config.questions.getValue("tasks").associate { q -> q.id to q.options.first { it.id != q.correct }.id },
            valuesTop3 = listOf("VAL04", "VAL06", "VAL09"),
            valuesLeast2 = listOf("VAL10", "VAL07"),
            needs = NeedsAnswers(top3 = listOf("NEED03", "NEED05", "NEED06")),
        )
        val result = ScoringEngine.compute(answers, config)
        assertEquals(setOf(0), result.domains.values.toSet())
        assertTrue(result.spheres.all { it.scoreInterest == 4 && it.scoreAbility == 0 && it.percent >= 0 })
        val it = result.spheres.first { it.id == "it" }
        assertEquals(-4, it.scoreValues)
        assertEquals(0, it.scoreNeeds)
        assertEquals(18, it.total)
        assertEquals(25, it.percent)
    }

    @Test fun completeTiesKeepConfigOrder() {
        val sphere = config.spheres.first()
        val tied = config.copy(spheres = config.spheres.reversed().map { sphere.copy(id = it.id) })
        val result = ScoringEngine.compute(reference, tied)
        assertEquals(tied.spheres.map { it.id }, result.spheres.map { it.id })
        assertEquals(tied.spheres.take(3).map { it.id }, result.topSpheres)
        assertEquals(1, result.spheres.map { Triple(it.total, it.scoreInterest, it.scoreRiasec) }.toSet().size)
    }

    @Test fun percentagesRoundHalfUpAndClampNegativeValues() {
        assertEquals(0, ScoringEngine.roundPercent(-1.5))
        assertEquals(0, ScoringEngine.roundPercent(0.49))
        assertEquals(1, ScoringEngine.roundPercent(0.5))
        assertEquals(2, ScoringEngine.roundPercent(1.5))
        assertEquals(3, ScoringEngine.roundPercent(2.5))
        assertEquals(50, ScoringEngine.roundPercent(49.5))
        assertEquals(100, ScoringEngine.roundPercent(100.0))
    }

    @Test fun onlyScoredAnswersAffectScoresAndConfigVersionIsPreserved() {
        val other = reference.copy(
            profile = mapOf("PRF05" to JsonPrimitive(6)),
            experience = mapOf("EXP01" to ExperienceAnswer("", 1)),
            nad = mapOf("NAD07" to ""),
            open = mapOf("OPEN01" to ""),
            checks = mapOf("CHK01" to "S"),
            needs = reference.needs.copy(ratings = mapOf("NEED01" to 1)),
        )
        val result = ScoringEngine.compute(reference, config)
        val otherResult = ScoringEngine.compute(other, config)
        assertEquals(result.riasec, otherResult.riasec)
        assertEquals(result.domains, otherResult.domains)
        assertEquals(result.spheres, otherResult.spheres)
        assertEquals(result.copy(configVersion = config.version + 1), ScoringEngine.compute(reference, config.copy(version = config.version + 1)))
        assertEquals(other, Json.decodeFromString<TestAnswers>(Json.encodeToString(other)))
        assertEquals(result, Json.decodeFromString<ScoringResult>(Json.encodeToString(result)))
    }
}

