package axion.client.render

import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewBlockIdentityPolicyTest {
    @Test
    fun `remote Paper preview uses a neutral identity for non-air blocks`() {
        val result = PreviewBlockIdentityPolicy.resolve(
            original = "fake-gold",
            neutral = "neutral",
            isAir = false,
            remoteAxionSessionAvailable = true,
        )

        assertEquals("neutral", result)
    }

    @Test
    fun `singleplayer preview preserves the captured block identity`() {
        val result = PreviewBlockIdentityPolicy.resolve(
            original = "stone",
            neutral = "neutral",
            isAir = false,
            remoteAxionSessionAvailable = false,
        )

        assertEquals("stone", result)
    }

    @Test
    fun `remote Paper preview preserves air occupancy`() {
        val result = PreviewBlockIdentityPolicy.resolve(
            original = "air",
            neutral = "neutral",
            isAir = true,
            remoteAxionSessionAvailable = true,
        )

        assertEquals("air", result)
    }

    @Test
    fun `remote destination is normalized but deliberate move-source glass is preserved`() {
        assertEquals(
            true,
            PreviewBlockIdentityPolicy.shouldNormalizeRemoteIdentity(
                remoteAxionSessionAvailable = true,
                sessionTag = "placement-destination",
            ),
        )
        assertEquals(
            false,
            PreviewBlockIdentityPolicy.shouldNormalizeRemoteIdentity(
                remoteAxionSessionAvailable = true,
                sessionTag = PreviewBlockIdentityPolicy.MOVE_SOURCE_SESSION_TAG,
            ),
        )
    }

    @Test
    fun `move-source glass skips ambient occlusion so sky light reads through the replacement`() {
        assertEquals(
            false,
            PreviewBlockIdentityPolicy.usesAmbientOcclusion(
                "ghost:${PreviewBlockIdentityPolicy.MOVE_SOURCE_SESSION_TAG}",
            ),
        )
    }

    @Test
    fun `destination ghosts keep ambient occlusion so the previewed shape stays legible`() {
        assertEquals(true, PreviewBlockIdentityPolicy.usesAmbientOcclusion("ghost:placement-destination"))
        assertEquals(true, PreviewBlockIdentityPolicy.usesAmbientOcclusion("ghost:default"))
    }
}
