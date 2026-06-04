package axion.client.render

import net.minecraft.client.render.BuiltBuffer

class AxionPreviewBuffer : AutoCloseable {
    val isUploaded: Boolean get() = false
    val indexCountValue: Int get() = 0

    fun upload(builtBuffer: BuiltBuffer) {
        // Minecraft 1.21.4 does not expose the 1.21.5+ GPU buffer API used by
        // later Axion preview ranges. Chunked sessions still build dirty state,
        // but direct GPU drawing is disabled and falls back to the CPU renderer.
    }

    override fun close() = Unit
}
