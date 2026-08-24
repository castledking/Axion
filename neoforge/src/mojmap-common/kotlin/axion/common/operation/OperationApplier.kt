package axion.common.operation

import net.minecraft.world.level.Level

interface OperationApplier {
    fun apply(world: Level, operation: EditOperation)
}
