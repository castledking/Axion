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
 * Sodium snapshots chunk data into LevelSlice and never asks vanilla's chunk
 * render region for block states. Hook its integer lookup as well so MOVE's
 * source cells are replaced regardless of which chunk renderer is installed.
 */
@Pseudo
@Mixin(
    targets = ["net.caffeinemc.mods.sodium.client.world.LevelSlice"],
    remap = false,
)
abstract class SodiumMoveSourceLevelSliceMixin {
    @Shadow(remap = false)
    @Final
    private lateinit var level: ClientWorld

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
        val replacement = MoveSourceRenderState.suppressedState(level, x, y, z) ?: return
        cir.returnValue = replacement
    }
}
