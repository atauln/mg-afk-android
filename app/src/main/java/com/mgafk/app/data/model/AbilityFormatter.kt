package com.mgafk.app.data.model

/**
 * Format pet ability activity logs into human-readable descriptions.
 * Ported from Gemini's abilityFormatter.ts.
 */
object AbilityFormatter {

    /**
     * Format an AbilityLog into a human-readable description based on its action and params.
     * Returns null if no meaningful description can be generated.
     */
    fun format(log: AbilityLog): String? {
        val p = log.params
        return when (log.action) {
            // Coin Finders
            "CoinFinderI", "CoinFinderII", "CoinFinderIII" -> {
                val coins = p["coinsFound"] ?: return null
                "Found $coins coins"
            }

            // Seed Finders
            "SeedFinderI", "SeedFinderII", "SeedFinderIII", "SeedFinderIV" -> {
                val species = p["speciesId"] ?: "Unknown"
                "Found 1× $species seed"
            }

            // Hunger Restore
            "HungerRestore", "HungerRestoreII" -> {
                val amount = p["hungerRestoreAmount"] ?: return null
                val targetName = p["targetPetName"] ?: "Unknown"
                val isSelf = p["targetPetId"] == p["petId"]
                val target = if (isSelf) "itself" else targetName
                "Restored $amount hunger to $target"
            }

            // Double Harvest
            "DoubleHarvest" -> {
                val crop = p["harvestedCropSpecies"] ?: "Unknown"
                "Double harvested $crop"
            }

            // Double Hatch
            "DoubleHatch" -> {
                val species = p["extraPetSpecies"] ?: "Unknown"
                "Double hatched $species"
            }

            // Produce Eater
            "ProduceEater" -> {
                val crop = p["growSlotSpecies"] ?: "Unknown"
                val price = p["sellPrice"] ?: "0"
                "Ate $crop for $price coins"
            }

            // Pet Hatch Size Boost
            "PetHatchSizeBoost", "PetHatchSizeBoostII" -> {
                val target = p["targetPetName"] ?: "Unknown"
                val increase = p["strengthIncrease"]?.toDoubleOrNull()
                val formatted = if (increase != null) increase.toInt().toString() else (p["strengthIncrease"] ?: "?")
                "Boosted $target's size by +$formatted"
            }

            // Pet Age Boost
            "PetAgeBoost", "PetAgeBoostII" -> {
                val target = p["targetPetName"] ?: "Unknown"
                val xp = p["bonusXp"] ?: "0"
                "Gave +$xp XP to $target"
            }

            // Pet Refund
            "PetRefund", "PetRefundII" -> {
                val eggId = p["eggId"] ?: "Unknown Egg"
                "Refunded 1× $eggId"
            }

            // Produce Refund
            "ProduceRefund" -> {
                val count = p["cropsRefundedCount"] ?: "0"
                val label = if (count == "1") "crop" else "crops"
                "Refunded $count $label"
            }

            // Sell Boost
            "SellBoostI", "SellBoostII", "SellBoostIII", "SellBoostIV" -> {
                val bonus = p["bonusCoins"] ?: "0"
                "Gave +$bonus bonus coins"
            }

            // Gold / Rainbow / Rain / Snow
            "GoldGranter", "RainbowGranter", "RainDance", "SnowGranter" -> {
                val mutation = p["mutation"] ?: "Unknown"
                val crop = p["growSlotSpecies"] ?: "Unknown"
                "Made $crop turn $mutation"
            }

            // Pet XP Boost
            "PetXpBoost", "PetXpBoostII", "SnowyPetXpBoost" -> {
                val xp = p["bonusXp"] ?: "0"
                val count = p["petsAffectedCount"] ?: "0"
                val label = if (count == "1") "pet" else "pets"
                "Gave +$xp XP to $count $label"
            }

            // Egg Growth Boost
            "EggGrowthBoost", "EggGrowthBoostII", "EggGrowthBoostII_NEW" -> {
                val seconds = p["secondsReduced"]?.toDoubleOrNull()?.toInt() ?: 0
                val count = p["eggsAffectedCount"] ?: "0"
                val label = if (count == "1") "egg" else "eggs"
                "Reduced $count $label growth by ${formatTime(seconds)}"
            }

            // Plant Growth Boost
            "PlantGrowthBoost", "PlantGrowthBoostII", "SnowyPlantGrowthBoost" -> {
                val seconds = p["secondsReduced"]?.toDoubleOrNull()?.toInt() ?: 0
                val count = p["numPlantsAffected"] ?: "0"
                val label = if (count == "1") "plant" else "plants"
                "Reduced $count $label growth by ${formatTime(seconds)}"
            }

            // Produce Scale Boost
            "ProduceScaleBoost", "ProduceScaleBoostII" -> {
                val pct = p["scaleIncreasePercentage"]?.toDoubleOrNull()
                val formatted = if (pct != null) pct.toInt().toString() else (p["scaleIncreasePercentage"] ?: "?")
                val count = p["numPlantsAffected"] ?: "0"
                val label = if (count == "1") "crop" else "crops"
                "Boosted $count $label size by +$formatted%"
            }

            // Pet Mutation Boost
            "PetMutationBoost", "PetMutationBoostII" -> {
                val target = p["targetPetName"] ?: "Unknown"
                val mutation = p["mutation"] ?: "Unknown"
                "Gave $mutation mutation to $target"
            }

            else -> null
        }
    }

    private fun formatTime(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }
}
