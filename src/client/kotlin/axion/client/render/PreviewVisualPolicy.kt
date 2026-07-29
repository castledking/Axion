package axion.client.render

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Version-independent visual contract for destination block previews.
 *
 * Keeping these values outside the compatibility renderers prevents the eight
 * release branches from slowly acquiring different opacity and pulse behavior.
 */
object PreviewVisualPolicy {
    /**
     * When false, textured block previews read the world's depth buffer and
     * disappear behind terrain. Keep depth writes disabled in every pipeline:
     * previews should be occluded by the scene, but must never occlude it.
     *
     * This is deliberately a code policy rather than mutable render state so a
     * frame cannot switch depth behavior halfway through a cached GPU pipeline.
     */
    const val XRAY_BLOCK_PREVIEWS: Boolean = false

    // Destination ghosts are 80% opaque after all visible shell crossings.
    const val TARGET_SCENE_TRANSMISSION: Double = 0.20
    // Move-source glass deliberately remains 80% transparent so the terrain
    // around and behind the replaced source region stays readable.
    const val MOVE_SOURCE_TARGET_SCENE_TRANSMISSION: Double = 0.80
    const val CLOSED_SHELL_CROSSINGS: Int = 2

    // Vanilla light-gray stained-glass texels average this alpha in every
    // supported client asset. It is multiplied by the renderer's vertex alpha.
    const val STAINED_GLASS_MEAN_TEXTURE_ALPHA: Double = 0.460126

    // A two-sided closed shell contributes a near and far surface. Alpha 141
    // on each leaves ~20% of the scene visible after both source-over blends.
    // In other words, the complete destination ghost is 80% opaque. The
    // older cull-enabled pipelines contribute one crossing, whose equivalent
    // alpha is 204. Sparse shells on no-cull pipelines are still closed shells
    // and therefore keep the two-crossing alpha.
    //
    // These are alphaForTransmission(TARGET_SCENE_TRANSMISSION, crossings)
    // pre-computed, since `const val` cannot call a function. The
    // PreviewVisualPolicyTest keeps them in sync with the formula.
    const val DESTINATION_ALPHA: Int = 141
    const val MOVE_DESTINATION_ALPHA: Int = 141
    // Source replacement is textured stained glass, unlike the destination
    // shell. Alpha 59 with two visible sides (or 111 with back-face culling)
    // leaves roughly 80% of the terrain visible through vanilla glass.
    const val MOVE_SOURCE_ALPHA: Int = 59
    const val CULLED_MOVE_SOURCE_ALPHA: Int = 111
    const val CULLED_DESTINATION_ALPHA: Int = 204
    const val SPARSE_DESTINATION_ALPHA: Int = DESTINATION_ALPHA
    const val CULLED_SPARSE_DESTINATION_ALPHA: Int = CULLED_DESTINATION_ALPHA

    const val PLACEMENT_PULSE_MIN_ALPHA: Int = 8
    const val PLACEMENT_PULSE_MAX_ALPHA: Int = 26

    const val CULL_GHOST_BACK_FACES: Boolean = true

    /** Per-layer source alpha needed to leave [transmission] after [crossings]. */
    fun alphaForTransmission(transmission: Double, crossings: Int): Int {
        require(transmission in 0.0..1.0) { "transmission must be between 0 and 1" }
        require(crossings > 0) { "crossings must be positive" }
        val sourceAlpha = 1.0 - transmission.pow(1.0 / crossings)
        return (sourceAlpha * 255.0).roundToInt().coerceIn(0, 255)
    }

    fun compoundedTransmission(alpha: Int, crossings: Int): Double {
        require(crossings > 0) { "crossings must be positive" }
        return (1.0 - alpha.coerceIn(0, 255) / 255.0).pow(crossings)
    }

    fun compoundedTexturedTransmission(alpha: Int, textureAlpha: Double, crossings: Int): Double {
        require(textureAlpha in 0.0..1.0) { "textureAlpha must be between 0 and 1" }
        require(crossings > 0) { "crossings must be positive" }
        val effectiveAlpha = alpha.coerceIn(0, 255) / 255.0 * textureAlpha
        return (1.0 - effectiveAlpha).pow(crossings)
    }

    /** Signed-phase pulse with a non-zero floor at the color crossover. */
    fun pulseAlpha(minAlpha: Int, maxAlpha: Int, signedPhase: Float): Float {
        require(minAlpha in 0..255 && maxAlpha in minAlpha..255)
        val magnitude = abs(signedPhase.coerceIn(-1f, 1f))
        return minAlpha + ((maxAlpha - minAlpha) * magnitude)
    }
}
