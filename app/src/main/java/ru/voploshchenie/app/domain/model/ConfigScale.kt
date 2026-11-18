package ru.voploshchenie.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ConfigScale(val min: Int, val max: Int)
