package axion.client.tool

import axion.client.compat.extents
import axion.client.selection.AxionTarget
import axion.client.selection.blockPosOrNull
import axion.client.compat.add
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter

object ExtrudePlacementService {
    fun createPreview(
        client: Minecraft,
        world: BlockGetter,
        target: AxionTarget,
    ): ExtrudePreviewState? {
        val origin = target.blockPosOrNull()?.immutable() ?: return null
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
            extrudePositions = footprint.map { it.add(direction.extents).immutable() },
        )
    }
}
