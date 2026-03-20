package com.mgafk.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MgAfkApp : Application() {

    companion object {
        const val CHANNEL_SERVICE = "mgafk_service"
        const val CHANNEL_ALERTS = "mgafk_alerts"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "AFK Connection",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the WebSocket connection alive in background"
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Game Alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Shop items, pet hunger, weather alerts"
        }

        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(alertsChannel)
    }
}
