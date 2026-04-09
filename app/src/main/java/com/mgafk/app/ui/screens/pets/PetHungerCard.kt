package com.mgafk.app.ui.screens.pets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mgafk.app.data.model.InventoryProduceItem
import com.mgafk.app.data.model.PetSnapshot
import com.mgafk.app.data.repository.MgApi
import com.mgafk.app.data.repository.PriceCalculator
import com.mgafk.app.data.websocket.Constants
import com.mgafk.app.ui.components.AppCard
import com.mgafk.app.ui.components.SpriteImage
import com.mgafk.app.ui.theme.Accent
import com.mgafk.app.ui.theme.StatusConnected
import com.mgafk.app.ui.theme.StatusError
import com.mgafk.app.ui.theme.SurfaceCard
import com.mgafk.app.ui.theme.SurfaceDark
import com.mgafk.app.ui.theme.TextMuted
import com.mgafk.app.ui.theme.TextPrimary
import com.mgafk.app.ui.theme.TextSecondary

private val TILE_MIN = 76.dp
private val GAP = 6.dp

private const val MUT_BASE = "https://mg-api.ariedam.fr/assets/sprites/ui/Mutation"
private val MUT_MAP = mapOf("Ambershine" to "Amberlit")
private fun mutUrl(m: String) = "$MUT_BASE${MUT_MAP[m] ?: m}.png"

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

@Composable
fun PetHungerCard(
    pets: List<PetSnapshot>,
    produce: List<InventoryProduceItem> = emptyList(),
    apiReady: Boolean = false,
    onFeedPet: (petItemId: String, cropItemIds: List<String>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier, title = "Pet Hunger", collapsible = true, persistKey = "pets.hunger") {
        if (pets.isEmpty()) {
            Text("No pets found.", fontSize = 12.sp, color = TextMuted)
        } else {
            pets.forEachIndexed { i, pet ->
                PetRow(pet, produce, apiReady, onFeedPet)
                if (i < pets.lastIndex) Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PetRow(
    pet: PetSnapshot,
    produce: List<InventoryProduceItem>,
    apiReady: Boolean,
    onFeedPet: (petItemId: String, cropItemIds: List<String>) -> Unit,
) {
    val maxHunger = Constants.PET_HUNGER_COSTS[pet.species.lowercase()] ?: 1000
    val percent = ((pet.hunger.toFloat() / maxHunger) * 100).coerceIn(0f, 100f)
    val color = when {
        percent < 5f -> StatusError
        percent < 25f -> Color(0xFFFBBF24)
        else -> StatusConnected
    }
    val mutationLabel = if (pet.mutations.isNotEmpty()) pet.mutations.joinToString(", ") else null
    var showFeedPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpriteImage(category = "pets", name = pet.species, size = 28.dp, contentDescription = pet.species)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, false)) {
                    Text(
                        pet.name.ifBlank { pet.species },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (mutationLabel != null) {
                        Text(" · $mutationLabel", fontSize = 11.sp, color = Color(0xFFEBC800))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "%.1f%%".format(percent),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                    Text(
                        text = "Feed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Accent.copy(alpha = 0.12f))
                            .clickable { showFeedPicker = true }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (percent / 100f).coerceIn(0f, 1f))
                        .height(5.dp)
                        .background(color),
                )
            }
        }
    }

    if (showFeedPicker) {
        FeedPetPickerDialog(
            pet = pet,
            produce = produce,
            apiReady = apiReady,
            onConfirm = { selectedIds ->
                showFeedPicker = false
                if (selectedIds.isNotEmpty()) onFeedPet(pet.id, selectedIds)
            },
            onDismiss = { showFeedPicker = false },
        )
    }
}

@Composable
private fun FeedPetPickerDialog(
    pet: PetSnapshot,
    produce: List<InventoryProduceItem>,
    apiReady: Boolean,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val petEntry = remember(pet.species, apiReady) { MgApi.findPet(pet.species) }
    val diet = remember(petEntry) { petEntry?.diet ?: emptyList() }
    val compatible = remember(produce, diet, apiReady) {
        if (diet.isEmpty()) emptyList()
        else produce.filter { it.species in diet }
    }
    var selected by remember { mutableStateOf(setOf<String>()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceCard)
                .padding(16.dp),
        ) {
            Text(
                "Feed ${pet.name.ifBlank { pet.species }}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            if (diet.isNotEmpty()) {
                val dietNames = remember(diet, apiReady) {
                    diet.mapNotNull { MgApi.findItem(it)?.name?.removeSuffix(" Seed") }
                }
                Text(
                    "Diet: ${dietNames.joinToString(", ")}",
                    fontSize = 10.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Text(
                "${selected.size} selected",
                fontSize = 11.sp,
                color = if (selected.isNotEmpty()) StatusConnected else TextMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )

            if (compatible.isEmpty()) {
                Text(
                    "No compatible produce in inventory.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
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
                    items(compatible, key = { it.id }) { item ->
                        val isSelected = item.id in selected
                        FeedProduceTile(
                            item = item,
                            apiReady = apiReady,
                            isSelected = isSelected,
                            onClick = {
                                selected = if (isSelected) selected - item.id else selected + item.id
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
                    onClick = { onConfirm(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) {
                    Text("Feed ${selected.size}", fontSize = 12.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FeedProduceTile(
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
