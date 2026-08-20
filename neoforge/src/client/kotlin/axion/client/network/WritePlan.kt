package axion.client.network

import axion.common.history.EntityCloneChange

data class WritePlan(
    val label: String,
    val writes: List<BlockWrite>,
    val entityMoves: List<EntityMovePlan> = emptyList(),
    val entityClones: List<EntityCloneChange> = emptyList(),
    val entityDeletes: List<EntityCloneChange> = emptyList(),
)
