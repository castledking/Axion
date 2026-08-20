package axion.client.mode

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForcePlaceSupportBypassTest {
    @Test
    fun `bypass is inactive by default`() {
        assertFalse(ForcePlaceSupportBypass.isActive())
    }

    @Test
    fun `bypass is only active inside an enabled scope`() {
        ForcePlaceSupportBypass.withBypass(enabled = true) {
            assertTrue(ForcePlaceSupportBypass.isActive())
        }
        assertFalse(ForcePlaceSupportBypass.isActive())
    }

    @Test
    fun `a disabled scope leaves support checks alone`() {
        ForcePlaceSupportBypass.withBypass(enabled = false) {
            assertFalse(ForcePlaceSupportBypass.isActive())
        }
    }

    @Test
    fun `nested scopes do not clear the outer bypass early`() {
        ForcePlaceSupportBypass.withBypass(enabled = true) {
            ForcePlaceSupportBypass.withBypass(enabled = true) {
                assertTrue(ForcePlaceSupportBypass.isActive())
            }
            assertTrue(ForcePlaceSupportBypass.isActive())
        }
        assertFalse(ForcePlaceSupportBypass.isActive())
    }

    @Test
    fun `a throwing body still releases the bypass`() {
        runCatching {
            ForcePlaceSupportBypass.withBypass(enabled = true) { error("boom") }
        }
        assertFalse(ForcePlaceSupportBypass.isActive())
    }
}
