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

    companion object {
        val toolbarOrder: List<AxionSubtool> = listOf(
            MOVE,
            CLONE,
            STACK,
            SMEAR,
            EXTRUDE,
            ERASE,
            SETUP_SYMMETRY,
        )
    }

    fun cycle(step: Int): AxionSubtool {
        val order = toolbarOrder
        val currentIndex = order.indexOf(this).takeIf { it >= 0 } ?: 0
        val target = Math.floorMod(currentIndex + step, order.size)
        return order[target]
    }
}
