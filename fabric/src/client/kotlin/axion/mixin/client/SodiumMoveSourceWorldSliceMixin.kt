package axion.mixin.client

import axion.client.render.MoveSourceRenderState
import net.minecraft.block.BlockState
import net.minecraft.client.world.ClientWorld
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Pseudo
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Desc
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/**
 * Sodium 0.5.x (used by exact Minecraft 1.21) snapshots chunk data through
 * WorldSlice. Sodium 0.6 renamed and moved this class to LevelSlice, which is
 * covered separately by [SodiumMoveSourceLevelSliceMixin].
 */
@Pseudo
@Mixin(
    targets = ["me.jellysquid.mods.sodium.client.world.WorldSlice"],
    remap = false,
)
abstract class SodiumMoveSourceWorldSliceMixin {
    @Shadow(remap = false)
    @Final
    private lateinit var world: ClientWorld

    @Inject(
        target = [
            Desc(
                value = "getBlockState",
                ret = BlockState::class,
                args = [Int::class, Int::class, Int::class],
            ),
        ],
        at = [At("HEAD")],
        cancellable = true,
        remap = false,
        require = 0,
    )
    private fun axionSuppressMoveSourceBlock(
        x: Int,
        y: Int,
        z: Int,
        cir: CallbackInfoReturnable<BlockState>,
    ) {
        val replacement = MoveSourceRenderState.suppressedState(world, x, y, z) ?: return
        cir.returnValue = replacement
    }
}
