package axion.client.tool

/**
 * Radius of the erase tool's right-click connected erase.
 *
 * Kept separate from [MagicSelectionService]'s selection brush: this one reaches
 * further (up to 32) because it commits immediately instead of building a
 * selection the player refines first.
 */
object EraseBrushSize {
    private const val DEFAULT_RADIUS: Int = 8
    private const val MIN_RADIUS: Int = 1
    private const val MAX_RADIUS: Int = 32

    private var currentRadius: Int = DEFAULT_RADIUS

    fun radius(): Int = currentRadius

    fun adjust(scrollAmount: Double): Int? {
        val direction = scrollAmount.compareTo(0.0)
        if (direction == 0) {
            return null
        }
        val next = (currentRadius + direction).coerceIn(MIN_RADIUS, MAX_RADIUS)
        if (next == currentRadius) {
            return null
        }
        currentRadius = next
        return currentRadius
    }
}
