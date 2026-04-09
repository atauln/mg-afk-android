package com.mgafk.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class WakeLockMode {
    /** Never acquire a CPU wake lock. */
    OFF,
    /** Acquire automatically after the screen has been off for a while, release on unlock. */
    SMART,
    /** Always keep the CPU wake lock while a session is running. */
    ALWAYS,
}

@Serializable
data class AppSettings(
    // Background & Battery
    val wifiLockEnabled: Boolean = true,
    val wakeLockMode: WakeLockMode = WakeLockMode.SMART,
    val wakeLockAutoDelayMin: Int = 5,

    // Reconnection
    val retryDelayMs: Long = 1500,
    val retryMaxDelayMs: Long = 60000,
    val retrySupersededDelayMs: Long = 30000,
    val notifyOnDisconnect: Boolean = false,

    // Developer
    val showDebugMenu: Boolean = false,
)
