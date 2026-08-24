package axion.common.model

import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

data class SymmetryAnchor(
    val position: Vec3,
    val face: Direction? = null,
)
