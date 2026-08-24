package axion.common.operation

import axion.common.model.BlockRegion
import net.minecraft.core.BlockPos

data class FilteredCloneRegionOperation(
    val sourceRegion: BlockRegion,
    val destinationOrigin: BlockPos,
    val copyAir: Boolean,
    val keepExisting: Boolean,
) : EditOperation {
    override val kind: String = "filtered_clone_region"
}
