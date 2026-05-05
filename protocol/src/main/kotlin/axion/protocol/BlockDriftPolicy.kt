package axion.protocol

/**
 * Shared drift-tolerance policy used by both the Fabric and Paper history
 * services when validating that the world still looks like the state we
 * recorded at edit-time.
 *
 * Two mechanisms are exposed:
 *
 *  1. [stripTransientProperties] — normalize a stringified block state by
 *     removing purely connection/physics-derived properties (fence `north`,
 *     stair `shape`, redstone `power`, etc.). After stripping, identical
 *     "identity" states compare equal even when neighbors have shifted.
 *
 *  2. [acceptsDecay] — an allowlist of expected → current block id pairs
 *     that are treated as equivalent for validation. Covers natural vanilla
 *     decay such as grass_block → dirt (light blocked), ice → water (melt),
 *     leaves → air (disconnected from wood), fire → air (burnout), and so on.
 *
 * Both are intentionally string-keyed so they can be called from platform
 * services that otherwise use incompatible block APIs (Bukkit `Material`
 * vs Fabric `Block`).
 */
object BlockDriftPolicy {

    /**
     * Block-state properties that are a pure function of neighbors (or
     * short-lived physics state) and should be ignored when comparing a
     * recorded state to the live world state.
     *
     * The name-level match is deliberately coarse — if a block happens to
     * use one of these names for an identity property, we would lose a
     * little precision, but for vanilla blocks these names are only ever
     * used for connection/derived state.
     */
    val TRANSIENT_PROPERTIES: Set<String> = setOf(
        // Connection sides on fences, walls, glass panes, iron bars,
        // chorus plants, redstone wire, tripwire, fire, etc.
        "north",
        "south",
        "east",
        "west",
        "up",
        "down",
        // Stair / wall shape derived from neighbors.
        "shape",
        // Redstone dust power level (0..15).
        "power",
        // Repeater/comparator "locked from side" flag.
        "locked",
        // Door/button/pressure-plate/repeater/comparator/note block
        // "powered" state — comes from redstone neighbors.
        "powered",
        // Door hinge is auto-computed from the adjacent door.
        "hinge",
        // Chest pairing side (single / left / right).
        "type",
        // Note block instrument and note are recomputed from the block
        // underneath, so a neighbor change flips them without the note
        // block itself being edited.
        "instrument",
        "note",
        // Tripwire / tripwire-hook attachment state.
        "attached",
        "disarmed",
        // Rail connection shape to neighbor rails.
        "rail_shape",
    )

    /**
     * Expected vanilla decay paths: if the world shows `current` where we
     * recorded `expected`, treat them as equivalent.
     *
     * Keep this list additive — adding a pair can only make undo *more*
     * permissive, never less.
     */
    private val DECAY_REGISTRY: Map<String, Set<String>> = mapOf(
        // Dirt-family → dirt. Grass/mycelium/podzol dim when light is
        // blocked; farmland un-hydrates; dirt_path is crushed to dirt when
        // a mob jumps on it.
        "minecraft:grass_block" to setOf("minecraft:dirt"),
        "minecraft:mycelium" to setOf("minecraft:dirt"),
        "minecraft:podzol" to setOf("minecraft:dirt"),
        "minecraft:farmland" to setOf("minecraft:dirt"),
        "minecraft:dirt_path" to setOf("minecraft:dirt"),
        "minecraft:rooted_dirt" to setOf("minecraft:dirt"),
        // Snow melt / shovel-off.
        "minecraft:snow" to setOf("minecraft:air"),
        // Ice / frosted-ice melt to water (or, in some biomes, air).
        "minecraft:ice" to setOf("minecraft:water", "minecraft:air"),
        "minecraft:frosted_ice" to setOf("minecraft:water", "minecraft:air"),
        "minecraft:packed_ice" to setOf("minecraft:water", "minecraft:air"),
        "minecraft:blue_ice" to setOf("minecraft:water", "minecraft:air"),
        // Fire burnout.
        "minecraft:fire" to setOf("minecraft:air"),
        "minecraft:soul_fire" to setOf("minecraft:air"),
        // Torch / lantern falling when support is removed.
        "minecraft:torch" to setOf("minecraft:air"),
        "minecraft:wall_torch" to setOf("minecraft:air"),
        "minecraft:soul_torch" to setOf("minecraft:air"),
        "minecraft:soul_wall_torch" to setOf("minecraft:air"),
        "minecraft:redstone_torch" to setOf("minecraft:air"),
        "minecraft:redstone_wall_torch" to setOf("minecraft:air"),
        "minecraft:lantern" to setOf("minecraft:air"),
        "minecraft:soul_lantern" to setOf("minecraft:air"),
        // Tall grass / ferns / flowers when their support breaks.
        "minecraft:tall_grass" to setOf("minecraft:air"),
        "minecraft:large_fern" to setOf("minecraft:air"),
        // Redstone wire dropped as item when support removed.
        "minecraft:redstone_wire" to setOf("minecraft:air"),
    )

    /**
     * Suffix-keyed decay rules. Avoids having to enumerate every variant
     * of a block family (all *_leaves, all *_sapling, etc.).
     */
    private val DECAY_SUFFIX_RULES: List<Pair<String, Set<String>>> = listOf(
        // Any *_leaves block can decay to air when disconnected from wood.
        "_leaves" to setOf("minecraft:air"),
        // Saplings can grow into tree structure or break when support fails.
        "_sapling" to setOf("minecraft:air"),
        // Carpets / pressure plates / buttons drop when support removed.
        "_carpet" to setOf("minecraft:air"),
        "_pressure_plate" to setOf("minecraft:air"),
        "_button" to setOf("minecraft:air"),
    )

    /**
     * Parse a stringified block state and return a normalized form with
     * every property in [TRANSIENT_PROPERTIES] removed. A state without
     * bracketed properties is returned unchanged.
     *
     * Input examples:
     *  - `"minecraft:stone"`
     *    → `"minecraft:stone"`
     *  - `"minecraft:oak_fence[north=true,south=false,east=false,west=false,waterlogged=false]"`
     *    → `"minecraft:oak_fence[waterlogged=false]"`
     *  - `"minecraft:redstone_wire[east=none,north=up,power=7,south=none,west=side]"`
     *    → `"minecraft:redstone_wire"`
     */
    fun stripTransientProperties(stateString: String): String {
        val openBracket = stateString.indexOf('[')
        if (openBracket < 0) return stateString
        val closeBracket = stateString.lastIndexOf(']')
        if (closeBracket <= openBracket) return stateString

        val blockId = stateString.substring(0, openBracket)
        val propsBody = stateString.substring(openBracket + 1, closeBracket)
        if (propsBody.isEmpty()) return blockId

        val retained = propsBody
            .splitToSequence(',')
            .map { it.trim() }
            .filter { entry ->
                val eq = entry.indexOf('=')
                if (eq <= 0) return@filter false
                val name = entry.substring(0, eq).trim()
                name !in TRANSIENT_PROPERTIES
            }
            .toList()

        return if (retained.isEmpty()) blockId else "$blockId[${retained.joinToString(",")}]"
    }

    /**
     * True when [currentBlockId] is a known decay target of [expectedBlockId]
     * per [DECAY_REGISTRY] or [DECAY_SUFFIX_RULES]. Block ids are compared
     * as lowercase namespaced strings (`"minecraft:grass_block"`).
     */
    fun acceptsDecay(expectedBlockId: String, currentBlockId: String): Boolean {
        DECAY_REGISTRY[expectedBlockId]?.let { allowed ->
            if (currentBlockId in allowed) return true
        }
        for ((suffix, allowed) in DECAY_SUFFIX_RULES) {
            if (expectedBlockId.endsWith(suffix) && currentBlockId in allowed) {
                return true
            }
        }
        return false
    }
}
