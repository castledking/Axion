package axion.server.paper

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.block.data.BlockData

/**
 * Keeps every Paper confirm, undo, and redo write on Bukkit's no-physics path.
 *
 * Capability interactions (replace mode, infinite reach, force place, and plain
 * place/break routed through Axion) opt into physics by passing
 * `applyPhysics = true`, which is what an ordinary vanilla click would do. Only
 * the No Updates capability keeps them on the quiet path.
 */
internal object PaperBlockWritePolicy {
    const val APPLY_PHYSICS = false

    fun setBlockData(block: Block, data: BlockData, applyPhysics: Boolean = APPLY_PHYSICS) {
        block.setBlockData(data, applyPhysics)
    }

    fun setType(block: Block, material: Material, applyPhysics: Boolean = APPLY_PHYSICS) {
        block.setType(material, applyPhysics)
    }

    fun copyState(state: BlockState, destination: Location): Boolean =
        state.copy(destination).update(true, APPLY_PHYSICS)
}
