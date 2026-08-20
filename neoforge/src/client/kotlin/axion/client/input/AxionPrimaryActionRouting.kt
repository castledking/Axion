package axion.client.input

import axion.client.mode.ClientModeController
import axion.client.symmetry.SymmetryBreakController
import net.minecraft.client.MinecraftClient

/** Keeps infinite-reach ownership ahead of the generic symmetry side effect. */
object AxionPrimaryActionRouting {
    /**
     * Runtime entry point for mixins.
     *
     * Keep the lambdas in this ordinary class. Capturing a mixin instance in a
     * Kotlin-generated runtime lambda leaves a direct mixin-class descriptor in
     * the target bytecode, which legacy Mixin loaders reject.
     */
    fun route(client: MinecraftClient): Boolean = route(
        handleBulldozerInfiniteReach = {
            ClientModeController.handleBulldozerInfiniteReachBreaking(client)
        },
        handleInfiniteReach = {
            ClientModeController.handleInfiniteReachBreaking(client)
        },
        handleGenericSymmetry = {
            SymmetryBreakController.handlePrimaryAction(client)
        },
    )

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
