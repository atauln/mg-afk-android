package com.mgafk.app.ui.screens.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.AlertConfig
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted

private val WEATHER_ITEMS = listOf(
    "Sunny" to "Clear Skies",
    "Rain" to "Rain",
    "Frost" to "Snow",
    "AmberMoon" to "Amber Moon",
    "Dawn" to "Dawn",
    "Thunderstorm" to "Thunderstorm",
)

private const val HUNGER_KEY = "hunger<5"

private val ALERT_TABS = listOf("Shops", "Weather", "Pets")

private val SHOP_CATEGORIES = listOf(
    "Seeds" to "seed",
    "Tools" to "tool",
    "Eggs" to "egg",
    "Decors" to "decor",
)


@Composable
fun AlertsCard(
    alerts: AlertConfig,
    onToggle: (key: String, enabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    AppCard(modifier = modifier, title = "Alerts") {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = SurfaceDark,
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Accent,
                    )
                }
            },
            modifier = Modifier.clip(RoundedCornerShape(10.dp)),
        ) {
            ALERT_TABS.forEachIndexed { index, label ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> ShopAlertsTab(alerts, onToggle)
            1 -> WeatherAlertsTab(alerts, onToggle)
            2 -> PetAlertsTab(alerts, onToggle)
        }
    }
}

// ---- Shop Alerts ----

@Composable
private fun ShopAlertsTab(alerts: AlertConfig, onToggle: (String, Boolean) -> Unit) {
    var shopTab by remember { mutableIntStateOf(0) }

    ScrollableTabRow(
        selectedTabIndex = shopTab,
        edgePadding = 0.dp,
        containerColor = SurfaceBorder.copy(alpha = 0.3f),
        indicator = { tabPositions ->
            if (shopTab < tabPositions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[shopTab]),
                    color = Accent.copy(alpha = 0.6f),
                )
            }
        },
        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
    ) {
        SHOP_CATEGORIES.forEachIndexed { index, (label, _) ->
            Tab(
                selected = shopTab == index,
                onClick = { shopTab = index },
                text = { Text(label, fontSize = 11.sp) },
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    val (_, category) = SHOP_CATEGORIES[shopTab]
    ShopCategoryItems(category, alerts, onToggle)
}

@Composable
private fun ShopCategoryItems(
    category: String,
    alerts: AlertConfig,
    onToggle: (String, Boolean) -> Unit,
) {
    data class AlertEntry(val id: String, val name: String, val rarity: String?, val sprite: String?)

    // Recompute when category or apiReady changes
    val data = when (category) {
        "seed" -> MgApi.getPlants()
        "tool" -> MgApi.getItems()
        "egg" -> MgApi.getEggs()
        "decor" -> MgApi.getDecors()
        else -> emptyMap()
    }
    val items = data.values.map { AlertEntry(it.id, it.name, it.rarity, it.sprite) }

    if (items.isEmpty() && !MgApi.isReady) {
        Text("Loading items...", fontSize = 12.sp, color = TextMuted)
    } else if (items.isEmpty()) {
        Text("No items found.", fontSize = 12.sp, color = TextMuted)
    } else {
        Column {
            items.forEach { entry ->
                val key = "shop:$category:${entry.id}"
                val enabled = alerts.items[key]?.enabled ?: false
                AlertItemRow(
                    spriteUrl = entry.sprite,
                    label = entry.name,
                    rarity = entry.rarity,
                    enabled = enabled,
                    onToggle = { onToggle(key, it) },
                )
            }
        }
    }
}

// ---- Weather Alerts ----

@Composable
private fun WeatherAlertsTab(alerts: AlertConfig, onToggle: (String, Boolean) -> Unit) {
    val weatherSprites = MgApi.getWeathers().mapValues { it.value.sprite }

    Column {
        WEATHER_ITEMS.forEach { (apiKey, displayName) ->
            val key = "weather:$displayName"
            val enabled = alerts.items[key]?.enabled ?: false
            val spriteUrl = weatherSprites[apiKey]

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceBorder.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (spriteUrl != null) {
                    SpriteImage(url = spriteUrl, size = 20.dp, contentDescription = displayName)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(displayName, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { onToggle(key, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent),
                )
            }
        }
    }
}

// ---- Pet Alerts ----

@Composable
private fun PetAlertsTab(alerts: AlertConfig, onToggle: (String, Boolean) -> Unit) {
    val enabled = alerts.items[HUNGER_KEY]?.enabled ?: false

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceBorder.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Hunger < 5%", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle(HUNGER_KEY, it) },
            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
        )
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Get notified when any pet drops below 5% hunger.",
        fontSize = 11.sp,
        color = TextMuted,
    )
}

// ---- Rarity colors ----

private val RARITY_COLORS = mapOf(
    "Common" to Color(0xFF9CA3AF),
    "Uncommon" to Color(0xFF4ADE80),
    "Rare" to Color(0xFF60A5FA),
    "Legendary" to Color(0xFFC084FC),
    "Mythic" to Color(0xFFF472B6),
    "Divine" to Color(0xFFFBBF24),
    "Celestial" to Color(0xFF38BDF8),
)

// ---- Reusable item row ----

@Composable
private fun AlertItemRow(
    spriteUrl: String?,
    label: String,
    rarity: String? = null,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val rarityColor = rarity?.let { RARITY_COLORS[it] }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceBorder.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpriteImage(url = spriteUrl, size = 20.dp, contentDescription = label)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp)
            if (rarity != null && rarityColor != null) {
                Text(
                    text = rarity,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = rarityColor,
                )
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent),
        )
    }
}
