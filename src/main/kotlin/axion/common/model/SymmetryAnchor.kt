package axion.common.model

import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

data class SymmetryAnchor(
    val position: Vec3d,
    val face: Direction? = null,
)
