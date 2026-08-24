package axion.common.model

import net.minecraft.nbt.CompoundTag

data class BlockEntityDataSnapshot(
    val nbt: CompoundTag,
) {
    fun copy(): BlockEntityDataSnapshot = BlockEntityDataSnapshot(nbt.copy())
}
