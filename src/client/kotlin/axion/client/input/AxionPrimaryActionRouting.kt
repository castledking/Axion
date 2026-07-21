package axion.client.input

/** Keeps infinite-reach ownership ahead of the generic symmetry side effect. */
object AxionPrimaryActionRouting {
    fun route(
        handleBulldozerInfiniteReach: () -> Boolean,
        handleInfiniteReach: () -> Boolean,
        handleGenericSymmetry: () -> Unit,
    ): Boolean {
        if (handleBulldozerInfiniteReach()) return true
        if (handleInfiniteReach()) return true
        handleGenericSymmetry()
        return false
    }
}
