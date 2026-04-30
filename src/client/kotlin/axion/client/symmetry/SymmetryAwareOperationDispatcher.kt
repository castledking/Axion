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

        // Construct mode: expand operations across all symmetry transforms
        val config = ActiveSymmetryConfig.current()
        val expandedOperation = if (config?.constructEnabled == true && ActiveSymmetryConfig.hasDerivedTransforms(config)) {
            val expanded = SymmetryOperationExpander.expand(operation, config)
            when (expanded.size) {
                0 -> return
                1 -> expanded.first()
                else -> axion.common.operation.CompositeOperation(expanded)
            }
        } else {
            operation
        }

        val client = MinecraftClient.getInstance()
        val server = client.server
        if (server == null) {
            networkDispatcher.dispatch(expandedOperation)
            return
        }

        val worldKey = client.world?.registryKey
        server.execute {
            val targetWorld = server.getWorld(worldKey)
            if (targetWorld == null) {
                AxionMod.LOGGER.warn("Dropping operation {} because no local world is available", expandedOperation.kind)
                return@execute
            }

            val plan = planner.plan(targetWorld, expandedOperation)
            if (plan.writes.isEmpty() && plan.entityMoves.isEmpty() && plan.entityClones.isEmpty() && plan.entityDeletes.isEmpty()) {
                return@execute
            }

            if (recordHistory) {
                HistoryManager.record(targetWorld, plan)
            }
            applier.apply(targetWorld, plan)
        }
    }
}
