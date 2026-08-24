package axion.client.network

import axion.common.compat.VersionCompat
import net.minecraft.world.level.block.state.BlockState

object ProtocolBlockStateCodec {
    fun decode(state: String): BlockState? {
        return VersionCompat.INSTANCE.stringToBlockState(state)
    }
}
