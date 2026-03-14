package axion.client.network

import axion.AxionMod
import axion.client.history.HistoryManager
import axion.common.operation.EditOperation
import axion.common.operation.OperationDispatcher
import net.minecraft.client.MinecraftClient

class LocalOperationDispatcher : OperationDispatcher {
    private val validator = PermissiveOperationValidator()
    private val planner = LocalWritePlanner()
    private val applier = LocalOperationApplier()

    override fun dispatch(operation: EditOperation) {
        if (!validator.validate(operation)) {
            return
        }

        val client = MinecraftClient.getInstance()
        val serverWorld = client.server?.getWorld(client.world?.registryKey)
        val targetWorld = serverWorld

        if (targetWorld == null) {
            AxionMod.LOGGER.warn("Dropping operation {} because no integrated server world is available", operation.kind)
            return
        }

        val plan = planner.plan(targetWorld, operation)
        if (plan.writes.isEmpty()) {
            return
        }

        HistoryManager.record(targetWorld, plan.label, plan.writes)
        applier.apply(targetWorld, plan)
    }
}
