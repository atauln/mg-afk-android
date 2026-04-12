package com.mgafk.app.ui.screens.storage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mgafk.app.data.model.InventoryDecorItem
import com.mgafk.app.data.model.InventoryEggItem
import com.mgafk.app.data.model.InventoryPetItem
import com.mgafk.app.data.model.InventoryPlantItem
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.InventorySeedItem
import com.mgafk.app.data.model.InventorySnapshot
import com.mgafk.app.data.model.InventoryToolItem
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.SurfaceBorder
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary
import com.mgafk.app.ui.components.mutationSpriteUrl
import com.mgafk.app.ui.components.sortMutations
import androidx.compose.ui.window.Dialog

// ── Rarity colors ──

private val RarityCommon = Color(0xFFE7E7E7)
private val RarityUncommon = Color(0xFF67BD4D)
private val RarityRare = Color(0xFF0071C6)
private val RarityLegendary = Color(0xFFFFD700)
private val RarityMythical = Color(0xFF9944A7)
private val RarityDivine = Color(0xFFFF7835)
private val RarityCelestial = Color(0xFFFF00FF)

private fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "common" -> RarityCommon
    "uncommon" -> RarityUncommon
    "rare" -> RarityRare
    "legendary" -> RarityLegendary
    "mythical", "mythic" -> RarityMythical
    "divine" -> RarityDivine
    "celestial" -> RarityCelestial
    else -> TextMuted
}

private val TILE_MIN_WIDTH = 76.dp
private val TILE_SPACING = 6.dp

// ── Strength (from game source) ──

private const val XP_PER_HOUR = 3600.0
private const val BASE_STR = 80
private const val MAX_STR = 100
private const val STR_GAINED = 30

private fun maxStr(species: String, scale: Double): Int {
    val ms = MgApi.findPet(species)?.maxScale ?: return BASE_STR
    if (scale <= 1.0) return BASE_STR
    if (scale >= ms) return MAX_STR
    return (BASE_STR + 20 * (scale - 1.0) / (ms - 1.0)).toInt()
}

private fun curStr(species: String, xp: Double, max: Int): Int {
    val htm = MgApi.findPet(species)?.hoursToMature ?: return max - STR_GAINED
    val gained = minOf(STR_GAINED / htm * (xp / XP_PER_HOUR), STR_GAINED.toDouble())
    return ((max - STR_GAINED) + gained).toInt()
}

// ── Size percent ──

private fun sizePercent(scale: Double, maxScale: Double): Double {
    if (maxScale <= 1.0) return if (scale >= 1.0) 100.0 else scale * 100.0
    return if (scale <= 1.0) scale * 50.0
    else (50.0 + (scale - 1.0) / (maxScale - 1.0) * 50.0).coerceIn(0.0, 100.0)
}

private val RARITY_ORDER = listOf("Celestial", "Divine", "Mythic", "Mythical", "Legendary", "Rare", "Uncommon", "Common")

/** Rarity index for sorting (lower = rarer = first). Unknown rarities go last. */
private fun raritySort(itemId: String): Int {
    val rarity = MgApi.findItem(itemId)?.rarity ?: return RARITY_ORDER.size
    return RARITY_ORDER.indexOfFirst { it.equals(rarity, ignoreCase = true) }.let { if (it < 0) RARITY_ORDER.size else it }
}

private fun raritySortPet(species: String): Int {
    val rarity = MgApi.findPet(species)?.rarity ?: return RARITY_ORDER.size
    return RARITY_ORDER.indexOfFirst { it.equals(rarity, ignoreCase = true) }.let { if (it < 0) RARITY_ORDER.size else it }
}

private fun fmtQty(q: Int): String = when {
    q >= 1_000_000 -> "%.1fM".format(q / 1_000_000.0).removeSuffix(".0M") + "M".takeIf { "M" !in "%.1fM".format(q / 1_000_000.0) }.orEmpty()
    q >= 10_000 -> "${q / 1000}K"
    q >= 1_000 -> "%.1fK".format(q / 1000.0)
    else -> "$q"
}

// ── Main ──

@Composable
fun InventoryCard(
    inventory: InventorySnapshot,
    apiReady: Boolean = false,
    freePlantTiles: Int = 0,
    onPlantSeed: (species: String) -> Unit = {},
    onGrowEgg: (eggId: String) -> Unit = {},
    showSeedTip: Boolean = false,
    onDismissSeedTip: () -> Unit = {},
) {
    val totalItems = inventory.seeds.size + inventory.eggs.size + inventory.produce.size +
        inventory.plants.size + inventory.pets.size + inventory.tools.size + inventory.decors.size

    var selectedSeedSpecies by remember { mutableStateOf<String?>(null) }
    var selectedEggId by remember { mutableStateOf<String?>(null) }

    AppCard(title = "Inventory", collapsible = true, persistKey = "storage.inventory", trailing = {
        Text("$totalItems types", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Accent.copy(0.7f))
    }) {
        if (totalItems == 0) {
            Text("No inventory data yet.", fontSize = 12.sp, color = TextMuted)
        } else {
            val sortedSeeds = remember(inventory.seeds, apiReady) { inventory.seeds.sortedBy { raritySort(it.species) } }
            val sortedTools = remember(inventory.tools, apiReady) { inventory.tools.sortedBy { raritySort(it.toolId) } }
            val sortedEggs = remember(inventory.eggs, apiReady) { inventory.eggs.sortedBy { raritySort(it.eggId) } }
            val sortedPlants = remember(inventory.plants, apiReady) { inventory.plants.sortedBy { raritySort(it.species) } }
            val sortedProduce = remember(inventory.produce, apiReady) { inventory.produce.sortedBy { raritySort(it.species) } }
            val sortedDecors = remember(inventory.decors, apiReady) { inventory.decors.sortedBy { raritySort(it.decorId) } }
            val sortedPets = remember(inventory.pets, apiReady) { inventory.pets.sortedBy { raritySortPet(it.petSpecies) } }

            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (sortedSeeds.isNotEmpty()) SubSection("Seeds", sortedSeeds.size) {
                    // First-time tip
                    AnimatedVisibility(visible = showSeedTip, enter = fadeIn(), exit = fadeOut()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Accent.copy(alpha = 0.1f))
                                .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .clickable { onDismissSeedTip() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Tap a seed to view details and plant it.",
                                    fontSize = 11.sp, color = Accent, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                                Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Accent,
                                    modifier = Modifier.clickable { onDismissSeedTip() })
                            }
                        }
                    }
                    GridOf(sortedSeeds.size) { i ->
                        Box(modifier = Modifier.clickable { selectedSeedSpecies = sortedSeeds[i].species }) {
                            QuantityTile(sortedSeeds[i].species, sortedSeeds[i].quantity, apiReady)
                        }
                    }
                }
                if (sortedTools.isNotEmpty()) SubSection("Tools", sortedTools.size) {
                    GridOf(sortedTools.size) { i -> QuantityTile(sortedTools[i].toolId, sortedTools[i].quantity, apiReady) }
                }
                if (sortedEggs.isNotEmpty()) SubSection("Eggs", sortedEggs.size) {
                    GridOf(sortedEggs.size) { i ->
                        Box(modifier = Modifier.clickable { selectedEggId = sortedEggs[i].eggId }) {
                            QuantityTile(sortedEggs[i].eggId, sortedEggs[i].quantity, apiReady)
                        }
                    }
                }
                if (sortedPlants.isNotEmpty()) {
                    val totalPlantsValue = remember(sortedPlants) { sortedPlants.sumOf { it.totalPrice } }
                    SubSection("Plants", sortedPlants.size, extraInfo = if (totalPlantsValue > 0) PriceCalculator.formatPrice(totalPlantsValue) else null) {
                        GridOf(sortedPlants.size) { i -> PlantTile(sortedPlants[i], apiReady) }
                    }
                }
                if (sortedProduce.isNotEmpty()) {
                    val totalProduceValue = remember(sortedProduce, apiReady) {
                        sortedProduce.sumOf { p ->
                            PriceCalculator.calculateCropSellPrice(p.species, p.scale, p.mutations) ?: 0L
                        }
                    }
                    SubSection("Produce", sortedProduce.size, extraInfo = if (totalProduceValue > 0) PriceCalculator.formatPrice(totalProduceValue) else null) {
                        GridOf(sortedProduce.size) { i -> ProduceTile(sortedProduce[i], apiReady) }
                    }
                }
                if (sortedDecors.isNotEmpty()) SubSection("Decors", sortedDecors.size) {
                    GridOf(sortedDecors.size) { i -> QuantityTile(sortedDecors[i].decorId, sortedDecors[i].quantity, apiReady) }
                }
                if (sortedPets.isNotEmpty()) SubSection("Pets", sortedPets.size) {
                    PetsList(sortedPets, apiReady)
                }
            }
        }
    }

    // Seed detail dialog — look up live data from inventory so quantity updates in real-time
    selectedSeedSpecies?.let { species ->
        val liveSeed = inventory.seeds.find { it.species == species }
        if (liveSeed != null) {
            SeedDetailDialog(
                seed = liveSeed,
                apiReady = apiReady,
                freePlantTiles = freePlantTiles,
                onPlantSeed = { onPlantSeed(species) },
                onDismiss = { selectedSeedSpecies = null },
            )
        } else {
            // Seed was fully consumed — close dialog
            selectedSeedSpecies = null
        }
    }

    // Egg detail dialog — live lookup
    selectedEggId?.let { eggId ->
        val liveEgg = inventory.eggs.find { it.eggId == eggId }
        if (liveEgg != null) {
            EggGrowDialog(
                egg = liveEgg,
                apiReady = apiReady,
                freePlantTiles = freePlantTiles,
                onGrowEgg = { onGrowEgg(eggId) },
                onDismiss = { selectedEggId = null },
            )
        } else {
            selectedEggId = null
        }
    }
}

// ── Sub-section with toggle ──

@Composable
private fun SubSection(label: String, count: Int, extraInfo: String? = null, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(label) { mutableStateOf(true) }

    HorizontalDivider(color = SurfaceBorder.copy(0.5f), thickness = 0.5.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.weight(1f))
        if (extraInfo != null) {
            Text(extraInfo, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD700))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text("$count", fontSize = 11.sp, color = TextMuted)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            Icons.Default.ExpandMore, contentDescription = null,
            tint = TextMuted, modifier = Modifier.rotate(if (expanded) 0f else -90f),
        )
    }
    if (expanded) {
        content()
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// ── Quantity tile (seeds, eggs, tools, decors) ──

@Composable
private fun QuantityTile(itemId: String, quantity: Int, apiReady: Boolean) {
    val entry = remember(itemId, apiReady) { MgApi.findItem(itemId) }
    val name = entry?.name ?: itemId
    val sprite = entry?.sprite
    val color = rarityColor(entry?.rarity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(url = sprite, size = 28.dp, contentDescription = name)
        Spacer(modifier = Modifier.height(2.dp))
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        Text(fmtQty(quantity), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Accent, lineHeight = 11.sp)
    }
}

// ── Produce tile ──

@Composable
private fun ProduceTile(item: InventoryProduceItem, apiReady: Boolean) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val color = rarityColor(entry?.rarity)
    val maxS = entry?.maxScale ?: 1.0
    val pct = sizePercent(item.scale, maxS)
    val fraction = (pct / 100.0).toFloat().coerceIn(0f, 1f)
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val price = remember(item.species, item.scale, item.mutations, apiReady) {
        PriceCalculator.calculateCropSellPrice(item.species, item.scale, item.mutations)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth().aspectRatio(0.85f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        SpriteImage(url = entry?.cropSprite, size = 28.dp, contentDescription = name)
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(0.15f))) {
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(color.copy(0.8f)))
            }
            Spacer(Modifier.width(3.dp))
            Text("${pct.toInt()}%", fontSize = 7.sp, color = TextSecondary, fontWeight = FontWeight.Medium, lineHeight = 8.sp)
        }
        if (price != null) {
            Text(PriceCalculator.formatPrice(price), fontSize = 8.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700), lineHeight = 10.sp)
        }
        if (item.mutations.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                sortMutations(item.mutations).take(4).forEach { SpriteImage(url = mutationSpriteUrl(it), size = 16.dp, contentDescription = it) }
            }
        }
    }
}

// ── Plant tile ──

@Composable
private fun PlantTile(item: InventoryPlantItem, apiReady: Boolean) {
    val entry = remember(item.species, apiReady) { MgApi.findItem(item.species) }
    val name = entry?.name?.removeSuffix(" Seed") ?: item.species
    val color = rarityColor(entry?.rarity)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (item.totalPrice > 0) 0.85f else 1f)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, color.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .background(SurfaceDark)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpriteImage(url = entry?.cropSprite, size = 28.dp, contentDescription = name)
        Spacer(modifier = Modifier.height(2.dp))
        Text(name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp)
        Text("${item.growSlots} slots", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Accent, lineHeight = 11.sp)
        if (item.totalPrice > 0) {
            Text(PriceCalculator.formatPrice(item.totalPrice), fontSize = 8.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700), lineHeight = 10.sp)
        }
    }
}

// ── Pets grid (compact tiles, same style as pet selector) ──

@Composable
private fun PetsList(pets: List<InventoryPetItem>, apiReady: Boolean) {
    GridOf(count = pets.size) { i ->
        PetTile(pets[i], apiReady)
    }
}

@Composable
private fun PetTile(pet: InventoryPetItem, apiReady: Boolean) {
    val entry = remember(pet.petSpecies, apiReady) { MgApi.findPet(pet.petSpecies) }
    val name = pet.name?.ifBlank { null } ?: entry?.name ?: pet.petSpecies
    val borderColor = rarityColor(entry?.rarity).copy(alpha = 0.5f)
    val ms = maxStr(pet.petSpecies, pet.targetScale)
    val cs = curStr(pet.petSpecies, pet.xp, ms)
    val isMax = cs >= ms

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .background(SurfaceDark),
    ) {
        // Mutation icons top-left
        if (pet.mutations.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                sortMutations(pet.mutations).take(2).forEach {
                    SpriteImage(url = mutationSpriteUrl(it), size = 12.dp, contentDescription = it)
                }
            }
        }
        // STR top-right
        if (ms > 0) {
            val strText = if (isMax) "$cs" else "$cs/$ms"
            val strColor = if (isMax) Color(0xFFFBBF24) else Accent
            Text(
                strText, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                color = strColor, lineHeight = 9.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
            )
        }
        // Center content
        Column(
            modifier = Modifier.align(Alignment.Center).padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpriteImage(category = "pets", name = pet.petSpecies, size = 28.dp, contentDescription = pet.petSpecies)
            Text(
                name, fontSize = 8.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 10.sp,
            )
            if (pet.abilities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    pet.abilities.forEach { abilityId ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(abilityColor(abilityId)),
                        )
                    }
                }
            }
        }
    }
}

private fun abilityColor(abilityId: String): Color {
    val id = abilityId.lowercase().replace(Regex("[\\s_-]+"), "")
    return when {
        id.startsWith("moonkisser") -> Color(0xFFFAA623)
        id.startsWith("dawnkisser") -> Color(0xFFA25CF2)
        id.startsWith("producescaleboost") || id.startsWith("snowycropsizeboost") -> Color(0xFF228B22)
        id.startsWith("plantgrowthboost") || id.startsWith("snowyplantgrowthboost") ||
            id.startsWith("dawnplantgrowthboost") || id.startsWith("amberplantgrowthboost") -> Color(0xFF008080)
        id.startsWith("egggrowthboost") || id.startsWith("snowyegggrowthboost") -> Color(0xFFB45AF0)
        id.startsWith("petageboost") -> Color(0xFF9370DB)
        id.startsWith("pethatchsizeboost") -> Color(0xFF800080)
        id.startsWith("petxpboost") || id.startsWith("snowypetxpboost") -> Color(0xFF1E90FF)
        id.startsWith("hungerboost") || id.startsWith("snowyhungerboost") -> Color(0xFFFF1493)
        id.startsWith("hungerrestore") || id.startsWith("snowyhungerrestore") -> Color(0xFFFF69B4)
        id.startsWith("sellboost") -> Color(0xFFDC143C)
        id.startsWith("coinfinder") || id.startsWith("snowycoinfinder") -> Color(0xFFB49600)
        id.startsWith("seedfinder") -> Color(0xFFA86626)
        id.startsWith("producemutationboost") || id.startsWith("snowycropmutationboost") ||
            id.startsWith("dawnboost") || id.startsWith("ambermoonboost") -> Color(0xFF8C0F46)
        id.startsWith("petmutationboost") -> Color(0xFFA03264)
        id.startsWith("doubleharvest") -> Color(0xFF0078B4)
        id.startsWith("doublehatch") -> Color(0xFF3C5AB4)
        id.startsWith("produceeater") -> Color(0xFFFF4500)
        id.startsWith("producerefund") -> Color(0xFFFF6347)
        id.startsWith("petrefund") -> Color(0xFF005078)
        id.startsWith("copycat") -> Color(0xFFFF8C00)
        id.startsWith("goldgranter") -> Color(0xFFE1C837)
        id.startsWith("rainbowgranter") -> Color(0xFF50AAAA)
        id.startsWith("raindance") -> Color(0xFF4CCCCC)
        id.startsWith("snowgranter") -> Color(0xFF90B8CC)
        id.startsWith("frostgranter") -> Color(0xFF94A0CC)
        id.startsWith("dawnlitgranter") -> Color(0xFFC47CB4)
        id.startsWith("amberlitgranter") -> Color(0xFFCC9060)
        else -> Color(0xFF646464)
    }
}

// ── Egg grow dialog ──

@Composable
private fun EggGrowDialog(
    egg: InventoryEggItem,
    apiReady: Boolean,
    freePlantTiles: Int,
    onGrowEgg: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(egg.eggId, apiReady) { MgApi.findItem(egg.eggId) }
    val name = entry?.name ?: egg.eggId
    val color = rarityColor(entry?.rarity)
    val canGrow = freePlantTiles > 0

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpriteImage(url = entry?.sprite, size = 56.dp, contentDescription = name)

            Spacer(modifier = Modifier.height(10.dp))

            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)

            if (entry?.rarity != null) {
                Text(entry.rarity, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Quantity", fontSize = 12.sp, color = TextSecondary)
                    Text(fmtQty(egg.quantity), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Free tiles", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        "$freePlantTiles",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canGrow) StatusConnected else Color(0xFFEF4444),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onGrowEgg,
                enabled = canGrow,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B5CF6),
                    disabledContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    if (canGrow) "Grow Egg" else "No free tiles",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canGrow) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ── Seed detail dialog ──

@Composable
private fun SeedDetailDialog(
    seed: InventorySeedItem,
    apiReady: Boolean,
    freePlantTiles: Int,
    onPlantSeed: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entry = remember(seed.species, apiReady) { MgApi.findItem(seed.species) }
    val name = entry?.name ?: seed.species
    val color = rarityColor(entry?.rarity)
    val canPlant = freePlantTiles > 0

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Sprite
            SpriteImage(url = entry?.sprite, size = 56.dp, contentDescription = name)

            Spacer(modifier = Modifier.height(10.dp))

            // Name
            Text(
                name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            // Rarity
            if (entry?.rarity != null) {
                Text(
                    entry.rarity,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Details
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Quantity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Quantity", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        fmtQty(seed.quantity),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                }

                // Free tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Free tiles", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        "$freePlantTiles",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canPlant) StatusConnected else Color(0xFFEF4444),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Plant seed button
            Button(
                onClick = onPlantSeed,
                enabled = canPlant,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StatusConnected,
                    disabledContainerColor = StatusConnected.copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.4f),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    if (canPlant) "Plant Seed" else "No free tiles",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canPlant) Color.White else Color.White.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ── Adaptive grid ──

@Composable
private fun GridOf(count: Int, content: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = ((maxWidth + TILE_SPACING) / (TILE_MIN_WIDTH + TILE_SPACING)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
            (0 until count).chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
                    row.forEach { i -> Box(Modifier.weight(1f)) { content(i) } }
                    repeat(cols - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}
