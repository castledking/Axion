package axion.common.operation

import axion.common.model.BlockRegion

data class DeleteEntitiesOperation(
    val sourceRegion: BlockRegion,
) : EditOperation {
    override val kind: String = "delete_entities"
}
