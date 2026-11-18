package ru.voploshchenie.app

import ru.voploshchenie.app.data.local.asset.ConfigLoader
import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.scoring.TestAnswers
import ru.voploshchenie.app.domain.scoring.NeedsAnswers
import ru.voploshchenie.app.domain.scoring.ScoringEngine
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScoringEngineTest {
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
        checks = config.questions.getValue("consistency").mapIndexed { index, q ->
            q.id to requireNotNull(if (index < 2) q.type_a else q.type_b)
        }.toMap(),
    )

    @Test fun referenceCodeEquivalenceLevelsAndPresentation() {
        val result = ScoringEngine.compute(reference, config)
        assertEquals("ICE", result.riasecCode)
        assertEquals(listOf(listOf("engineering", "biology"), listOf("telecom", "business"),
            listOf("telecom", "public_service"), listOf("business", "public_service")), result.equivalentPairs)
        assertEquals(0, result.mismatches)
        assertNull(result.consistencyWarning)
        assertEquals("Исследователь идей", result.label)
        assertEquals("Профессиональная компетентность", result.anchor)
        assertEquals("Выраженный", result.riasecLevels["I"])
        assertEquals("Умеренный", result.riasecLevels["E"])
        assertEquals("Слабый", result.riasecLevels["R"])
        assertEquals("Сильная зона", result.domainLevels["logic"])
        assertEquals("Рабочий уровень", result.domainLevels["numbers"])
        assertEquals("Зона роста", result.domainLevels["space"])
        assertEquals("Сильное совпадение", result.spheres[0].level)
        assertEquals("Заметное совпадение", result.spheres[2].level)
        assertEquals(listOf("разработка", "аналитика данных", "тестирование"), result.spheres[0].professions.map { it.name })
        result.spheres.forEach { sphere ->
            assertEquals(3, sphere.professions.size)
            assertTrue(sphere.professions.all { it.percent == sphere.percent })
        }
        assertEquals(listOf("skill_gap"), result.spheres[2].flags.map { it.id })
        assertTrue(result.spheres.drop(3).all { it.flags.isEmpty() })
    }

    @Test fun directPairResolvesTwoWayTieAndCycleUsesCanonicalOrder() {
        val questions = config.questions.getValue("interests")
        val scores = listOf("R", "I", "A", "S", "E", "C").associateWith { if (it in listOf("R", "I")) 4 else 0 }
        assertTrue(ScoringEngine.interestCode(scores, reference.pairs + ("INT01" to "R"), questions).startsWith("RI"))
        assertTrue(ScoringEngine.interestCode(scores, reference.pairs + ("INT01" to "I"), questions).startsWith("IR"))
        val cycle = reference.pairs + mapOf("INT01" to "R", "INT02" to "A", "INT06" to "I")
        assertEquals("RIA", ScoringEngine.interestCode(scores + ("A" to 4), cycle, questions))
        assertEquals("IAR", ScoringEngine.interestCode(scores + ("A" to 4), cycle + ("INT01" to "I"), questions))
    }

    @Test fun consistencyComparesTypesAndWarnsAtConfiguredBoundary() {
        val one = reference.copy(checks = reference.checks + ("CHK01" to "S"))
        val two = one.copy(checks = one.checks + ("CHK02" to "S"))
        assertEquals(1, ScoringEngine.compute(one, config).mismatches)
        assertNull(ScoringEngine.compute(one, config).consistencyWarning)
        val result = ScoringEngine.compute(two, config)
        assertEquals(2, result.mismatches)
        assertEquals(config.analyst.getValue("consistency_warning").toString().trim('"'), result.consistencyWarning)
        assertNull(ScoringEngine.compute(two, threshold("consistency_mismatches", 3)).consistencyWarning)
        val swapped = config.copy(questions = config.questions + ("consistency" to config.questions.getValue("consistency").map {
            it.copy(type_a = it.type_b, type_b = it.type_a)
        }))
        assertEquals(0, ScoringEngine.compute(reference, swapped).mismatches)
    }

    @Test fun flagsRespectEachBoundaryAndThresholdChanges() {
        fun flags(id: String, answers: TestAnswers = reference, rules: TestConfig = config): List<String> =
            ScoringEngine.compute(answers, rules.copy(spheres = rules.spheres.filter { it.id == id })).spheres.single().flags.map { it.id }
        assertTrue("skill_gap" in flags("engineering"))
        assertFalse("skill_gap" in flags("engineering", reference.copy(interest = reference.interest + ("SPH06" to 3))))
        val space = config.questions.getValue("tasks").first { it.domain == "space" }
        assertFalse("skill_gap" in flags("engineering", reference.copy(tasks = reference.tasks + (space.id to requireNotNull(space.correct)))))
        assertFalse("skill_gap" in flags("engineering", rules = threshold("score_interest_min", 17, "skill_gap")))
        val finance = reference.copy(interest = reference.interest + mapOf("SPH04" to 2, "SPH11" to 2))
        assertTrue("interest_gap" in flags("finance", finance))
        assertFalse("interest_gap" in flags("finance", finance.copy(interest = finance.interest + ("SPH04" to 3))))
        val numbers = config.questions.getValue("tasks").first { it.domain == "numbers" && finance.tasks[it.id] == it.correct }
        assertFalse("interest_gap" in flags("finance", finance.copy(tasks = finance.tasks + (numbers.id to "wrong"))))
        assertFalse("interest_gap" in flags("finance", finance, threshold("score_ability_min", 10, "interest_gap")))
        assertTrue("conditions_gap" in flags("public_service"))
        assertFalse("conditions_gap" in flags("public_service", reference.copy(needs = NeedsAnswers(top3 = listOf("NEED07")))))
        assertFalse("conditions_gap" in flags("public_service", rules = threshold("conditions_gap_score_needs", 1)))
        val flagged = ScoringEngine.compute(reference, config).spheres[2].flags.single()
        assertEquals("Вам интересно, но пока не хватает навыков: это зона роста. Пробуйте с поддержкой: курс, наставник, практика.", flagged.text)
    }

    @Test fun eachNadHintRequiresItsAnswerAndSphere() {
        data class Case(val id: String, val question: String, val yes: String, val no: String, val sphere: String)
        val cases = listOf(Case("H01", "NAD03", "a", "b", "biology"), Case("H02", "NAD04", "a", "c", "it"),
            Case("H03", "NAD05", "c", "b", "business"), Case("H04", "NAD02", "a", "b", "it"),
            Case("H05", "NAD01", "d", "c", "public_service"))
        for (case in cases) {
            val rules = config.copy(spheres = config.spheres.filter { it.id == case.sphere })
            val yes = reference.copy(nad = mapOf(case.question to case.yes))
            val hint = ScoringEngine.compute(yes, rules).hints.single()
            assertEquals(case.id, hint.id)
            assertEquals(config.hints.first { it.jsonObject.getValue("ID") == JsonPrimitive(case.id) }.jsonObject.getValue("Текст подсказки"), JsonPrimitive(hint.text))
            assertTrue(ScoringEngine.compute(yes.copy(nad = mapOf(case.question to case.no)), rules).hints.isEmpty())
            if (case.id in listOf("H01", "H03", "H05")) {
                assertTrue(ScoringEngine.compute(yes, config.copy(spheres = config.spheres.filter { it.id == "it" })).hints.isEmpty())
            }
        }
        assertEquals("H02", ScoringEngine.compute(reference.copy(nad = mapOf("NAD04" to "b")), config).hints.single().id)
        assertEquals("H05", ScoringEngine.compute(reference.copy(nad = mapOf("NAD01" to "d")), config.copy(spheres = config.spheres.filter { it.id == "biology" })).hints.single().id)
    }

    @Test fun levelsEquivalenceAndAnchorUseConfigAndRankOne() {
        val rules = config.analyst.getValue("levels").jsonObject
        for ((key, cases) in mapOf("riasec" to listOf(0, 1, 2, 3, 4, 5), "domain" to listOf(0, 1, 2), "match" to listOf(0, 44, 45, 59, 60, 100))) {
            val expected = when (key) {
                "riasec" -> listOf("Слабый", "Слабый", "Умеренный", "Умеренный", "Выраженный", "Выраженный")
                "domain" -> listOf("Зона роста", "Рабочий уровень", "Сильная зона")
                else -> listOf("Слабое совпадение", "Слабое совпадение", "Заметное совпадение", "Заметное совпадение", "Сильное совпадение", "Сильное совпадение")
            }
            assertEquals(expected, cases.map { ScoringEngine.level(it, rules.getValue(key)) })
        }
        val changed = config.copy(analyst = config.analyst + ("levels" to JsonObject(rules + ("match" to Json.parseToJsonElement("""[{"min":81,"label":"Сильное совпадение"},{"min":45,"label":"Заметное совпадение"},{"min":0,"label":"Слабое совпадение"}]""")))))
        assertEquals("Заметное совпадение", ScoringEngine.compute(reference, changed).spheres.first().level)
        assertEquals(listOf(listOf("engineering", "biology"), listOf("telecom", "business")), ScoringEngine.compute(reference, threshold("equivalent_percent_difference", 2)).equivalentPairs)
        assertTrue(ScoringEngine.compute(reference, threshold("equivalent_percent_difference", 0)).equivalentPairs.isEmpty())
        assertEquals("Автономия", ScoringEngine.compute(reference.copy(valuesTop3 = listOf("VAL01", "VAL10", "VAL07")), config).anchor)
        assertEquals("Доход для вас главный ориентир. Уточните, ради чего он нужен: свободы, безопасности или статуса, — и это подскажет, какое направление вам подойдёт", ScoringEngine.compute(reference.copy(valuesTop3 = listOf("VAL03", "VAL10", "VAL07")), config).anchor)
    }

    private fun threshold(key: String, value: Int, group: String? = null): TestConfig {
        val thresholds = config.analyst.getValue("thresholds").jsonObject
        val changed = if (group == null) thresholds + (key to JsonPrimitive(value))
        else thresholds + (group to JsonObject(thresholds.getValue(group).jsonObject + (key to JsonPrimitive(value))))
        return config.copy(analyst = config.analyst + ("thresholds" to JsonObject(changed)))
    }
}

