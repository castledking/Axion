package axion.client.network

import axion.common.operation.EditOperation
import axion.common.operation.OperationApplier
import net.minecraft.world.level.Level

class LocalOperationApplier : OperationApplier {
    override fun apply(world: Level, operation: EditOperation) {
        val planner = LocalWritePlanner()
        apply(world, planner.plan(world, operation))
    }

    fun apply(world: Level, plan: WritePlan, suppressBlockUpdates: Boolean = true) {
        plan.writes.forEach { write ->
            BlockEntitySnapshotService.apply(world, write, suppressBlockUpdates)
        }
        if (plan.entityDeletes.isNotEmpty()) {
            LocalEntityDeleteService.apply(world, plan.entityDeletes)
        }
        if (plan.entityMoves.isNotEmpty()) {
            LocalEntityMoveService.apply(world, plan.entityMoves)
        }
        if (plan.entityClones.isNotEmpty()) {
            LocalEntityCloneService.apply(world, plan.entityClones)
        }
    }
}
