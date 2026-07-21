package axion.client.symmetry

import axion.AxionMod
import axion.common.compat.VersionCompat
import axion.client.history.HistoryManager
import axion.client.network.LocalOperationApplier
import axion.client.network.LocalWritePlanner
import axion.client.network.NetworkOperationDispatcher
import axion.client.network.PermissiveOperationValidator
import axion.common.operation.EditOperation
import axion.common.operation.OperationDispatcher
import axion.protocol.AxionInteractionOrigin
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

class SymmetryAwareOperationDispatcher(
    private val recordHistory: Boolean = true,
    interactionOrigin: AxionInteractionOrigin = AxionInteractionOrigin.NONE,
) : OperationDispatcher {
    private val validator = PermissiveOperationValidator()
    private val planner = LocalWritePlanner()
    private val applier = LocalOperationApplier()
    private val networkDispatcher = NetworkOperationDispatcher(
        recordHistory = recordHistory,
        interactionOrigin = interactionOrigin,
    )

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
        val server = VersionCompat.INSTANCE.clientGetServer(client)
        if (server == null) {
            networkDispatcher.dispatch(expandedOperation)
            return
        }

        val worldKey = VersionCompat.INSTANCE.clientGetWorldRegistryKey(client)
        VersionCompat.INSTANCE.serverExecute(server) {
            val targetWorld = VersionCompat.INSTANCE.serverGetWorld(server, worldKey ?: return@serverExecute)
            if (targetWorld == null) {
                AxionMod.LOGGER.warn("Dropping operation {} because no local world is available", expandedOperation.kind)
                return@serverExecute
            }

            val plan = planner.plan(targetWorld as net.minecraft.world.World, expandedOperation)
            if (plan.writes.isEmpty() && plan.entityMoves.isEmpty() && plan.entityClones.isEmpty() && plan.entityDeletes.isEmpty()) {
                return@serverExecute
            }

            if (recordHistory) {
                HistoryManager.record(targetWorld, plan)
            }
            applier.apply(targetWorld, plan)
        }
    }
}
