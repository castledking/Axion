package axion.common.model

import net.minecraft.nbt.NbtCompound

data class BlockEntityDataSnapshot(
    val nbt: NbtCompound,
) {
    fun copy(): BlockEntityDataSnapshot = BlockEntityDataSnapshot(nbt.copy())
}
