package axion.common.operation

import axion.common.model.BlockRegion
import net.minecraft.util.math.BlockPos

enum class EntityMoveMirrorAxis {
    NONE,
    X,
    Z,
}

data class MoveEntitiesOperation(
    val sourceRegion: BlockRegion,
    val destinationOrigin: BlockPos,
    val rotationQuarterTurns: Int = 0,
    val mirrorAxis: EntityMoveMirrorAxis = EntityMoveMirrorAxis.NONE,
) : EditOperation {
    override val kind: String = "move_entities"
}
