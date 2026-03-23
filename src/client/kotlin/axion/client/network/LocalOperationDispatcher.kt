package axion.client.network

import axion.AxionMod
import axion.client.history.HistoryManager
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
            MinecraftClient.getInstance().player?.sendMessage(Text.literal(validator.lastFailureMessage ?: "Axion edit canceled."), false)
            return
        }

        val client = MinecraftClient.getInstance()
        val server = client.server
        val worldKey = client.world?.registryKey
        if (server == null) {
            AxionMod.LOGGER.warn("Dropping operation {} because no integrated server is available", operation.kind)
            return
        }

        server.execute {
            val targetWorld = server.getWorld(worldKey)
            if (targetWorld == null) {
                AxionMod.LOGGER.warn("Dropping operation {} because no integrated server world is available", operation.kind)
                return@execute
            }

            val plan = planner.plan(targetWorld, operation)
            if (plan.writes.isEmpty() && plan.entityMoves.isEmpty() && plan.entityClones.isEmpty() && plan.entityDeletes.isEmpty()) {
                return@execute
            }

            HistoryManager.record(targetWorld, plan)
            applier.apply(targetWorld, plan)
        }
    }
}
