package axion.common.model

import net.minecraft.core.Direction

enum class RegionFace(
    val direction: Direction,
) {
    DOWN(Direction.DOWN),
    UP(Direction.UP),
    NORTH(Direction.NORTH),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST),
    EAST(Direction.EAST),
}
