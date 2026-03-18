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
import net.minecraft.text.Text

class SymmetryAwareOperationDispatcher(
    private val recordHistory: Boolean = true,
) : OperationDispatcher {
    private val validator = PermissiveOperationValidator()
    private val planner = LocalWritePlanner()
    private val applier = LocalOperationApplier()
    private val networkDispatcher = NetworkOperationDispatcher(recordHistory = recordHistory)

    override fun dispatch(operation: EditOperation) {
        if (!validator.validate(operation)) {
            MinecraftClient.getInstance().player?.sendMessage(Text.literal(validator.lastFailureMessage ?: "Axion edit canceled."), false)
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

        if (recordHistory) {
            HistoryManager.record(targetWorld, plan.label, plan.writes)
        }
        applier.apply(targetWorld, plan)
    }
}
