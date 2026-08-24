package axion.client.compat

/**
 * Flat name for the blockstate base class the support-check mixin targets.
 *
 * The class is nested (`AbstractBlock.AbstractBlockState`) and is renamed
 * outright in the 26.x official namespace, so the shared mixin refers to it
 * through this alias rather than spelling either form directly.
 */
typealias AxionBlockStateBase = net.minecraft.world.level.block.state.BlockBehaviour.AbstractBlockState
