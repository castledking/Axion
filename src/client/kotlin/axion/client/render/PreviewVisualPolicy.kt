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
    // All preview pipelines cull back faces, so only the camera-facing shell
    // contributes opacity. Keeping this explicit prevents modern versions from
    // accidentally using the two-crossing alpha again.
    const val VISIBLE_DESTINATION_SHELL_CROSSINGS: Int = 1

    // Every supported preview pipeline culls back faces, so the camera sees one
    // shell crossing. Alpha 204 leaves ~20% of the scene visible, matching the
    // 26.1.2 preview that established the desired visual. This also makes sky
    // clouds substantially less prominent through a scrolled preview.
    //
    // These are alphaForTransmission(TARGET_SCENE_TRANSMISSION, crossings)
    // pre-computed, since `const val` cannot call a function. The
    // PreviewVisualPolicyTest keeps them in sync with the formula.
    const val DESTINATION_ALPHA: Int = 204
    const val MOVE_DESTINATION_ALPHA: Int = DESTINATION_ALPHA
    // Source replacement is textured stained glass, unlike the destination
    // shell. Full modulator alpha leaves its opacity entirely to the vanilla
    // glass texture, so it reads like an ordinary glass block instead of an
    // extra-faint preview overlay.
    const val MOVE_SOURCE_ALPHA: Int = 255
    const val CULLED_MOVE_SOURCE_ALPHA: Int = MOVE_SOURCE_ALPHA
    const val CULLED_DESTINATION_ALPHA: Int = DESTINATION_ALPHA
    const val SPARSE_DESTINATION_ALPHA: Int = DESTINATION_ALPHA
    const val CULLED_SPARSE_DESTINATION_ALPHA: Int = CULLED_DESTINATION_ALPHA

    const val PLACEMENT_PULSE_MIN_ALPHA: Int = 8
    const val PLACEMENT_PULSE_MAX_ALPHA: Int = 26

    const val CULL_GHOST_BACK_FACES: Boolean = true

    /**
     * Shader define that makes the preview shell take its opacity from the
     * vertex/modulator alpha alone, ignoring the sampled texel alpha.
     *
     * [DESTINATION_ALPHA] and friends are solved for an exact number of shell
     * crossings, so letting a translucent block's texel alpha compound into them
     * makes the ghost patchily see-through and hides what is about to be placed.
     * The texture still decides the silhouette and the colour.
     *
     * Move-source glass is the deliberate exception: full modulator alpha lets
     * the sampled vanilla glass texel control opacity, so it must not define
     * this.
     */
    const val IGNORE_TEXTURE_ALPHA_DEFINE: String = "IGNORE_TEXTURE_ALPHA"

    /** Whether a preview's shell should ignore texel alpha, keyed by preview id. */
    fun ignoresTextureAlpha(previewId: String): Boolean =
        !previewId.endsWith(PreviewBlockIdentityPolicy.MOVE_SOURCE_SESSION_TAG)

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

    /** Signed-phase pulse with a non-zero floor at the color crossover. */
    fun pulseAlpha(minAlpha: Int, maxAlpha: Int, signedPhase: Float): Float {
        require(minAlpha in 0..255 && maxAlpha in minAlpha..255)
        val magnitude = abs(signedPhase.coerceIn(-1f, 1f))
        return minAlpha + ((maxAlpha - minAlpha) * magnitude)
    }
}
