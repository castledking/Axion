package axion.common.operation

import axion.common.model.BlockRegion
import net.minecraft.util.math.BlockPos

data class CloneRegionOperation(
    val sourceRegion: BlockRegion,
    val destinationOrigin: BlockPos,
) : EditOperation {
    override val kind: String = "clone_region"
}
