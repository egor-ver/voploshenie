package ru.voploshchenie.app.domain.scoring

import ru.voploshchenie.app.domain.model.TestConfig

object ConfigValidator {
    private val riasec = setOf("R", "I", "A", "S", "E", "C")
    private val domains = setOf("logic", "numbers", "text", "space", "people")

    fun validate(config: TestConfig): List<String> = buildList {
        val questions = config.parts.flatMap { it.questions }
        val duplicateIds = (questions.map { it.id } + config.spheres.map { it.id }).groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicateIds.isNotEmpty()) add("Повторяются ID вопросов: ${duplicateIds.sorted().joinToString()}")
        val byId = questions.associateBy { it.id }

        val interests = questions.filter { it.id.startsWith("INT") }
        val pairs = interests.mapNotNull { q ->
            if (q.type_a != null && q.type_b != null) setOf(q.type_a, q.type_b) else null
        }
        val expectedPairs = riasec.flatMap { a -> riasec.filter { it > a }.map { b -> setOf(a, b) } }.toSet()
        if (interests.size != 15 || pairs.size != 15 || pairs.toSet() != expectedPairs)
            add("INT должны покрывать 15 пар RIASEC ровно по разу")

        config.spheres.forEach { sphere ->
            val count = questions.count { it.id.startsWith("SPH") && it.type == "likert5" && it.sphere_id == sphere.id }
            if (count != 2) add("Сфера ${sphere.id}: ожидалось 2 вопроса SPH, найдено $count")
            if (sphere.values.any { byId[it]?.type != "rank_values" || !it.startsWith("VAL") }) add("Сфера ${sphere.id}: ссылка на несуществующий VAL")
            if (sphere.needs.any { byId[it]?.type != "likert5" || !it.startsWith("NEED") }) add("Сфера ${sphere.id}: ссылка на несуществующий NEED")
            if (sphere.riasec.any { it !in riasec }) add("Сфера ${sphere.id}: неизвестный тип RIASEC")
            if (sphere.domains.any { it !in domains }) add("Сфера ${sphere.id}: неизвестный домен")
        }

        questions.filter { it.id.startsWith("TSK") }.forEach { q ->
            if (q.options.none { it.id == q.correct }) add("${q.id}: ключ отсутствует среди вариантов")
            if (q.domain !in domains) add("${q.id}: неизвестный домен")
        }
        questions.filter { it.id.startsWith("CHK") }.forEach { check ->
            val linked = check.linked?.let(byId::get)
            if (linked == null || !linked.id.startsWith("INT") || check.type_a == null || check.type_b == null ||
                linked.type_a == null || linked.type_b == null || setOf(check.type_a, check.type_b) != setOf(linked.type_a, linked.type_b))
                add("${check.id}: типы не совпадают со связанной парой INT")
        }

        config.spheres.forEach { sphere ->
            val sphereQuestions = questions.filter { it.id.startsWith("SPH") && it.sphere_id == sphere.id }.map { it.id }
            if (sphere.questions.toSet() != sphereQuestions.toSet() || sphere.questions.size != sphereQuestions.size)
                add("Сфера ${sphere.id}: список вопросов SPH не совпадает")
        }
    }
}
