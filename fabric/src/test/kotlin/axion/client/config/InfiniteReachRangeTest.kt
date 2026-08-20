package axion.client.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InfiniteReachRangeTest {
    @Test
    fun `unlimited range is displayed as infinity and uses safe raycast ceiling`() {
        assertEquals("∞", InfiniteReachRange.display(null))
        assertEquals(256.0, InfiniteReachRange.effective(null))
        assertNull(InfiniteReachRange.parse("∞"))
        assertNull(InfiniteReachRange.parse("infinity"))
    }

    @Test
    fun `finite range can be customized and is clamped to supported bounds`() {
        assertEquals(48.0, InfiniteReachRange.parse("48"))
        assertEquals(6.0, InfiniteReachRange.parse("2"))
        assertEquals(256.0, InfiniteReachRange.parse("500"))
        assertEquals("48", InfiniteReachRange.display(48.0))
    }
}
