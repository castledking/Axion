package axion.client.render

import net.minecraft.client.render.VertexConsumer

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

    // --- Abstract method implementations (must be explicit, not delegated) ---

    override fun vertex(x: Float, y: Float, z: Float): VertexConsumer {
        delegate.vertex(x, y, z)
        return this
    }

    override fun color(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer {
        delegate.color(tinted(red, tintRed), tinted(green, tintGreen), tinted(blue, tintBlue), scaledAlpha(alpha))
        return this
    }

    override fun color(color: Int): VertexConsumer {
        delegate.color(tintedPackedColor(color))
        return this
    }

    override fun texture(u: Float, v: Float): VertexConsumer {
        delegate.texture(u, v)
        return this
    }

    override fun overlay(u: Int, v: Int): VertexConsumer {
        delegate.overlay(u, v)
        return this
    }

    override fun light(u: Int, v: Int): VertexConsumer {
        if (fullBright) {
            delegate.light(MAX_LIGHT_UV, MAX_LIGHT_UV)
        } else {
            delegate.light(u, v)
        }
        return this
    }

    override fun normal(x: Float, y: Float, z: Float): VertexConsumer {
        delegate.normal(x, y, z)
        return this
    }

    @Suppress("NOTHING_TO_OVERRIDE", "ACCIDENTAL_OVERRIDE")
    override fun lineWidth(w: Float): VertexConsumer {
        lineWidthMethod?.invoke(delegate, w)
        return this
    }

    // --- Critical: 11-arg vertex fast-path override ---
    //
    // MC's block rendering pipeline calls this method via VertexConsumer.quad().
    // Without this override, BufferBuilder's fast-path writes color and light
    // directly to the buffer, completely bypassing our color() and light() overrides.
    // This was the root cause of ghost blocks appearing un-tinted and dark.

    override fun vertex(
        x: Float, y: Float, z: Float,
        color: Int,
        u: Float, v: Float,
        overlay: Int,
        light: Int,
        nx: Float, ny: Float, nz: Float,
    ) {
        delegate.vertex(
            x, y, z,
            tintedPackedColor(color),
            u, v,
            overlay,
            if (fullBright) MAX_LIGHT_PACKED else light,
            nx, ny, nz,
        )
    }

    // --- Default methods (quad, light(int), overlay(int), etc.) are inherited from ---
    // --- VertexConsumer and will dispatch through our overridden methods above.     ---

    private fun tintedPackedColor(color: Int): Int {
        val a = scaledAlpha((color ushr 24) and 0xFF)
        val r = tinted((color shr 16) and 0xFF, tintRed)
        val g = tinted((color shr 8) and 0xFF, tintGreen)
        val b = tinted(color and 0xFF, tintBlue)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun scaledAlpha(alpha: Int) = (alpha * alphaScale).toInt().coerceIn(0, 255)
    private fun tinted(channel: Int, tint: Int) = mix(mix(channel, 255, liftRatio), tint, tintRatio)
    private fun mix(from: Int, to: Int, amt: Float) = (from + (to - from) * amt).toInt().coerceIn(0, 255)

    companion object {
        /** Max lightmap UV coordinate — full sky light + full block light (0xF0 = 240). */
        private const val MAX_LIGHT_UV: Int = 0xF0

        /** Packed max light value for the 11-arg vertex() method: both components at 0xF0. */
        private const val MAX_LIGHT_PACKED: Int = 0x00F000F0

        /** lineWidth was added to VertexConsumer in 1.21.11 — resolved via reflection for cross-version compat. */
        private val lineWidthMethod: java.lang.reflect.Method? = runCatching {
            VertexConsumer::class.java.getMethod("lineWidth", Float::class.javaPrimitiveType)
        }.getOrNull()
    }
}
