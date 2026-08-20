package axion.client.compat

/**
 * Flat name for the blockstate base class the support-check mixin targets.
 *
 * 26.x renamed `AbstractBlock.AbstractBlockState` to
 * `BlockBehaviour.BlockStateBase` and `canPlaceAt` to `canSurvive`.
 */
typealias AxionBlockStateBase = net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase
