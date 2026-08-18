package axion.server.paper

import axion.protocol.AxionInteractionOrigin
import axion.protocol.AxionResultCode
import axion.protocol.AxionResultSource
import axion.protocol.IntVector3
import net.minecraft.core.BlockPos
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.craftbukkit.CraftWorld
import org.bukkit.craftbukkit.block.data.CraftBlockData
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.EquipmentSlot

/**
 * Presents remote infinite-reach writes to Bukkit protection plugins as ordinary
 * player interactions. The caller owns transaction rollback when an event denies
 * a write.
 */
internal class PaperInteractionEventGateway(
    private val callEvent: (Event) -> Unit = { event -> Bukkit.getPluginManager().callEvent(event) },
    private val noUpdatesService: AxionNoUpdatesService = AxionNoUpdatesService(),
) {
    fun applyClear(
        player: Player,
        world: World,
        pos: IntVector3,
        origin: AxionInteractionOrigin,
        suppressUpdates: Boolean = true,
    ) {
        val quiet = suppressesUpdates(player, suppressUpdates)
        applyChange(
            player = player,
            world = world,
            pos = pos,
            targetState = "minecraft:air",
            targetBlockEntityData = null,
            origin = origin,
            suppressUpdates = quiet,
        ) {
            PaperBlockWritePolicy.setType(
                world.getBlockAt(pos.x, pos.y, pos.z),
                org.bukkit.Material.AIR,
                applyPhysics = !quiet,
            )
        }
    }

    fun applyPlacement(
        player: Player,
        world: World,
        pos: IntVector3,
        blockState: String,
        blockEntityData: String?,
        origin: AxionInteractionOrigin,
        suppressUpdates: Boolean = true,
    ) {
        val quiet = suppressesUpdates(player, suppressUpdates)
        applyChange(
            player = player,
            world = world,
            pos = pos,
            targetState = blockState,
            targetBlockEntityData = blockEntityData,
            origin = origin,
            suppressUpdates = quiet,
        ) {
            PaperBlockEntitySnapshotService.apply(
                world = world,
                pos = BlockPos(pos.x, pos.y, pos.z),
                blockStateString = blockState,
                blockEntityPayload = blockEntityData,
                suppressUpdates = quiet,
            )
        }
    }

    /**
     * A batch says whether it wants updates, but a player with No Updates armed
     * never gets them — a stale or forged flag cannot re-enable physics for them.
     */
    private fun suppressesUpdates(player: Player, requested: Boolean): Boolean =
        requested || noUpdatesService.isArmed(player)

    private fun applyChange(
        player: Player,
        world: World,
        pos: IntVector3,
        targetState: String,
        targetBlockEntityData: String?,
        origin: AxionInteractionOrigin,
        suppressUpdates: Boolean,
        applyTarget: () -> Unit,
    ) {
        if (origin != AxionInteractionOrigin.INFINITE_REACH) {
            applyTarget()
            return
        }

        val block = world.getBlockAt(pos.x, pos.y, pos.z)
        val replacedState = block.state
        val currentState = block.blockData.getAsString(false)
        val currentBlockEntityData = PaperBlockEntitySnapshotService.capture(
            world,
            BlockPos(pos.x, pos.y, pos.z),
        )
        val targetData = Bukkit.createBlockData(targetState)
        val stateChanged = currentState != targetData.getAsString(false) ||
            currentBlockEntityData != targetBlockEntityData
        val eventKinds = PaperInteractionEventPolicy.eventKinds(
            origin = origin,
            stateChanged = stateChanged,
            oldIsAir = block.type.isAir,
            newIsAir = targetData.material.isAir,
        )
        if (eventKinds.isEmpty()) {
            return
        }

        if (PaperInteractionEventKind.BREAK in eventKinds) {
            val breakEvent = BlockBreakEvent(block, player)
            callEvent(breakEvent)
            if (breakEvent.isCancelled) {
                deny("Block break was denied by a server plugin", pos)
            }
        }

        if (suppressUpdates) {
            nmsWrite(world, pos, targetState)
        } else {
            applyTarget()
        }

        if (PaperInteractionEventKind.PLACE in eventKinds) {
            // The remote protocol identifies the destination but not the clicked support face.
            // Use the destination as the best available block-against value; protection plugins
            // still receive the exact player, world, destination, old state, new state, and hand.
            val placeEvent = BlockPlaceEvent(
                block,
                replacedState,
                block,
                player.inventory.itemInMainHand.clone(),
                player,
                true,
                EquipmentSlot.HAND,
            )
            callEvent(placeEvent)
            if (placeEvent.isCancelled || !placeEvent.canBuild()) {
                deny("Block placement was denied by a server plugin", pos)
            }
        }
    }

    private fun nmsWrite(world: World, pos: IntVector3, targetState: String) {
        val level = (world as CraftWorld).handle
        val blockPos = BlockPos(pos.x, pos.y, pos.z)
        val nmsState = (Bukkit.createBlockData(targetState) as CraftBlockData).state
        level.setBlock(blockPos, nmsState, 62)
    }

    private fun deny(message: String, pos: IntVector3): Nothing {
        throw PaperInteractionDeniedException(
            AxionRejection(
                code = AxionResultCode.PROTECTED_REGION,
                source = AxionResultSource.SERVER,
                message = message,
                blockedPosition = pos,
            ),
        )
    }
}
