package com.mgafk.app.ui.screens.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import com.mgafk.app.data.model.InventoryCropsItem
import com.mgafk.app.data.model.InventoryDecorItem
import com.mgafk.app.data.model.InventoryEggItem
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.InventoryPlantItem
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.InventorySeedItem
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary

private val RarityCommon = Color(0xFFE7E7E7)
private val RarityUncommon = Color(0xFF67BD4D)
private val RarityRare = Color(0xFF0071C6)
private val RarityLegendary = Color(0xFFFFC734)
private val RarityMythical = Color(0xFF9944A7)
private val RarityDivine = Color(0xFFFF7835)
private val RarityCelestial = Color(0xFFFF00FF)

private fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "common" -> RarityCommon; "uncommon" -> RarityUncommon; "rare" -> RarityRare
    "legendary" -> RarityLegendary; "mythical", "mythic" -> RarityMythical
    "divine" -> RarityDivine; "celestial" -> RarityCelestial; else -> TextMuted
}

private val TILE_MIN = 76.dp
private val GAP = 6.dp

private const val MUT_BASE = "https://mg-api.ariedam.fr/assets/sprites/ui/Mutation"
private val MUT_MAP = mapOf("Ambershine" to "Amberlit")
private fun mutUrl(m: String) = "$MUT_BASE${MUT_MAP[m] ?: m}.png"

private const val XP_H = 3600.0; private const val BASE_S = 80; private const val MAX_S = 100; private const val S_GAIN = 30
private fun maxStr(sp: String, sc: Double): Int {
    val ms = MgApi.findPet(sp)?.maxScale ?: return BASE_S
    if (sc <= 1.0) return BASE_S; if (sc >= ms) return MAX_S
    return (BASE_S + 20 * (sc - 1.0) / (ms - 1.0)).toInt()
}
private fun curStr(sp: String, xp: Double, max: Int): Int {
    val htm = MgApi.findPet(sp)?.hoursToMature ?: return max - S_GAIN
    return ((max - S_GAIN) + minOf(S_GAIN / htm * (xp / XP_H), S_GAIN.toDouble())).toInt()
}

private val RARITY_ORDER = listOf("Celestial", "Divine", "Mythic", "Mythical", "Legendary", "Rare", "Uncommon", "Common")
private fun raritySort(id: String): Int {
    val r = MgApi.findItem(id)?.rarity ?: return RARITY_ORDER.size
    return RARITY_ORDER.indexOfFirst { it.equals(r, ignoreCase = true) }.let { if (it < 0) RARITY_ORDER.size else it }
}
private fun raritySortPet(sp: String): Int {
    val r = MgApi.findPet(sp)?.rarity ?: return RARITY_ORDER.size
    return RARITY_ORDER.indexOfFirst { it.equals(r, ignoreCase = true) }.let { if (it < 0) RARITY_ORDER.size else it }
}

private fun fmtQty(q: Int): String = when {
    q >= 1_000_000 -> "%.1fM".format(q / 1_000_000.0)
    q >= 10_000 -> "${q / 1000}K"
    q >= 1_000 -> "%.1fK".format(q / 1000.0)
    else -> "$q"
}

// ── Seed Silo ──

@Composable
fun SeedSiloCard(seeds: List<InventorySeedItem>, apiReady: Boolean) {
    val sorted = remember(seeds, apiReady) { seeds.sortedBy { raritySort(it.species) } }
    AppCard(title = "Seed Silo", collapsible = true, persistKey = "storage.seedSilo", trailing = {
        Text("${seeds.size}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        if (sorted.isEmpty()) {
            Text("Empty", fontSize = 12.sp, color = TextMuted)
        } else {
            GridOf(sorted.size) { i -> QtyTile(sorted[i].species, sorted[i].quantity, apiReady) }
        }
    }
}

// ── Decor Shed ──

@Composable
fun DecorShedCard(decors: List<InventoryDecorItem>, apiReady: Boolean) {
    val sorted = remember(decors, apiReady) { decors.sortedBy { raritySort(it.decorId) } }
    AppCard(title = "Decor Shed", collapsible = true, persistKey = "storage.decorShed", trailing = {
        Text("${decors.size}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        if (sorted.isEmpty()) {
            Text("Empty", fontSize = 12.sp, color = TextMuted)
        } else {
            GridOf(sorted.size) { i -> QtyTile(sorted[i].decorId, sorted[i].quantity, apiReady) }
        }
    }
}

// ── Feeding Trough ──

private const val FEEDING_TROUGH_MAX = 9

@Composable
fun FeedingTroughCard(
    crops: List<InventoryCropsItem>,
    produce: List<InventoryProduceItem>,
    apiReady: Boolean,
    showTip: Boolean = false,
    onDismissTip: () -> Unit = {},
    onAddItems: (List<InventoryProduceItem>) -> Unit = {},
    onRemoveItem: (String) -> Unit = {},
) {
    val sorted = remember(crops, apiReady) { crops.sortedBy { raritySort(it.species) } }
    val troughIds = remember(crops) { crops.map { it.id }.toSet() }
    val availableProduce = remember(produce, troughIds) { produce.filter { it.id !in troughIds } }
    val slotsLeft = (FEEDING_TROUGH_MAX - crops.size).coerceAtLeast(0)
    var showPicker by remember { mutableStateOf(false) }

    AppCard(title = "Feeding Trough", collapsible = true, persistKey = "storage.feedingTrough", trailing = {
        Text("${crops.size}/$FEEDING_TROUGH_MAX", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        // First-time tip
        AnimatedVisibility(visible = showTip, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Accent.copy(alpha = 0.1f))
                    .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .clickable { onDismissTip() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tap a crop to remove it from the feeding trough.",
                        fontSize = 11.sp, color = Accent, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                    Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent,
                        modifier = Modifier.clickable { onDismissTip() })
                }
            }
        }

        val totalTiles = sorted.size + if (slotsLeft > 0 && availableProduce.isNotEmpty()) 1 else 0
        if (totalTiles == 0) {
            Text("Empty", fontSize = 12.sp, color = TextMuted)
        } else {
            GridOf(totalTiles) { i ->
                if (i < sorted.size) {
                    CropTile(sorted[i], apiReady, onClick = { onRemoveItem(sorted[i].id) })
                } else {
                    AddTile { showPicker = true }
                }
            }
        }
    }

    if (showPicker) {
        FeedingTroughPickerDialog(
            produce = availableProduce,
            maxSelectable = slotsLeft,
            apiReady = apiReady,
            onConfirm = { selected ->
                showPicker = false
                if (selected.isNotEmpty()) onAddItems(selected)
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Accent)
        Text("Add", fontSize = 8.sp, color = TextMuted, lineHeight = 10.sp)
    }
}

@Composable
private fun FeedingTroughPickerDialog(
    produce: List<InventoryProduceItem>,
    maxSelectable: Int,
    apiReady: Boolean,
    onConfirm: (List<InventoryProduceItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(produce, apiReady) { produce.sortedBy { raritySort(it.species) } }
    var selected by remember { mutableStateOf(setOf<String>()) } // item IDs

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(16.dp),
        ) {
            Text(
                "Add to Feeding Trough",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                "${selected.size}/$maxSelectable selected",
                fontSize = 11.sp,
                color = if (selected.size == maxSelectable) StatusConnected else TextMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )

            if (sorted.isEmpty()) {
                Text("No produce in inventory.", fontSize = 12.sp, color = TextMuted)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(TILE_MIN),
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                    verticalArrangement = Arrangement.spacedBy(GAP),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 100.dp)
                        .height(320.dp),
                ) {
                    items(sorted, key = { it.id }) { item ->
                        val isSelected = item.id in selected
                        val canSelect = selected.size < maxSelectable
                        PickerProduceTile(
                            item = item,
                            apiReady = apiReady,
                            isSelected = isSelected,
                            onClick = {
                                selected = if (isSelected) {
                                    selected - item.id
                                } else if (canSelect) {
                                    selected + item.id
                                } else selected
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                ) {
                    Text("Cancel", fontSize = 12.sp, color = TextSecondary)
                }
                Button(
                    onClick = {
                        val items = sorted.filter { it.id in selected }
                        onConfirm(items)
                    },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Text("Add ${selected.size}", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PickerProduceTile(
    item: InventoryProduceItem,
    apiReady: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val color = rarityColor(entry?.rarity)
    val borderColor = if (isSelected) StatusConnected else color.copy(alpha = 0.5f)
    val borderWidth = if (isSelected) 2.5.dp else 1.5.dp
    val price = remember(item.species, item.scale, item.mutations, apiReady) {
        PriceCalculator.calculateCropSellPrice(item.species, item.scale, item.mutations)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (item.mutations.isNotEmpty() || price != null) 0.8f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .background(if (isSelected) StatusConnected.copy(0.1f) else SurfaceDark)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(url = entry?.cropSprite, size = 28.dp, contentDescription = name)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        if (price != null) {
            Text(PriceCalculator.formatPrice(price), fontSize = 8.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700), lineHeight = 10.sp)
        }
        if (item.mutations.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                item.mutations.take(3).forEach { SpriteImage(url = mutUrl(it), size = 12.dp, contentDescription = it) }
            }
        }
        if (isSelected) {
            Text("✓", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusConnected, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun CropTile(item: InventoryCropsItem, apiReady: Boolean, onClick: () -> Unit = {}) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val color = rarityColor(entry?.rarity)

    Column(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(url = entry?.cropSprite, size = 28.dp, contentDescription = name)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        if (item.mutations.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                item.mutations.take(3).forEach { SpriteImage(url = mutUrl(it), size = 12.dp, contentDescription = it) }
            }
        }
    }
}

// ── Pet Hutch ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PetHutchCard(pets: List<InventoryPetItem>, apiReady: Boolean) {
    val sorted = remember(pets, apiReady) { pets.sortedBy { raritySortPet(it.petSpecies) } }
    AppCard(title = "Pet Hutch", collapsible = true, persistKey = "storage.petHutch", trailing = {
        Text("${pets.size}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        if (sorted.isEmpty()) {
            Text("Empty", fontSize = 12.sp, color = TextMuted)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sorted.forEach { pet -> PetRow(pet, apiReady) }
            }
        }
    }
}

// ── Shared: quantity tile ──

@Composable
private fun QtyTile(itemId: String, quantity: Int, apiReady: Boolean) {
    val entry = remember(itemId, apiReady) { MgApi.findItem(itemId) }
    val name = entry?.name ?: itemId
    val color = rarityColor(entry?.rarity)

    Column(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(SurfaceDark).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(url = entry?.sprite, size = 28.dp, contentDescription = name)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        Text(fmtQty(quantity), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Accent, lineHeight = 11.sp)
    }
}

// ── Shared: pet row ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PetRow(pet: InventoryPetItem, apiReady: Boolean) {
    val e = remember(pet.petSpecies, apiReady) { MgApi.findPet(pet.petSpecies) }
    val name = pet.name ?: e?.name ?: pet.petSpecies
    val color = rarityColor(e?.rarity)
    val ms = maxStr(pet.petSpecies, pet.targetScale)
    val cs = curStr(pet.petSpecies, pet.xp, ms)
    val isMax = cs >= ms

    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(0.25f), RoundedCornerShape(10.dp))
            .background(SurfaceDark).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpriteImage(url = e?.sprite, size = 32.dp, contentDescription = name)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                pet.mutations.forEach { SpriteImage(url = mutUrl(it), size = 13.dp, contentDescription = it) }
                val strText = if (isMax) "MAX $ms" else "$cs/$ms"
                val strCol = if (isMax) StatusConnected else TextMuted
                Text(strText, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = strCol)
            }
            if (pet.abilities.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    pet.abilities.forEach { id ->
                        val entry = remember(id, apiReady) { MgApi.getAbilities()[id] }
                        val bg = remember(entry?.color) {
                            entry?.color?.let {
                                try { Color(android.graphics.Color.parseColor(it)) } catch (_: Exception) { null }
                            } ?: Color(0xFF646464)
                        }
                        Text(entry?.name ?: id, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                            maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                .background(bg.copy(0.85f)).padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

// ── Adaptive grid ──

@Composable
private fun GridOf(count: Int, content: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = ((maxWidth + GAP) / (TILE_MIN + GAP)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
            (0 until count).chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    row.forEach { i -> Box(Modifier.weight(1f)) { content(i) } }
                    repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}
