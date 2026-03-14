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

        val config = ActiveSymmetryConfig.current()
        val expandedOperation = SymmetryOperationExpander.expand(operation, config).let { expanded ->
            when (expanded.size) {
                0 -> null
                1 -> expanded.first()
                else -> axion.common.operation.CompositeOperation(expanded)
            }
        } ?: return

        val basePlan = planner.plan(targetWorld, expandedOperation)
        val plan = if (operation is axion.common.operation.ExtrudeOperation) {
            SymmetryWritePlanExpander.expand(basePlan, config)
        } else {
            basePlan
        }

        if (plan.writes.isEmpty()) {
            return
        }

        HistoryManager.record(targetWorld, plan.label, plan.writes)
        applier.apply(targetWorld, plan)
    }
}
