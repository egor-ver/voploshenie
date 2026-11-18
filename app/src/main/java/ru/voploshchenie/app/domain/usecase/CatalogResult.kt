package ru.voploshchenie.app.domain.usecase

import kotlinx.serialization.json.Json
import ru.voploshchenie.app.domain.model.CatalogResult
import ru.voploshchenie.app.domain.model.CatalogSphereResult
import ru.voploshchenie.app.domain.model.SavedTestResult
import ru.voploshchenie.app.domain.scoring.ScoringResult

internal fun SavedTestResult.catalogResult(): CatalogResult {
    val score = Json.decodeFromString<ScoringResult>(payload)
    return CatalogResult(score.spheres.map { CatalogSphereResult(it.id, it.percent) }, score.topSpheres)
}
