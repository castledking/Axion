package axion.client.mode

import axion.common.model.SymmetryConfig
import axion.client.compat.normalizeReplacePlacementState
import axion.common.operation.SymmetryBlockPlacement
import axion.common.operation.SymmetryPlacementOperation
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.client.Minecraft
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.Shapes

object BuildPlacementService {
    fun createPlacementOperation(
        client: Minecraft,
        target: ModeTargeting.BlockTarget,
        symmetryConfig: SymmetryConfig? = null,
        replaceMode: Boolean = false,
        hand: InteractionHand = InteractionHand.MAIN_HAND,
    ): SymmetryPlacementOperation? {
        val player = client.player ?: return null
        val world = client.level ?: return null
        val stack = player.getItemInHand(hand)
        val blockItem = stack.item as? BlockItem ?: return null

        return withSupportBypass {
            resolvePlacementOperation(world, player, hand, stack, blockItem, target, symmetryConfig, replaceMode)
        }
    }

    private fun resolvePlacementOperation(
        world: net.minecraft.client.multiplayer.ClientLevel,
        player: net.minecraft.client.player.LocalPlayer,
        hand: InteractionHand,
        stack: net.minecraft.world.item.ItemStack,
        blockItem: BlockItem,
        target: ModeTargeting.BlockTarget,
        symmetryConfig: SymmetryConfig?,
        replaceMode: Boolean,
    ): SymmetryPlacementOperation? {
        val primary = createPrimaryPlacement(
            world = world,
            player = player,
            hand = hand,
            stack = stack,
            blockItem = blockItem,
            hitResult = target.hitResult,
            replaceMode = replaceMode,
        ) ?: return null

        val placements = buildList {
            add(primary.placement)
            addAll(
                createDerivedPlacements(
                    world = world,
                    player = player,
                    hand = hand,
                    stack = stack,
                    blockItem = blockItem,
                    target = target,
                    symmetryConfig = symmetryConfig,
                    replaceMode = replaceMode,
                    primaryPos = primary.placement.pos,
                ),
            )
        }.distinctBy { it.pos }

        if (placements.isEmpty()) {
            return null
        }

        return SymmetryPlacementOperation(placements)
    }

    fun createDerivedPlacementOperation(
        client: Minecraft,
        target: ModeTargeting.BlockTarget,
        symmetryConfig: SymmetryConfig,
        replaceMode: Boolean = false,
        hand: InteractionHand = InteractionHand.MAIN_HAND,
    ): SymmetryPlacementOperation? {
        val player = client.player ?: return null
        val world = client.level ?: return null
        val stack = player.getItemInHand(hand)
        val blockItem = stack.item as? BlockItem ?: return null

        return withSupportBypass {
            resolveDerivedPlacementOperation(world, player, hand, stack, blockItem, target, symmetryConfig, replaceMode)
        }
    }

    private fun resolveDerivedPlacementOperation(
        world: net.minecraft.client.multiplayer.ClientLevel,
        player: net.minecraft.client.player.LocalPlayer,
        hand: InteractionHand,
        stack: net.minecraft.world.item.ItemStack,
        blockItem: BlockItem,
        target: ModeTargeting.BlockTarget,
        symmetryConfig: SymmetryConfig,
        replaceMode: Boolean,
    ): SymmetryPlacementOperation? {
        val primary = createPrimaryPlacement(
            world = world,
            player = player,
            hand = hand,
            stack = stack,
            blockItem = blockItem,
            hitResult = target.hitResult,
            replaceMode = replaceMode,
        ) ?: return null

        val placements = createDerivedPlacements(
            world = world,
            player = player,
            hand = hand,
            stack = stack,
            blockItem = blockItem,
            target = target,
            symmetryConfig = symmetryConfig,
            replaceMode = replaceMode,
            primaryPos = primary.placement.pos,
        )
            .distinctBy { it.pos }

        if (placements.isEmpty()) {
            return null
        }

        return SymmetryPlacementOperation(placements)
    }

    /**
     * Runs a placement resolution with Force Place's support bypass applied.
     *
     * Wrapping the whole resolution matters: the bypass has to cover
     * `getPlacementState` as well as the `canPlaceAt` guards under it. A lantern
     * returns no state at all when its support check fails, so without this there
     * would be nothing to place rather than something to place illegally.
     */
    private fun <T> withSupportBypass(block: () -> T): T =
        ForcePlaceSupportBypass.withBypass(AxionCapabilityPolicy.ignoresSupportRequirements(), block)

    private fun createPrimaryPlacement(
        world: net.minecraft.client.multiplayer.ClientLevel,
        player: net.minecraft.client.player.LocalPlayer,
        hand: InteractionHand,
        stack: net.minecraft.world.item.ItemStack,
        blockItem: BlockItem,
        hitResult: BlockHitResult,
        replaceMode: Boolean,
    ): PlacementResult? {
        return if (replaceMode) {
            createReplacePlacement(
                world = world,
                player = player,
                hand = hand,
                stack = stack,
                blockItem = blockItem,
                hitResult = hitResult,
            )
        } else {
            createAdjacentPlacement(
                world = world,
                player = player,
                hand = hand,
                stack = stack,
                blockItem = blockItem,
                hitResult = hitResult,
            )
        }
    }

    private fun createDerivedPlacements(
        world: net.minecraft.client.multiplayer.ClientLevel,
        player: net.minecraft.client.player.LocalPlayer,
        hand: InteractionHand,
        stack: net.minecraft.world.item.ItemStack,
        blockItem: BlockItem,
        target: ModeTargeting.BlockTarget,
        symmetryConfig: SymmetryConfig?,
        replaceMode: Boolean,
        primaryPos: BlockPos,
    ): List<SymmetryBlockPlacement> {
        val config = symmetryConfig ?: return emptyList()
        return axion.client.symmetry.SymmetryTransformService.activeTransforms(config)
            .asSequence()
            .filterNot { transform ->
                transform.rotationQuarterTurns == 0 && transform.mirrorAxis == null
            }
            .map { transform ->
                val derivedPos = axion.client.symmetry.SymmetryTransformService.transformBlock(
                    sourceBlock = primaryPos,
                    anchor = config.anchor.position,
                    transform = transform,
                )
                val derivedSide = axion.client.symmetry.SymmetryTransformService.transformDirection(
                    target.hitResult.direction,
                    transform,
                )
                derivedPos to derivedSide
            }
            .filter { (derivedPos, _) -> derivedPos != primaryPos }
            .distinctBy { (derivedPos, _) -> derivedPos }
            .mapNotNull { (derivedPos, derivedSide) ->
                if (replaceMode) {
                    createReplacePlacementAt(
                        world = world,
                        player = player,
                        hand = hand,
                        stack = stack,
                        blockItem = blockItem,
                        pos = derivedPos,
                        side = derivedSide,
                    )
                } else {
                    createAdjacentPlacementAt(
                        world = world,
                        player = player,
                        hand = hand,
                        stack = stack,
                        blockItem = blockItem,
                        pos = derivedPos,
                        side = derivedSide,
                    )
                }
            }
            .distinctBy { it.pos }
            .toList()
    }

    private fun createAdjacentPlacement(
        world: net.minecraft.client.multiplayer.ClientLevel,
        player: net.minecraft.client.player.LocalPlayer,
        hand: InteractionHand,
        stack: net.minecraft.world.item.ItemStack,
        blockItem: BlockItem,
        hitResult: BlockHitResult,
    ): PlacementResult? {
        val rawContext = BlockPlaceContext(player, hand, stack, hitResult)
        val placementContext = blockItem.updatePlacementContext(rawContext) ?: return null
        if (!placementContext.canPlace()) {
            return null
        }
        val placementPos = placementContext.clickedPos
        val placementState = blockItem.getPlacementState(placementContext) ?: return null
        if (!placementState.canSurvive(world, placementPos)) {
            return null
        }
        if (wouldCollideWithPlayer(world, player, placementPos, placementState)) {
            return null
        }
        return PlacementResult(
            placement = SymmetryBlockPlacement(placementPos, placementState),
            canReplaceExisting = rawContext.replacingClickedOnBlock(),
        )
    }

    private fun createAdjacentPlacementAt(
        world: net.minecraft.client.multiplayer.ClientLevel,
        player: net.minecraft.client.player.LocalPlayer,
        hand: InteractionHand,
        stack: net.minecraft.world.item.ItemStack,
        blockItem: BlockItem,
        pos: BlockPos,
        side: Direction,
    ): SymmetryBlockPlacement? {
        // Try the ideal support direction first (transformed side), then fall back
        // to any adjacent solid block. When the support pos is air/replaceable,
        // Minecraft's BlockPlaceContext tries to place there instead of at pos.
        val facesToTry = buildList {
            add(side)
            Direction.entries.forEach { d -> if (d != side) add(d) }
        }
        for (face in facesToTry) {
            val supportPos = pos.offset(face.opposite.unitVec3i)
            if (world.getBlockState(supportPos).isAir) continue
            val hitPos = centerOf(supportPos).add(
                face.stepX * 0.5,
                face.stepY * 0.5,
                face.stepZ * 0.5,
            )
            val rawContext = BlockPlaceContext(
                player,
                hand,
                stack,
                BlockHitResult(hitPos, face, supportPos, false),
            )
            val placementContext = blockItem.updatePlacementContext(rawContext) ?: continue
            if (placementContext.clickedPos != pos || !placementContext.canPlace()) continue
            val placementState = blockItem.getPlacementState(placementContext) ?: continue
            if (!placementState.canSurvive(world, pos)) continue
            if (wouldCollideWithPlayer(world, player, pos, placementState)) return null
            return SymmetryBlockPlacement(pos, placementState)
        }
        return null
    }

    private fun createReplacePlacement(
        world: net.minecraft.client.multiplayer.ClientLevel,
        player: net.minecraft.client.player.LocalPlayer,
        hand: InteractionHand,
        stack: net.minecraft.world.item.ItemStack,
        blockItem: BlockItem,
        hitResult: BlockHitResult,
    ): PlacementResult? {
        val placement = createReplacePlacementAt(
            world = world,
            player = player,
            hand = hand,
            stack = stack,
            blockItem = blockItem,
            pos = hitResult.blockPos,
            hitResult = hitResult,
        ) ?: return null
        return PlacementResult(
            placement = placement,
            canReplaceExisting = true,
        )
    }

    private fun createReplacePlacementAt(
        world: net.minecraft.client.multiplayer.ClientLevel,
        player: net.minecraft.client.player.LocalPlayer,
        hand: InteractionHand,
        stack: net.minecraft.world.item.ItemStack,
        blockItem: BlockItem,
        pos: BlockPos,
        hitResult: BlockHitResult,
    ): SymmetryBlockPlacement? {
        if (world.getBlockState(pos).isAir) {
            return null
        }

        val placementContext = object : BlockPlaceContext(world, player, hand, stack, hitResult) {
            override fun getClickedPos(): BlockPos = pos
            override fun canPlace(): Boolean = true
            override fun replacingClickedOnBlock(): Boolean = true
        }
        val adjustedContext = blockItem.updatePlacementContext(placementContext) ?: placementContext
        val rawPlacementState = blockItem.getPlacementState(adjustedContext) ?: return null
        val placementState = normalizeReplacePlacementState(rawPlacementState, hitResult, pos)
        if (!placementState.canSurvive(world, pos)) {
            return null
        }
        if (wouldCollideWithPlayer(world, player, pos, placementState)) {
            return null
        }

        return SymmetryBlockPlacement(
            pos = pos,
            state = placementState,
        )
    }

    private fun createReplacePlacementAt(
        world: net.minecraft.client.multiplayer.ClientLevel,
        player: net.minecraft.client.player.LocalPlayer,
        hand: InteractionHand,
        stack: net.minecraft.world.item.ItemStack,
        blockItem: BlockItem,
        pos: BlockPos,
        side: Direction,
    ): SymmetryBlockPlacement? {
        val supportPos = pos.offset(side.opposite.unitVec3i)
        val hitPos = centerOf(supportPos).add(
            side.stepX * 0.5,
            side.stepY * 0.5,
            side.stepZ * 0.5,
        )
        return createReplacePlacementAt(
            world = world,
            player = player,
            hand = hand,
            stack = stack,
            blockItem = blockItem,
            pos = pos,
            hitResult = BlockHitResult(hitPos, side, supportPos, false),
        )
    }

    private data class PlacementResult(
        val placement: SymmetryBlockPlacement,
        val canReplaceExisting: Boolean,
    )

    private fun wouldCollideWithPlayer(
        world: net.minecraft.client.multiplayer.ClientLevel,
        player: net.minecraft.client.player.LocalPlayer,
        pos: BlockPos,
        state: net.minecraft.world.level.block.state.BlockState,
    ): Boolean {
        if (player.isSpectator || player.noPhysics) {
            return false
        }
        // Force place is exactly "place it anyway". Axion writes the block
        // directly, so no entity ever gets a say — mobs included.
        if (AxionCapabilityPolicy.ignoresEntityCollision()) {
            return false
        }

        val collisionShape = state.getCollisionShape(world, pos, CollisionContext.of(player))
        if (collisionShape.isEmpty) {
            return false
        }

        val playerShape = Shapes.create(player.boundingBox.inflate(1.0E-4))
        return Shapes.joinIsNotEmpty(
            collisionShape.move(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()),
            playerShape,
            BooleanOp.AND,
        )
    }

    private fun centerOf(pos: BlockPos): Vec3 =
        Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
}
