package com.mgafk.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AlertConfig(
    val items: Map<String, AlertItem> = emptyMap(),
)

@Serializable
data class AlertItem(
    val enabled: Boolean = false,
    val mode: AlertMode = AlertMode.NOTIFICATION,
)

@Serializable
enum class AlertMode { NOTIFICATION, SOUND }
