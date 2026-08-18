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

    @Test
    fun `capability writes notify neighbors unless updates are suppressed`() {
        val notifying = BlockWriteUpdatePolicy.capabilityFlags(
            suppressUpdates = false,
            modernCallbacksAvailable = true,
        )

        assertNotEquals(0, notifying and NOTIFY_NEIGHBORS)
        assertNotEquals(0, notifying and BlockWriteUpdatePolicy.NOTIFY_CLIENTS)
        // Gravity and redstone must be free to react, so none of the
        // callback-skipping bits may leak into the notifying flags.
        assertEquals(0, notifying and BlockWriteUpdatePolicy.SKIP_ADDED_CALLBACK)
        assertEquals(0, notifying and BlockWriteUpdatePolicy.SKIP_REPLACED_CALLBACK)
        assertEquals(0, notifying and BlockWriteUpdatePolicy.KEEP_KNOWN_SHAPE)
    }

    @Test
    fun `suppressed capability writes fall back to the version's quiet flags`() {
        assertEquals(
            BlockWriteUpdatePolicy.MODERN_NO_PHYSICS_FLAGS,
            BlockWriteUpdatePolicy.capabilityFlags(suppressUpdates = true, modernCallbacksAvailable = true),
        )
        assertEquals(
            BlockWriteUpdatePolicy.LEGACY_NO_PHYSICS_FLAGS,
            BlockWriteUpdatePolicy.capabilityFlags(suppressUpdates = true, modernCallbacksAvailable = false),
        )
        assertEquals(
            0,
            BlockWriteUpdatePolicy.capabilityFlags(suppressUpdates = true, modernCallbacksAvailable = false) and
                NOTIFY_NEIGHBORS,
        )
    }

    private companion object {
        const val NOTIFY_NEIGHBORS = 1
    }
}
