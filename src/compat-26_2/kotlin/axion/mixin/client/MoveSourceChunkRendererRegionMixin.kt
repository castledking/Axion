package axion.mixin.client

import axion.client.render.defaultState
import axion.client.render.MoveSourceRenderState
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.renderer.chunk.RenderSectionRegion
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.FluidState
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(RenderSectionRegion::class)
abstract class MoveSourceChunkRendererRegionMixin {
    @Shadow
    @Final
    private lateinit var level: ClientWorld

    @Inject(
        method = ["getBlockState"],
        at = [At("HEAD")],
        cancellable = true,
    )
    private fun axionSuppressMoveSourceBlock(
        pos: BlockPos,
        cir: CallbackInfoReturnable<BlockState>,
    ) {
        if (MoveSourceRenderState.shouldSuppress(level, pos)) {
            cir.returnValue = Blocks.AIR.defaultState
        }
    }

    @Inject(method = ["getFluidState"], at = [At("HEAD")], cancellable = true)
    private fun axionSuppressMoveSourceFluid(
        pos: BlockPos,
        cir: CallbackInfoReturnable<FluidState>,
    ) {
        if (MoveSourceRenderState.shouldSuppress(level, pos)) {
            cir.returnValue = Blocks.AIR.defaultState.fluidState
        }
    }

    @Inject(method = ["getBlockEntity"], at = [At("HEAD")], cancellable = true)
    private fun axionSuppressMoveSourceBlockEntity(
        pos: BlockPos,
        cir: CallbackInfoReturnable<BlockEntity?>,
    ) {
        if (MoveSourceRenderState.shouldSuppress(level, pos)) {
            cir.returnValue = null
        }
    }
}
