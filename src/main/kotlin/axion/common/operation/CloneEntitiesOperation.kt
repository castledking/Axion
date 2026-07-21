package axion.common.operation

import axion.common.model.BlockRegion
import axion.protocol.EntitySelectionMask
import net.minecraft.util.math.BlockPos

data class CloneEntitiesOperation(
    val entitySelection: EntitySelectionMask,
    val sourceRegion: BlockRegion,
    val destinationOrigin: BlockPos,
    val rotationQuarterTurns: Int = 0,
    val mirrorAxis: EntityMoveMirrorAxis = EntityMoveMirrorAxis.NONE,
) : EditOperation {
    override val kind: String = "clone_entities"
}
