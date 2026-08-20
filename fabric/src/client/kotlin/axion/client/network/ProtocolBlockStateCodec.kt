package axion.client.network

import axion.common.compat.VersionCompat
import net.minecraft.block.BlockState

object ProtocolBlockStateCodec {
    fun decode(state: String): BlockState? {
        return VersionCompat.INSTANCE.stringToBlockState(state)
    }
}
