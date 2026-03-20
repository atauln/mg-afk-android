package com.mgafk.app.data.websocket

import com.mgafk.app.data.model.AbilityLog
import com.mgafk.app.data.model.Pet
import com.mgafk.app.data.model.ShopItem
import com.mgafk.app.data.model.ShopState
import com.mgafk.app.data.model.RestockTimers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Extracts typed model data from raw JsonElement game state.
 */
object StateParser {

    fun formatWeather(value: String?): String {
        if (value.isNullOrBlank()) return "Clear Skies"
        return Constants.WEATHER_MAP[value.trim().lowercase()] ?: value.trim()
    }

    // ---- Players ----

    data class PlayerInfo(
        val id: String,
        val name: String,
        val isConnected: Boolean,
        val databaseUserId: String?,
    )

    fun extractPlayers(roomState: JsonElement?): List<PlayerInfo> {
        val players = (roomState as? JsonObject)?.get("players") as? JsonArray ?: return emptyList()
        return players.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            PlayerInfo(
                id = obj.string("id"),
                name = obj.string("name"),
                isConnected = obj["isConnected"]?.jsonPrimitive?.booleanOrNull ?: false,
                databaseUserId = obj["databaseUserId"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    // ---- User Slot ----

    fun findUserSlotIndex(
        roomState: JsonElement?,
        gameState: JsonElement?,
        playerId: String,
        playerIndex: Int,
    ): Int? {
        val slots = (gameState as? JsonObject)?.get("userSlots") as? JsonArray ?: return null
        val players = extractPlayers(roomState)
        val dbId = players.find { it.id == playerId }?.databaseUserId

        // Try by playerIndex first
        if (playerIndex >= 0 && playerIndex < slots.size) {
            val slot = slots[playerIndex] as? JsonObject
            if (slot != null && matchesPlayer(slot, playerId)) return playerIndex
        }

        // Try by playerId
        for (i in slots.indices) {
            val slot = slots[i] as? JsonObject ?: continue
            if (matchesPlayer(slot, playerId)) return i
        }

        // Try by databaseUserId
        if (dbId != null) {
            for (i in slots.indices) {
                val slot = slots[i] as? JsonObject ?: continue
                if (matchesDb(slot, dbId)) return i
            }
        }

        return null
    }

    private fun matchesPlayer(slot: JsonObject, playerId: String): Boolean {
        if (slot.string("playerId") == playerId) return true
        val data = slot["data"] as? JsonObject
        return data?.string("playerId") == playerId
    }

    private fun matchesDb(slot: JsonObject, dbId: String): Boolean {
        val data = slot["data"] as? JsonObject ?: return false
        return data.string("databaseUserId") == dbId || data.string("userId") == dbId
    }

    // ---- Pets ----

    fun extractPets(gameState: JsonElement?, slotIndex: Int?): List<Pet> {
        if (slotIndex == null) return emptyList()
        val slots = (gameState as? JsonObject)?.get("userSlots") as? JsonArray ?: return emptyList()
        val slot = slots.getOrNull(slotIndex) as? JsonObject ?: return emptyList()
        val data = slot["data"] as? JsonObject ?: return emptyList()
        val petSlots = data["petSlots"] as? JsonArray ?: return emptyList()

        return petSlots.mapIndexedNotNull { index, el ->
            val pet = el as? JsonObject ?: return@mapIndexedNotNull null
            val mutations = (pet["mutations"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            Pet(
                id = pet.string("id"),
                name = pet.string("name"),
                species = pet.string("petSpecies"),
                hunger = pet["hunger"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                index = index,
                mutations = mutations,
            )
        }
    }

    // ---- Shops ----

    fun extractShops(gameState: JsonElement?): ShopState {
        val shops = (gameState as? JsonObject)?.get("shops") as? JsonObject ?: return ShopState()
        return ShopState(
            seed = extractShopItems(shops["seed"], "species"),
            tool = extractShopItems(shops["tool"], "toolId"),
            egg = extractShopItems(shops["egg"], "eggId"),
            decor = extractShopItems(shops["decor"], "decorId"),
            restock = RestockTimers(
                seed = extractRestock(shops["seed"]),
                tool = extractRestock(shops["tool"]),
                egg = extractRestock(shops["egg"]),
                decor = extractRestock(shops["decor"]),
            ),
        )
    }

    private fun extractShopItems(shop: JsonElement?, key: String): List<ShopItem> {
        val inventory = (shop as? JsonObject)?.get("inventory") as? JsonArray ?: return emptyList()
        return inventory.mapNotNull { el ->
            val item = el as? JsonObject ?: return@mapNotNull null
            val stock = item["initialStock"]?.jsonPrimitive?.intOrNull ?: 0
            if (stock <= 0) return@mapNotNull null
            val name = item[key]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ShopItem(name = name, stock = stock)
        }
    }

    private fun extractRestock(shop: JsonElement?): Int {
        return (shop as? JsonObject)?.get("secondsUntilRestock")?.jsonPrimitive?.intOrNull ?: 0
    }

    // ---- Activity Logs ----

    fun isAbilityName(action: String?): Boolean {
        if (action.isNullOrBlank()) return false
        return action.trim().lowercase() !in Constants.BLOCKED_ABILITIES
    }

    // ---- Helpers ----

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    fun fmtDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
}
