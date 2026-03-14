package axion.common.model

enum class AxionSubtool(
    val displayName: String,
    val shortLabel: String,
    val usesRegionSelection: Boolean,
) {
    MOVE("Move", "Mv", true),
    STACK("Stack", "St", true),
    CLONE("Clone", "Cl", true),
    SETUP_SYMMETRY("Symmetry", "Sy", false),
    EXTRUDE("Extrude", "Ex", false),
    SMEAR("Smear", "Sm", true),
    ERASE("Erase", "Er", true);

    fun cycle(step: Int): AxionSubtool {
        val size = entries.size
        val target = Math.floorMod(ordinal + step, size)
        return entries[target]
    }
}
