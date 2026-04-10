package com.mgafk.app.ui.components

/** Canonical display order for mutations (lower index = shown first). */
private val MUTATION_ORDER = listOf(
    "Gold", "Rainbow", "Wet", "Chilled", "Frozen",
    "Thunderstruck", "Amberlit", "Amberbound", "Dawnlit", "Dawnbound",
)

private const val MUT_SPRITE_BASE = "https://mg-api.ariedam.fr/assets/sprites/ui/Mutation"
private val MUT_SPRITE_NAME = mapOf("Ambershine" to "Amberlit")

/** Returns the sprite URL for a mutation name. */
fun mutationSpriteUrl(mutation: String): String {
    val spriteName = MUT_SPRITE_NAME[mutation] ?: mutation
    return "$MUT_SPRITE_BASE$spriteName.png"
}

/** Sorts a list of mutation names into the canonical display order. Unknown mutations go last, alphabetically. */
fun sortMutations(mutations: List<String>): List<String> {
    return mutations.sortedWith(compareBy<String> {
        val idx = MUTATION_ORDER.indexOf(it)
        if (idx >= 0) idx else MUTATION_ORDER.size
    }.thenBy { it })
}
