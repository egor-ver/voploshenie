package ru.voploshchenie.app.domain.model

data class CatalogRecommendation(val item: CatalogItem, val fromSphere: String? = null, val isOther: Boolean = false)
