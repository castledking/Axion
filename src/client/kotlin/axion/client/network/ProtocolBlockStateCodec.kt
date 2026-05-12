package axion.client.network

import axion.common.compat.VersionCompat
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient

object ProtocolBlockStateCodec {
    fun decode(state: String): BlockState? {
        // 26.1.2: BlockArgumentParser API has changed significantly
        // For now, return null - actual implementation needs proper 26.1.2 API
        return null
    }
}
