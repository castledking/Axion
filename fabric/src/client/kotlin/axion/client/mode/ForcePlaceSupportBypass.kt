package axion.client.mode

/**
 * A scope in which blockstate support checks are answered "yes".
 *
 * Minecraft expresses every attachment rule through one virtual call —
 * `canPlaceAt` in yarn, `canSurvive` in the 26.x official namespace — so a
 * lantern refusing to hang under another lantern, redstone refusing to sit on a
 * lantern, and a torch refusing a wall are all the same check. Suppressing it is
 * what lets Force Place cover every block without a hand-written list.
 *
 * The suppression is deliberately scoped rather than global. `canPlaceAt` is also
 * consulted by world ticking and neighbour updates, and answering "yes" there
 * would keep blocks alive that vanilla means to break. The scope is entered only
 * around Axion's own placement resolution, and it is per-thread so the
 * integrated server tick never observes it.
 *
 * IMPORTANT: this must stay outside `axion.mixin.client.*`. Mixin's class-load
 * policy refuses to resolve classes inside a defined mixin package when they are
 * referenced from a mixin handler — the same constraint that keeps
 * [EntityNoClipSupport] where it is.
 */
object ForcePlaceSupportBypass {
    private val depth = ThreadLocal.withInitial { 0 }

    /** True while the calling thread is resolving a force placement. */
    @JvmStatic
    fun isActive(): Boolean = depth.get() > 0

    /**
     * Runs [block] with support checks suppressed when [enabled].
     *
     * Nesting is counted rather than flagged so an inner resolution cannot clear
     * the bypass an outer one is relying on.
     */
    fun <T> withBypass(enabled: Boolean, block: () -> T): T {
        if (!enabled) {
            return block()
        }

        depth.set(depth.get() + 1)
        try {
            return block()
        } finally {
            val remaining = depth.get() - 1
            if (remaining <= 0) {
                depth.remove()
            } else {
                depth.set(remaining)
            }
        }
    }
}
