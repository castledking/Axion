package axion.client.network

import axion.common.operation.EditOperation
import axion.common.operation.OperationApplier
import net.minecraft.world.World

class LocalOperationApplier : OperationApplier {
    override fun apply(world: World, operation: EditOperation) {
        val planner = LocalWritePlanner()
        apply(world, planner.plan(world, operation))
    }

    fun apply(world: World, plan: WritePlan) {
        plan.writes.forEach { write ->
            BlockEntitySnapshotService.apply(world, write)
        }
    }
}
