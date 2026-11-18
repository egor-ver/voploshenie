package ru.voploshchenie.app.data.local.asset

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.voploshchenie.app.domain.model.CatalogItem

object CatalogLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<CatalogItem> = parse(context.assets.open("catalog.json").bufferedReader().use { it.readText() })

    /** Дополнительные позиции (assets/catalog_extra.json); основной catalog.json не меняется. */
    fun loadExtra(context: Context): List<CatalogItem> = parse(context.assets.open("catalog_extra.json").bufferedReader().use { it.readText() })

    fun parse(source: String): List<CatalogItem> = json.decodeFromString<List<CatalogItemDto>>(source).map { it.toDomain() }
}

@Serializable
private data class CatalogDateDto(val kind: String, val date: String, val precision: String) {
    fun toDomain() = ru.voploshchenie.app.domain.model.CatalogDate(kind, date, precision)
}

@Serializable
private data class CatalogItemDto(
    val id: String,
    val type: String,
    val title: String,
    val organizer: String? = null,
    val direction: String,
    val spheres: List<String>,
    val format: String? = null,
    val city: String? = null,
    val audience_text: String,
    val dates: List<CatalogDateDto> = emptyList(),
    val date_text: String = "",
    val is_rolling: Boolean = false,
    val url: String,
    val url_kind: String,
) {
    fun toDomain() = CatalogItem(id, type, title, organizer, direction, spheres, format, city,
        audience_text, dates.map { it.toDomain() }, date_text, is_rolling, url, url_kind)
}
