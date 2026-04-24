package axion.common.operation

import axion.common.model.BlockRegion
import java.util.UUID
import net.minecraft.util.math.BlockPos

data class CloneEntitiesOperation(
    val entityUuids: List<UUID>,
    val sourceRegion: BlockRegion,
    val destinationOrigin: BlockPos,
    val rotationQuarterTurns: Int = 0,
    val mirrorAxis: EntityMoveMirrorAxis = EntityMoveMirrorAxis.NONE,
) : EditOperation {
    override val kind: String = "clone_entities"
}
