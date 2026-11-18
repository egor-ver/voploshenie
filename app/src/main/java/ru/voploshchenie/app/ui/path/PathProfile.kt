package ru.voploshchenie.app.ui.path

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import ru.voploshchenie.app.domain.model.PathProfileData
import ru.voploshchenie.app.domain.model.TestConfig
import ru.voploshchenie.app.domain.scoring.ScoringResult

internal fun pathProfileData(
    result: ScoringResult, name: String?, config: TestConfig,
): PathProfileData {
    val spheres = config.texts.getValue("spheres").jsonObject.getValue("table").jsonArray
    val top = result.topSpheres.take(3)
    val firstIndex = config.spheres.indexOfFirst { it.id == result.topSpheres.firstOrNull() }
    val experiment = if (firstIndex < 0) "" else spheres[firstIndex].jsonObject.entries
        .firstOrNull { it.key.contains("Вход и первые пробы") }?.value?.jsonPrimitive?.contentOrNull.orEmpty()
    return PathProfileData(name, result.riasecCode.firstOrNull(), top, result.topSpheres.firstOrNull().orEmpty(), experiment,
        result.spheres.firstOrNull { it.id == result.topSpheres.firstOrNull() }?.percent)
}
