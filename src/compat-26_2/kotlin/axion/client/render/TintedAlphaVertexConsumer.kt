package axion.client.render

import com.mojang.blaze3d.vertex.VertexConsumer

class TintedAlphaVertexConsumer(
    private val delegate: VertexConsumer,
    private val alphaScale: Float,
    tintColor: Int,
    private val liftRatio: Float = 0.5f,
    private val tintRatio: Float = 0.35f,
    private val fullBright: Boolean = false,
) : VertexConsumer {
    private val tintRed = (tintColor shr 16) and 0xFF
    private val tintGreen = (tintColor shr 8) and 0xFF
    private val tintBlue = tintColor and 0xFF

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
        delegate.addVertex(x, y, z)
        return this
    }
    
    override fun setColor(r: Int, g: Int, b: Int, a: Int): VertexConsumer {
        delegate.setColor(tinted(r, tintRed), tinted(g, tintGreen), tinted(b, tintBlue), scaledAlpha(a))
        return this
    }
    
    override fun setColor(color: Int): VertexConsumer {
        delegate.setColor(tintedPackedColor(color))
        return this
    }
    
    override fun setUv(u: Float, v: Float): VertexConsumer {
        delegate.setUv(u, v)
        return this
    }
    
    override fun setUv1(u: Int, v: Int): VertexConsumer {
        delegate.setUv1(u, v)
        return this
    }
    
    override fun setUv2(u: Int, v: Int): VertexConsumer {
        if (fullBright) {
            delegate.setUv2(MAX_LIGHT_UV, MAX_LIGHT_UV)
        } else {
            delegate.setUv2(u, v)
        }
        return this
    }
    
    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer {
        delegate.setNormal(x, y, z)
        return this
    }
    
    override fun setLineWidth(width: Float): VertexConsumer {
        delegate.setLineWidth(width)
        return this
    }

    override fun addVertex(
        x: Float,
        y: Float,
        z: Float,
        color: Int,
        u: Float,
        v: Float,
        overlay: Int,
        light: Int,
        nx: Float,
        ny: Float,
        nz: Float,
    ) {
        delegate.addVertex(
            x,
            y,
            z,
            tintedPackedColor(color),
            u,
            v,
            overlay,
            if (fullBright) MAX_LIGHT_PACKED else light,
            nx,
            ny,
            nz,
        )
    }

    private fun tintedPackedColor(color: Int): Int {
        val a = scaledAlpha((color ushr 24) and 0xFF)
        val r = tinted((color shr 16) and 0xFF, tintRed)
        val g = tinted((color shr 8) and 0xFF, tintGreen)
        val b = tinted(color and 0xFF, tintBlue)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun scaledAlpha(alpha: Int) = (alpha * alphaScale).toInt().coerceIn(0, 255)

    private fun tinted(channel: Int, tint: Int) = mix(mix(channel, 255, liftRatio), tint, tintRatio)

    private fun mix(from: Int, to: Int, amount: Float) = (from + (to - from) * amount).toInt().coerceIn(0, 255)

    companion object {
        private const val MAX_LIGHT_UV: Int = 0xF0
        private const val MAX_LIGHT_PACKED: Int = 0x00F000F0
    }
}
