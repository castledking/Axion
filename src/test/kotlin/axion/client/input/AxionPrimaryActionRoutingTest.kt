package axion.client.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AxionPrimaryActionRoutingTest {
    @Test
    fun `bulldozer reach ownership prevents generic symmetry dispatch`() {
        val calls = mutableListOf<String>()
        val owned = AxionPrimaryActionRouting.route(
            handleBulldozerInfiniteReach = { calls += "bulldozer"; true },
            handleInfiniteReach = { calls += "reach"; true },
            handleGenericSymmetry = { calls += "symmetry" },
        )

        assertTrue(owned)
        assertEquals(listOf("bulldozer"), calls)
    }

    @Test
    fun `ordinary reach ownership prevents generic symmetry dispatch`() {
        val calls = mutableListOf<String>()
        val owned = AxionPrimaryActionRouting.route(
            handleBulldozerInfiniteReach = { calls += "bulldozer"; false },
            handleInfiniteReach = { calls += "reach"; true },
            handleGenericSymmetry = { calls += "symmetry" },
        )

        assertTrue(owned)
        assertEquals(listOf("bulldozer", "reach"), calls)
    }

    @Test
    fun `generic symmetry runs only when reach does not own the click`() {
        val calls = mutableListOf<String>()
        val owned = AxionPrimaryActionRouting.route(
            handleBulldozerInfiniteReach = { calls += "bulldozer"; false },
            handleInfiniteReach = { calls += "reach"; false },
            handleGenericSymmetry = { calls += "symmetry" },
        )

        assertFalse(owned)
        assertEquals(listOf("bulldozer", "reach", "symmetry"), calls)
    }
}
