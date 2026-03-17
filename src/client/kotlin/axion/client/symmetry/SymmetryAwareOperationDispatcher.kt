package axion.client.symmetry

import axion.AxionMod
import axion.client.history.HistoryManager
import axion.client.network.LocalOperationApplier
import axion.client.network.LocalWritePlanner
import axion.client.network.NetworkOperationDispatcher
import axion.client.network.PermissiveOperationValidator
import axion.common.operation.EditOperation
import axion.common.operation.OperationDispatcher
import net.minecraft.client.MinecraftClient

class SymmetryAwareOperationDispatcher : OperationDispatcher {
    private val validator = PermissiveOperationValidator()
    private val planner = LocalWritePlanner()
    private val applier = LocalOperationApplier()
    private val networkDispatcher = NetworkOperationDispatcher()

    override fun dispatch(operation: EditOperation) {
        if (!validator.validate(operation)) {
            return
        }

        val client = MinecraftClient.getInstance()
        if (client.server == null) {
            networkDispatcher.dispatch(operation)
            return
        }

        val serverWorld = client.server?.getWorld(client.world?.registryKey)
        val targetWorld = serverWorld
        if (targetWorld == null) {
            AxionMod.LOGGER.warn("Dropping operation {} because no local world is available", operation.kind)
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
