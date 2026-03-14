package axion.common.math

import net.minecraft.util.math.Direction

data class AxisOffset(
    val axis: Direction.Axis,
    val amount: Int,
)
