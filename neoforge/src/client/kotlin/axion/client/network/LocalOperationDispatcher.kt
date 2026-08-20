package axion.client.network

import axion.AxionMod
import axion.client.history.HistoryManager
import axion.common.compat.VersionCompat
import axion.common.operation.EditOperation
import axion.common.operation.OperationDispatcher
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

class LocalOperationDispatcher : OperationDispatcher {
    private val validator = PermissiveOperationValidator()
    private val planner = LocalWritePlanner()
    private val applier = LocalOperationApplier()

    override fun dispatch(operation: EditOperation) {
        if (!validator.validate(operation)) {
            val client = MinecraftClient.getInstance()
            client.player?.let { VersionCompat.INSTANCE.playerSendMessage(it, Text.literal(validator.lastFailureMessage ?: "Axion edit canceled."), false) }
            return
        }

        val client = MinecraftClient.getInstance()
        val server = VersionCompat.INSTANCE.clientGetServer(client) ?: return
        val worldKey = VersionCompat.INSTANCE.clientGetWorldRegistryKey(client) ?: return

        VersionCompat.INSTANCE.serverExecute(server, Runnable {
            val targetWorld = VersionCompat.INSTANCE.serverGetWorld(server, worldKey)
            if (targetWorld == null) {
                AxionMod.LOGGER.warn("Dropping operation {} because no integrated server world is available", operation.kind)
                return@Runnable
            }

            val plan = planner.plan(targetWorld as net.minecraft.world.World, operation)
            if (plan.writes.isEmpty() && plan.entityMoves.isEmpty() && plan.entityClones.isEmpty() && plan.entityDeletes.isEmpty()) {
                return@Runnable
            }

            HistoryManager.record(targetWorld, plan)
            applier.apply(targetWorld, plan)
        })
    }
}
