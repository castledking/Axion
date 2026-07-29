package axion.client.render

import axion.client.tool.CloneToolState
import axion.client.tool.PlacementPreviewPolicy
import axion.client.tool.PlacementToolMode
import axion.common.model.ClipboardBuffer
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.client.MinecraftClient
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos

/**
 * Selected source blocks that must be absent from vanilla's baked chunk mesh
 * while a MOVE preview is active.
 *
 * The snapshot is immutable after publication because chunk compilation reads
 * it from worker threads. Only exact non-air clipboard cells are included; the
 * source region is used solely as the clipboard origin.
 */
object MoveSourceRenderState {
    internal data class SectionCoordinate(
        val x: Int,
        val y: Int,
        val z: Int,
    )

    private data class Snapshot(
        val world: ClientWorld,
        val clipboard: ClipboardBuffer,
        val sourceOrigin: BlockPos,
        val positions: LongOpenHashSet,
        val sections: Set<SectionCoordinate>,
    )

    @Volatile
    private var snapshot: Snapshot? = null

    fun synchronize(state: CloneToolState) {
        val world = runCatching { MinecraftClient.getInstance().world }.getOrNull()
        synchronize(world, state)
    }

    fun shouldSuppress(worldIdentity: Any, pos: BlockPos): Boolean {
        val current = snapshot ?: return false
        return current.world === worldIdentity && current.positions.contains(pos.asLong())
    }

    /**
     * Drops a mask tied to an unloaded/different world. The caller resets the
     * placement preview too, since its captured source is not valid there.
     */
    fun clearIfWorldChanged(world: ClientWorld?): Boolean {
        val current = snapshot ?: return false
        if (current.world === world) return false
        snapshot = null
        return true
    }

    /**
     * Disconnect/client-stop teardown. No invalidation is needed because the
     * associated world renderer is being discarded.
     */
    fun clear() {
        snapshot = null
    }

    private fun synchronize(world: ClientWorld?, state: CloneToolState) {
        val preview = PlacementPreviewPolicy.activePreview(state)
            ?.takeIf { it.mode == PlacementToolMode.MOVE }
        val previous = snapshot

        if (world == null || preview == null) {
            if (previous == null) return
            snapshot = null
            if (world === previous.world) {
                MoveSourceRenderInvalidator.invalidate(world, previous.sections)
            }
            return
        }

        val sourceOrigin = preview.sourceRegion.minCorner()
        if (
            previous != null &&
            previous.world === world &&
            previous.clipboard === preview.sourceClipboardBuffer &&
            previous.sourceOrigin == sourceOrigin
        ) {
            return
        }

        val selectedCells = preview.sourceClipboardBuffer.nonAirCells()
        val positions = LongOpenHashSet(selectedCells.size)
        val sections = HashSet<SectionCoordinate>()
        selectedCells.forEach { cell ->
            val pos = cell.absolutePos(sourceOrigin)
            positions.add(pos.asLong())
            sections += SectionCoordinate(pos.x shr 4, pos.y shr 4, pos.z shr 4)
        }
        val nextSnapshot = Snapshot(
            world = world,
            clipboard = preview.sourceClipboardBuffer,
            sourceOrigin = sourceOrigin,
            positions = positions,
            sections = sections,
        )

        // Publish first: every rebuild scheduled below must observe the new
        // mask, including asynchronous chunk compiler workers.
        snapshot = nextSnapshot
        val dirtySections = HashSet<SectionCoordinate>(sections)
        if (previous?.world === world) {
            dirtySections += previous.sections
        }
        MoveSourceRenderInvalidator.invalidate(world, dirtySections)
    }
}
