package ru.voploshchenie.app.domain.usecase

import ru.voploshchenie.app.domain.model.CatalogDate
import ru.voploshchenie.app.domain.model.CatalogItem
import ru.voploshchenie.app.domain.model.CatalogRecommendation
import ru.voploshchenie.app.domain.model.CatalogResult
import java.time.LocalDate
import java.time.YearMonth

class CatalogSelection(private val items: List<CatalogItem>) {
    fun all(): List<CatalogItem> = items

    fun filter(type: String? = null, query: String = "", candidates: List<CatalogItem> = items): List<CatalogItem> = candidates.filter { item ->
        (type == null || item.type == type) && (query.isBlank() || item.title.contains(query.trim(), true) || item.organizer?.contains(query.trim(), true) == true)
    }

    fun byNearestDate(today: LocalDate, candidates: List<CatalogItem> = items): List<CatalogItem> = candidates.sortedWith(
        compareBy<CatalogItem> { it.dates.mapNotNull { date -> date.toSortDate(today) }.minOrNull() ?: LocalDate.MAX }
            .thenBy { it.title }
    )

    fun byInterests(result: CatalogResult, candidates: List<CatalogItem> = items): List<CatalogItem> = candidates.sortedByDescending { matchPercent(it, result) }

    fun byCourse(course: String?, candidates: List<CatalogItem> = items): List<CatalogItem> {
        val target = course?.let(::courseLevel) ?: return candidates
        val groups = candidates.groupBy { item -> audienceMatch(item.audience_text, target) }
        return groups[true].orEmpty() + groups[false].orEmpty()
    }

    fun recommendations(result: CatalogResult): List<CatalogRecommendation> {
        val primary = result.topSpheres.firstOrNull() ?: return emptyList()
        val selected = mutableSetOf<String>()
        val primaryItems = items.filter { primary in it.spheres && selected.add(it.id) }
        val extra = result.topSpheres.drop(1).take(2).flatMap { sphere ->
            items.filter { sphere in it.spheres && selected.add(it.id) }.map { CatalogRecommendation(it, sphere) }
        }
        val other = items.filter { selected.add(it.id) }.map { CatalogRecommendation(it, isOther = true) }
        return primaryItems.map { CatalogRecommendation(it) } + extra + other
    }

    fun similar(item: CatalogItem): List<CatalogItem> = items.filter { candidate ->
        candidate.id != item.id && candidate.type == item.type && candidate.spheres.any(item.spheres::contains)
    }
}

private data class CourseLevel(val stage: String, val year: Int? = null)

private fun courseLevel(value: String): CourseLevel? {
    val text = value.lowercase()
    val stage = when {
        "аспирантур" in text -> "graduate"
        "магистратур" in text -> "master"
        "бакалавриат" in text || "специалитет" in text -> "undergraduate"
        Regex("\\d+\\s*курс").containsMatchIn(text) -> "undergraduate"
        else -> return null
    }
    val year = Regex("\\d+").find(text)?.value?.toIntOrNull()
    if (stage != "graduate" && year !in 1..6) return null
    return CourseLevel(stage, year)
}

private fun audienceMatch(audience: String, target: CourseLevel): Boolean {
    val normalized = audience.lowercase()
    if (target.stage == "graduate") return "аспирантур" in normalized
    val clauses = normalized.split(',', ';')
    return clauses.withIndex().any { (index, clause) ->
        val hasMaster = "магистратур" in clause
        val hasUndergraduate = "бакалавриат" in clause || "специалитет" in clause ||
            Regex("\\d+\\s*[-–—]\\s*\\d+\\s*курс|\\d+\\s*курс|курс\\s*от\\s*\\d+|от\\s*\\d+\\s*курс").containsMatchIn(clause)
        val matchesStage = when (target.stage) {
            "master" -> hasMaster
            else -> !hasMaster && hasUndergraduate
        }
        if (!matchesStage) return@any false
        if (target.year == null || hasMaster && !Regex("\\d+\\s*курс").containsMatchIn(clause)) return@any true
        val enumeration = if ("курс" in clause) clauses.take(index).takeLastWhile { it.trim().matches(Regex("\\d+")) }.joinToString(" ") else ""
        val courseNumbers = Regex("(\\d+)\\s*(?:[-–—]\\s*(\\d+))?").findAll("$enumeration $clause").flatMap { match ->
            val first = match.groupValues[1].toIntOrNull() ?: return@flatMap emptyList()
            val last = match.groupValues[2].toIntOrNull() ?: if ("старше" in clause || "от " in clause) 6 else first
            (first..last).toList()
        }
        target.year in courseNumbers
    }
}

fun matchPercent(item: CatalogItem, result: CatalogResult): Int =
    result.spheres.filter { it.id in item.spheres }.maxOfOrNull { it.percent } ?: 0

private fun CatalogDate.toSortDate(today: LocalDate): LocalDate? = datePeriod()?.let { (start, end) ->
    if (end < today) null else maxOf(start, today)
}

internal fun opportunityClosed(item: CatalogItem, today: LocalDate): Boolean =
    item.dates.isNotEmpty() && item.dates.all { it.datePeriod()?.second?.let { end -> end < today } == true }

internal fun CatalogDate.datePeriod(): Pair<LocalDate, LocalDate>? = try {
    when (precision) {
        "day" -> LocalDate.parse(date).let { it to it }
        "month" -> YearMonth.parse(date).let { it.atDay(1) to it.atEndOfMonth() }
        "year" -> LocalDate.parse("$date-01-01") to LocalDate.parse("$date-12-31")
        else -> null
    }
} catch (_: java.time.format.DateTimeParseException) {
    // Unparseable catalog dates remain undated, as required by ?11.
    null
}
