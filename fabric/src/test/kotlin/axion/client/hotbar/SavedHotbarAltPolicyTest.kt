package axion.client.hotbar

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedHotbarAltPolicyTest {
    @Test
    fun freshAltPressInSpectatorRequestsCreativeMode() {
        assertTrue(
            SavedHotbarAltPolicy.shouldRequestCreative(
                wasAltDown = false,
                isAltDown = true,
                isSpectator = true,
                menuEligible = true,
            ),
        )
    }

    @Test
    fun heldAltDoesNotBounceSpectatorButtonBackToCreative() {
        assertFalse(
            SavedHotbarAltPolicy.shouldRequestCreative(
                wasAltDown = true,
                isAltDown = true,
                isSpectator = true,
                menuEligible = true,
            ),
        )
    }

    @Test
    fun altPressOutsideTheSavedHotbarContextDoesNothing() {
        assertFalse(
            SavedHotbarAltPolicy.shouldRequestCreative(
                wasAltDown = false,
                isAltDown = true,
                isSpectator = true,
                menuEligible = false,
            ),
        )
    }

    @Test
    fun creativePlayersDoNotSendASecondGameModeRequest() {
        assertFalse(
            SavedHotbarAltPolicy.shouldRequestCreative(
                wasAltDown = false,
                isAltDown = true,
                isSpectator = false,
                menuEligible = true,
            ),
        )
    }
}
