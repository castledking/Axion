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
        val server = client.server
        if (server == null) {
            networkDispatcher.dispatch(operation)
            return
        }

        val worldKey = client.world?.registryKey
        server.execute {
            val targetWorld = server.getWorld(worldKey)
            if (targetWorld == null) {
                AxionMod.LOGGER.warn("Dropping operation {} because no local world is available", operation.kind)
                return@execute
            }

            val plan = planner.plan(targetWorld, operation)
            if (plan.writes.isEmpty() && plan.entityMoves.isEmpty()) {
                return@execute
            }

            if (recordHistory) {
                HistoryManager.record(targetWorld, plan)
            }
            applier.apply(targetWorld, plan)
        }
    }
}
