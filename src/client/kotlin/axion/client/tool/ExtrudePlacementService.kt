package axion.client.tool

import axion.client.selection.AxionTarget
import axion.client.selection.blockPosOrNull
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.BlockPos
import net.minecraft.world.BlockView

object ExtrudePlacementService {
    fun createPreview(
        client: MinecraftClient,
        world: BlockView,
        target: AxionTarget,
    ): ExtrudePreviewState? {
        val origin = target.blockPosOrNull()?.toImmutable() ?: return null
        val footprint = LayerDiscoveryService.discoverPlanarFootprint(
            world = world,
            origin = origin,
            direction = ExtrudeTargetService.resolveDirection(client, target),
        )
        if (footprint.isEmpty()) {
            return null
        }

        val direction = ExtrudeTargetService.resolveDirection(client, target)
        val sourceState = world.getBlockState(origin)
        return ExtrudePreviewState(
            origin = origin,
            footprint = footprint,
            sourceState = sourceState,
            direction = direction,
            extrudePositions = footprint.map { it.add(direction.vector).toImmutable() },
        )
    }
}
