package ru.voploshchenie.app.domain.model

data class PathProfileData(
    val name: String?, val labelCode: Char?, val topSpheres: List<String>, val topSphere: String,
    val experiment: String,
    val percent: Int? = null,
)

