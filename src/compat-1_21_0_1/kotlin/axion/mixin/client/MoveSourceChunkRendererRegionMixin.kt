package axion.mixin.client

import axion.client.render.MoveSourceRenderState
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.render.chunk.ChunkRendererRegion
import net.minecraft.fluid.FluidState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ChunkRendererRegion::class)
abstract class MoveSourceChunkRendererRegionMixin {
    @Shadow
    @Final
    private lateinit var world: World

    @Inject(
        method = ["getBlockState"],
        at = [At("HEAD")],
        cancellable = true,
    )
    private fun axionSuppressMoveSourceBlock(
        pos: BlockPos,
        cir: CallbackInfoReturnable<BlockState>,
    ) {
        if (MoveSourceRenderState.shouldSuppress(world, pos)) {
            cir.returnValue = Blocks.AIR.defaultState
        }
    }

    @Inject(method = ["getFluidState"], at = [At("HEAD")], cancellable = true)
    private fun axionSuppressMoveSourceFluid(
        pos: BlockPos,
        cir: CallbackInfoReturnable<FluidState>,
    ) {
        if (MoveSourceRenderState.shouldSuppress(world, pos)) {
            cir.returnValue = Blocks.AIR.defaultState.fluidState
        }
    }

    @Inject(method = ["getBlockEntity"], at = [At("HEAD")], cancellable = true)
    private fun axionSuppressMoveSourceBlockEntity(
        pos: BlockPos,
        cir: CallbackInfoReturnable<BlockEntity?>,
    ) {
        if (MoveSourceRenderState.shouldSuppress(world, pos)) {
            cir.returnValue = null
        }
    }
}
