package axion.common.operation

import axion.common.model.BlockRegion

data class ClearRegionOperation(
    val region: BlockRegion,
) : EditOperation {
    override val kind: String = "clear_region"
}
