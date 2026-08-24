package axion.client.itemStack

import axion.client.current.AxionTarget
import axion.client.current.blockPosOrNull
import axion.client.compat.toImmutable
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
            extrudePositions = footprint.map { it.add(direction.extents).toImmutable() },
        )
    }
}
