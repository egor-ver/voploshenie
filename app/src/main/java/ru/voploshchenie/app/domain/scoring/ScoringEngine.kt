package ru.voploshchenie.app.domain.scoring

import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.model.ConfigQuestion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlin.math.floor

@Serializable
data class TestAnswers(
    val profile: Map<String, JsonElement> = emptyMap(),
    val pairs: Map<String, String> = emptyMap(),
    val valuesTop3: List<String> = emptyList(),
    val valuesLeast2: List<String> = emptyList(),
    val interest: Map<String, Int> = emptyMap(),
    val tasks: Map<String, String> = emptyMap(),
    val experience: Map<String, ExperienceAnswer> = emptyMap(),
    val nad: Map<String, String> = emptyMap(),
    val needs: NeedsAnswers = NeedsAnswers(),
    val open: Map<String, String> = emptyMap(),
    val checks: Map<String, String> = emptyMap(),
)

@Serializable
data class ExperienceAnswer(val text: String, val energy: Int)

@Serializable
data class NeedsAnswers(
    val ratings: Map<String, Int> = emptyMap(),
    val top3: List<String> = emptyList(),
)

@Serializable
data class ScoringResult(
    val configVersion: Int,
    val riasec: Map<String, Int>,
    val domains: Map<String, Int>,
    val spheres: List<SphereScore>,
    val topSpheres: List<String>,
    val riasecCode: String,
    val riasecLevels: Map<String, String>,
    val domainLevels: Map<String, String>,
    val equivalentPairs: List<List<String>>,
    val mismatches: Int,
    val consistencyWarning: String?,
    val hints: List<ResultHint>,
    val anchor: String?,
    val label: String,
)

@Serializable
data class SphereScore(
    val id: String,
    val scoreInterest: Int,
    val scoreRiasec: Int,
    val scoreAbility: Int,
    val scoreValues: Int,
    val scoreNeeds: Int,
    val total: Int,
    val percent: Int,
    val level: String = "",
    val flags: List<ResultHint> = emptyList(),
    val professions: List<ProfessionScore> = emptyList(),
)

@Serializable
data class ResultHint(val id: String, val text: String)

@Serializable
data class ProfessionScore(val name: String, val percent: Int)

object ScoringEngine {
    fun compute(answers: TestAnswers, config: TestConfig): ScoringResult {
        val riasec = listOf("R", "I", "A", "S", "E", "C").associateWith { 0 }.toMutableMap()
        config.questions.getValue("interests").forEach { question ->
            val type = requireNotNull(selectedType(question, answers.pairs.getValue(question.id)))
            riasec[type] = riasec.getValue(type) + 1
        }
        val domains = listOf("logic", "numbers", "text", "space", "people").associateWith { 0 }.toMutableMap()
        config.questions.getValue("tasks").forEach { question ->
            if (answers.tasks.getValue(question.id) == question.correct) {
                val domain = requireNotNull(question.domain)
                domains[domain] = domains.getValue(domain) + 1
            }
        }
        val spheres = config.spheres.map { sphere ->
            val interest = 2 * sphere.questions.sumOf { answers.interest.getValue(it) }
            val types = sphere.riasec.mapIndexed { index, type -> (3 - index) * riasec.getValue(type) }.sum()
            val ability = sphere.domains.mapIndexed { index, domain -> (3 - index) * domains.getValue(domain) }.sum()
            val values = 2 * sphere.values.count { it in answers.valuesTop3 } -
                2 * sphere.values.count { it in answers.valuesLeast2 }
            val needs = sphere.needs.count { it in answers.needs.top3 }
            val total = interest + types + ability + values + needs
            SphereScore(sphere.id, interest, types, ability, values, needs, total, roundPercent(total / 71.0 * 100))
        }.sortedWith(compareByDescending<SphereScore> { it.total }
            .thenByDescending { it.scoreInterest }
            .thenByDescending { it.scoreRiasec })
        val thresholds = config.analyst.getValue("thresholds").jsonObject
        val levels = config.analyst.getValue("levels").jsonObject
        val topSpheres = spheres.take(3).map { it.id }
        val enriched = spheres.map { sphere ->
            val flags = if (sphere.id in topSpheres) buildList {
                val skill = thresholds.getValue("skill_gap").jsonObject
                val interest = thresholds.getValue("interest_gap").jsonObject
                if (sphere.scoreInterest >= skill.getValue("score_interest_min").jsonPrimitive.int &&
                    sphere.scoreAbility <= skill.getValue("score_ability_max").jsonPrimitive.int) add("skill_gap")
                if (sphere.scoreAbility >= interest.getValue("score_ability_min").jsonPrimitive.int &&
                    sphere.scoreInterest <= interest.getValue("score_interest_max").jsonPrimitive.int) add("interest_gap")
                if (sphere.scoreNeeds == thresholds.getValue("conditions_gap_score_needs").jsonPrimitive.int) add("conditions_gap")
            } else emptyList()
            sphere.copy(
                level = level(sphere.percent, levels.getValue("match")),
                flags = flags.map { ResultHint(it, config.analyst.getValue("flag_texts").jsonObject.getValue(it).jsonPrimitive.content) },
                professions = config.analyst.getValue("professions").jsonObject.getValue(sphere.id).jsonArray.map {
                    ProfessionScore(it.jsonPrimitive.content, sphere.percent)
                },
            )
        }
        val interests = config.questions.getValue("interests").associateBy { it.id }
        val mismatches = config.questions.getValue("consistency").count { check ->
            val linked = interests.getValue(requireNotNull(check.linked))
            selectedType(check, answers.checks[check.id]) != selectedType(linked, answers.pairs.getValue(linked.id))
        }
        val hintIds = buildList {
            if (answers.nad["NAD03"] == "a" && "biology" in topSpheres) add("H01")
            if (answers.nad["NAD04"] in listOf("a", "b")) add("H02")
            if (answers.nad["NAD05"] == "c" && "business" in topSpheres) add("H03")
            if (answers.nad["NAD02"] == "a") add("H04")
            if (answers.nad["NAD01"] == "d" && topSpheres.any { it in listOf("public_service", "biology") }) add("H05")
        }
        val code = interestCode(riasec, answers.pairs, interests.values.toList())
        val value = answers.valuesTop3.firstOrNull()
        return ScoringResult(
            config.version, riasec, domains, enriched, topSpheres, code,
            riasec.mapValues { level(it.value, levels.getValue("riasec")) },
            domains.mapValues { level(it.value, levels.getValue("domain")) },
            spheres.flatMapIndexed { index, sphere ->
                spheres.drop(index + 1).filter {
                    sphere.percent - it.percent < thresholds.getValue("equivalent_percent_difference").jsonPrimitive.int
                }.map { listOf(sphere.id, it.id) }
            },
            mismatches,
            config.analyst.getValue("consistency_warning").jsonPrimitive.content.takeIf {
                mismatches >= thresholds.getValue("consistency_mismatches").jsonPrimitive.int
            },
            hintIds.map { id ->
                val hint = config.hints.first { it.jsonObject.getValue("ID").jsonPrimitive.content == id }.jsonObject
                ResultHint(id, hint.getValue("Текст подсказки").jsonPrimitive.content)
            },
            if (value == "VAL03") config.analyst.getValue("income_anchor").jsonPrimitive.content
            else value?.let { config.anchors.getValue(it).jsonPrimitive.content },
            config.analyst.getValue("riasec_labels").jsonObject.getValue(code.take(1)).jsonPrimitive.content,
        )
    }

    private fun selectedType(question: ConfigQuestion, answer: String?): String? =
        answer?.takeIf { it == question.type_a || it == question.type_b }

    internal fun interestCode(scores: Map<String, Int>, pairs: Map<String, String>, questions: List<ConfigQuestion>): String =
        listOf("R", "I", "A", "S", "E", "C").groupBy { scores.getValue(it) }.toSortedMap(reverseOrder()).values
            .flatMap { tied ->
                val wins = tied.associateWith { type ->
                    questions.count { q -> q.type_a in tied && q.type_b in tied && selectedType(q, pairs.getValue(q.id)) == type }
                }
                val ordered = tied.sortedByDescending { wins.getValue(it) }
                if (ordered.map { wins.getValue(it) } == tied.indices.reversed().toList()) ordered else tied
            }.take(3).joinToString("")

    internal fun level(score: Int, rules: JsonElement): String = rules.jsonArray.first {
        score >= it.jsonObject.getValue("min").jsonPrimitive.int
    }.jsonObject.getValue("label").jsonPrimitive.content

    internal fun roundPercent(value: Double): Int = floor(value + 0.5).toInt().coerceAtLeast(0)
}
