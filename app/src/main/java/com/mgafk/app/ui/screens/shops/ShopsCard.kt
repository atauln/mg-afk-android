package com.mgafk.app.ui.screens.shops

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.ShopItem
import com.mgafk.app.data.model.ShopState
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import kotlinx.coroutines.delay

private val SHOP_TABS = listOf("Seeds" to "seed", "Tools" to "tool", "Eggs" to "egg", "Decors" to "decor")
private val SPRITE_CATEGORY = mapOf("seed" to "seeds", "tool" to "items", "egg" to "pets", "decor" to "decor")

@Composable
fun ShopsCard(shops: ShopState, modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableIntStateOf(0) }

    AppCard(modifier = modifier, title = "Shops") {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = SurfaceDark,
            contentColor = MaterialTheme.colorScheme.onSurface,
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
            SHOP_TABS.forEachIndexed { index, (label, _) ->
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

        Spacer(modifier = Modifier.height(10.dp))

        val (_, key) = SHOP_TABS[selectedTab]
        val items = when (key) { "seed" -> shops.seed; "tool" -> shops.tool; "egg" -> shops.egg; "decor" -> shops.decor; else -> emptyList() }
        val restockSec = when (key) { "seed" -> shops.restock.seed; "tool" -> shops.restock.tool; "egg" -> shops.restock.egg; "decor" -> shops.restock.decor; else -> 0 }

        RestockTimer(restockSec)
        Spacer(modifier = Modifier.height(6.dp))

        if (items.isEmpty()) {
            Text("No items.", fontSize = 12.sp, color = TextMuted)
        } else {
            val spriteCategory = SPRITE_CATEGORY[key] ?: key
            items.forEach { item -> ShopItemRow(item, spriteCategory) }
        }
    }
}

@Composable
private fun RestockTimer(initialSeconds: Int) {
    var remaining by remember(initialSeconds) { mutableStateOf(initialSeconds) }
    LaunchedEffect(initialSeconds) {
        while (remaining > 0) { delay(1000); remaining-- }
    }
    Text(
        text = "Restock in %02d:%02d".format(remaining / 60, remaining % 60),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = Accent.copy(alpha = 0.7f),
    )
}

@Composable
private fun ShopItemRow(item: ShopItem, spriteCategory: String) {
    val entry = MgApi.findItem(item.name)
    val displayName = entry?.name ?: item.name
    val spriteUrl = entry?.sprite

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceBorder.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpriteImage(url = spriteUrl, size = 22.dp, contentDescription = displayName)
            Spacer(modifier = Modifier.width(8.dp))
            Text(displayName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text("x${item.stock}", fontSize = 12.sp, color = TextMuted)
    }
}
