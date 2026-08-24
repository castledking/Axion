package axion.common.model

/**
 * Covers the corner-stair case that vanilla's own mirror leaves alone.
 *
 * `StairBlock.mirror` only swaps the corner handedness (`inner_left` <->
 * `inner_right`, `outer_left` <-> `outer_right`) when the stair faces *along* the
 * mirrored axis. A stair facing north that gets mirrored across X keeps its
 * facing — correctly — but vanilla returns it completely untouched, so the corner
 * still points the way it did before. Mirroring flips handedness no matter which
 * way the stair faces, so that case needs finishing by hand.
 *
 * The tell is cheap: for stairs, vanilla's mirror rotates the facing 180 degrees
 * whenever it handled the block, so a mirrored state that compares equal to the
 * original is exactly the case vanilla skipped.
 */
object StairMirrorPolicy {
    /**
     * @param isStairs whether the block is a stairs block; nothing else in
     *   vanilla carries this left/right handedness without mirroring it already
     *   (doors, for instance, cycle their hinge for every mirror).
     * @param mirrorLeftStateUnchanged whether vanilla's mirror returned the state
     *   it was given.
     */
    fun needsHandednessFlip(
        isStairs: Boolean,
        mirrorLeftStateUnchanged: Boolean,
    ): Boolean = isStairs && mirrorLeftStateUnchanged
}
