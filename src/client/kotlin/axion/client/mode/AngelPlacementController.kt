package axion.client.mode

import axion.client.AxionClientState
import axion.client.compat.blockPosOfFloored
import axion.client.config.AxionClientConfig
import axion.client.tool.AxionToolSelectionController
import axion.common.operation.SymmetryBlockPlacement
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemPlacementContext
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext

/**
 * Angel Placement: puts a block in mid air when the crosshair has nothing to
 * build against.
 *
 * The target is recomputed every client tick and published through [currentGhost]
 * so the per-version preview renderer can draw it. Recomputing on tick rather
 * than per frame keeps the ghost stable while the camera micro-jitters, and keeps
 * the render path free of raycasts.
 */
object AngelPlacementController {
    /**
     * A resolved mid-air target: where the block goes and what state it takes.
     */
    data class Ghost(
        val pos: BlockPos,
        val state: BlockState,
    )

    @Volatile
    private var ghost: Ghost? = null

    /** The mid-air target for this tick, or null when Angel has nothing to offer. */
    fun currentGhost(): Ghost? = ghost

    fun onEndTick(client: MinecraftClient) {
        ghost = resolveGhost(client)
    }

    fun clear() {
        ghost = null
    }

    /**
     * Places the mid-air block if Angel currently owns the click.
     *
     * @return true when the click was consumed and vanilla must not run.
     */
    fun consumeSecondaryAction(client: MinecraftClient): Boolean {
        val target = ghost ?: return false
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return false
        }
        val player = client.player ?: return false

        dispatcher.dispatch(
            axion.common.operation.SymmetryPlacementOperation(
                listOf(SymmetryBlockPlacement(target.pos, target.state)),
            ),
        )
        player.swingHand(Hand.MAIN_HAND)
        return true
    }

    private val dispatcher = axion.client.symmetry.SymmetryAwareOperationDispatcher(
        recordHistory = false,
        suppressBlockUpdates = AxionCapabilityPolicy::suppressBlockUpdates,
    )

    private fun resolveGhost(client: MinecraftClient): Ghost? {
        val state = AxionClientState.globalModeState
        if (!state.angelPlacementEnabled) {
            return null
        }
        if (client.currentScreen != null) {
            return null
        }
        val player = client.player ?: return null
        if (!player.isInCreativeMode) {
            return null
        }
        if (AxionToolSelectionController.isAxionSlotActive()) {
            return null
        }
        val world = client.world ?: return null
        val stack = player.getStackInHand(Hand.MAIN_HAND)
        val blockItem = stack.item as? BlockItem ?: return null

        val cameraEntity = client.cameraEntity ?: player
        val origin = cameraEntity.getCameraPosVec(1.0f)
        val look = cameraEntity.getRotationVec(1.0f)

        // Angel only fills the gap when the crosshair finds nothing. Reach the
        // same distance the active capabilities would, so infinite reach really
        // does take the click back for far-away blocks.
        val searchDistance = if (state.infiniteReachEnabled) {
            AxionClientConfig.infiniteReachRange()
        } else {
            AngelPlacementPolicy.GHOST_DISTANCE
        }
        val hit = world.raycast(
            RaycastContext(
                origin,
                origin.add(look.multiply(searchDistance)),
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                cameraEntity,
            ),
        )
        if (!AngelPlacementPolicy.offersMidAirTarget(
                angelPlacementEnabled = true,
                blockTargetPresent = hit.type.name == "BLOCK",
            )
        ) {
            return null
        }

        val ghostCenter = origin.add(look.multiply(AngelPlacementPolicy.GHOST_DISTANCE))
        val pos = blockPosOfFloored(ghostCenter)
        // The ray came back empty, so this is open air. Checking isAir keeps the
        // guard on API that exists in every namespace, unlike isReplaceable.
        if (!world.getBlockState(pos).isAir) {
            return null
        }

        val placementState = midAirPlacementState(
            client = client,
            blockItem = blockItem,
            stack = stack,
            pos = pos,
            look = look,
        ) ?: return null

        return Ghost(pos, placementState)
    }

    /**
     * Resolves the state the block would take when placed into open air.
     *
     * Vanilla derives facing and axis from the clicked face, which does not exist
     * here, so the face the player is looking through stands in for it. That
     * makes stairs, logs and directional blocks orient the way they would if the
     * player had clicked a wall in that spot.
     */
    private fun midAirPlacementState(
        client: MinecraftClient,
        blockItem: BlockItem,
        stack: net.minecraft.item.ItemStack,
        pos: BlockPos,
        look: Vec3d,
    ): BlockState? {
        val player = client.player ?: return null
        val world = client.world ?: return null
        val side = nearestDirection(-look.x, -look.y, -look.z)
        val hitPos = Vec3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5).add(
            side.offsetX * 0.5,
            side.offsetY * 0.5,
            side.offsetZ * 0.5,
        )
        val context = object : ItemPlacementContext(
            world,
            player,
            Hand.MAIN_HAND,
            stack,
            BlockHitResult(hitPos, side, pos, false),
        ) {
            override fun getBlockPos(): BlockPos = pos

            // The whole point of Angel is that there is nothing to build against,
            // so the support checks vanilla would run here are the wrong question.
            override fun canPlace(): Boolean = true

            override fun canReplaceExisting(): Boolean = true
        }
        val adjusted = blockItem.getPlacementContext(context) ?: context
        return blockItem.block.getPlacementState(adjusted)
    }

    // Takes components rather than a vector so it stays free of the Vec3 helper
    // methods, which are named differently in the 26.x official namespace.
    private fun nearestDirection(x: Double, y: Double, z: Double): Direction {
        val absX = kotlin.math.abs(x)
        val absY = kotlin.math.abs(y)
        val absZ = kotlin.math.abs(z)
        return when {
            absY >= absX && absY >= absZ -> if (y > 0) Direction.UP else Direction.DOWN
            absX >= absZ -> if (x > 0) Direction.EAST else Direction.WEST
            else -> if (z > 0) Direction.SOUTH else Direction.NORTH
        }
    }
}
