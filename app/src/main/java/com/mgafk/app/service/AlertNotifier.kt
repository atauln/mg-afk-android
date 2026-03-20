package com.mgafk.app.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.mgafk.app.MgAfkApp
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.model.Pet
import com.mgafk.app.data.websocket.Constants

/**
 * Sends Android notifications for shop alerts, pet hunger, and weather changes.
 */
class AlertNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)
    private var notificationId = 1000

    fun checkPetHunger(pets: List<Pet>, alerts: AlertConfig) {
        val hungerAlert = alerts.items["hunger<5"] ?: return
        if (!hungerAlert.enabled) return

        for (pet in pets) {
            val maxHunger = Constants.PET_HUNGER_COSTS[pet.species.lowercase()] ?: continue
            val percent = (pet.hunger.toFloat() / maxHunger) * 100
            if (percent < Constants.PET_HUNGER_THRESHOLD) {
                notify("Pet Hunger", "${pet.name} (${pet.species}) is at ${"%.1f".format(percent)}%!")
            }
        }
    }

    fun checkWeather(weather: String, previousWeather: String, alerts: AlertConfig) {
        if (weather == previousWeather || weather.isBlank()) return
        val key = "weather:$weather"
        val alert = alerts.items[key] ?: return
        if (!alert.enabled) return
        notify("Weather Change", "Weather changed to $weather")
    }

    fun checkShopItems(
        category: String,
        items: List<com.mgafk.app.data.model.ShopItem>,
        alerts: AlertConfig,
    ) {
        for (item in items) {
            val key = "shop:$category:${item.name}"
            val alert = alerts.items[key] ?: continue
            if (!alert.enabled) continue
            notify("Shop Alert", "${item.name} is now available in ${category}!")
        }
    }

    private fun notify(title: String, body: String) {
        val notification = NotificationCompat.Builder(context, MgAfkApp.CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        manager.notify(notificationId++, notification)
    }
}
