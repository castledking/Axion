package axion.client.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BlockWriteUpdatePolicyTest {
    @Test
    fun `edit flags notify clients without notifying neighbors`() {
        assertEquals(0, BlockWriteUpdatePolicy.LEGACY_NO_PHYSICS_FLAGS and NOTIFY_NEIGHBORS)
        assertEquals(0, BlockWriteUpdatePolicy.MODERN_NO_PHYSICS_FLAGS and NOTIFY_NEIGHBORS)
        assertNotEquals(0, BlockWriteUpdatePolicy.LEGACY_NO_PHYSICS_FLAGS and BlockWriteUpdatePolicy.NOTIFY_CLIENTS)
        assertNotEquals(0, BlockWriteUpdatePolicy.MODERN_NO_PHYSICS_FLAGS and BlockWriteUpdatePolicy.NOTIFY_CLIENTS)
    }

    @Test
    fun `modern flags suppress placement and replacement callbacks`() {
        assertNotEquals(0, BlockWriteUpdatePolicy.MODERN_NO_PHYSICS_FLAGS and BlockWriteUpdatePolicy.KEEP_KNOWN_SHAPE)
        assertNotEquals(0, BlockWriteUpdatePolicy.MODERN_NO_PHYSICS_FLAGS and BlockWriteUpdatePolicy.SUPPRESS_DROPS)
        assertNotEquals(0, BlockWriteUpdatePolicy.MODERN_NO_PHYSICS_FLAGS and BlockWriteUpdatePolicy.SKIP_REPLACED_CALLBACK)
        assertNotEquals(0, BlockWriteUpdatePolicy.MODERN_NO_PHYSICS_FLAGS and BlockWriteUpdatePolicy.SKIP_ADDED_CALLBACK)
    }

    private companion object {
        const val NOTIFY_NEIGHBORS = 1
    }
}
