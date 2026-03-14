package axion.common.operation

import net.minecraft.world.World

interface OperationApplier {
    fun apply(world: World, operation: EditOperation)
}
