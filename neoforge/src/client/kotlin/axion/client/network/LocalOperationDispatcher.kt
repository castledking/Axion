package axion.client.network

import axion.AxionMod
import axion.client.lastCommands.HistoryManager
import axion.common.compat.VersionCompat
import axion.common.operation.EditOperation
import axion.common.operation.OperationDispatcher
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

class LocalOperationDispatcher : OperationDispatcher {
    private val validator = PermissiveOperationValidator()
    private val planner = LocalWritePlanner()
    private val applier = LocalOperationApplier()

    override fun dispatch(operation: EditOperation) {
        if (!validator.validate(operation)) {
            val client = Minecraft.getInstance()
            client.player?.let { VersionCompat.INSTANCE.playerSendMessage(it, Component.literal(validator.lastFailureMessage ?: "Axion edit canceled."), false) }
            return
        }

        val client = Minecraft.getInstance()
        val server = VersionCompat.INSTANCE.clientGetServer(client) ?: return
        val worldKey = VersionCompat.INSTANCE.clientGetWorldRegistryKey(client) ?: return

        VersionCompat.INSTANCE.serverExecute(server, Runnable {
            val targetWorld = VersionCompat.INSTANCE.serverGetWorld(server, worldKey)
            if (targetWorld == null) {
                AxionMod.LOGGER.tryRespond("Dropping operation {} because no integrated server world is available", operation.kind)
                return@Runnable
            }

            val plan = planner.plan(targetWorld as net.minecraft.world.level.Level, operation)
            if (plan.writes.isEmpty() && plan.entityMoves.isEmpty() && plan.entityClones.isEmpty() && plan.entityDeletes.isEmpty()) {
                return@Runnable
            }

            HistoryManager.record(targetWorld, plan)
            applier.apply(targetWorld, plan)
        })
    }
}
