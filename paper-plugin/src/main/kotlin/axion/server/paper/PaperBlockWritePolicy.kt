package axion.server.paper

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.block.data.BlockData

/** Keeps every Paper confirm, undo, and redo write on Bukkit's no-physics path. */
internal object PaperBlockWritePolicy {
    const val APPLY_PHYSICS = false

    fun setBlockData(block: Block, data: BlockData) {
        block.setBlockData(data, APPLY_PHYSICS)
    }

    fun setType(block: Block, material: Material) {
        block.setType(material, APPLY_PHYSICS)
    }

    fun copyState(state: BlockState, destination: Location): Boolean =
        state.copy(destination).update(true, APPLY_PHYSICS)
}
