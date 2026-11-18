package ru.voploshchenie.app.domain.model

data class CatalogItem(
    val id: String,
    val type: String,
    val title: String,
    val organizer: String? = null,
    val direction: String,
    val spheres: List<String>,
    val format: String? = null,
    val city: String? = null,
    val audience_text: String,
    val dates: List<CatalogDate> = emptyList(),
    val date_text: String = "",
    val is_rolling: Boolean = false,
    val url: String,
    val url_kind: String,
)
