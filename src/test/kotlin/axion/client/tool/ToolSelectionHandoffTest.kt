package axion.client.tool

import axion.client.AxionClientState
import axion.common.model.AxionSubtool
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.common.model.SelectionState
import axion.protocol.EntitySelectionMask
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ToolSelectionHandoffTest {
    private val firstCorner = BlockPos(10, 20, 30)
    private val secondCorner = BlockPos(12, 20, 34)
    private val region = BlockRegion(firstCorner, secondCorner)
    private val clipboard = ClipboardBuffer(Vec3i(3, 1, 5), emptyList())

    @AfterTest
    fun resetToolStates() {
        AxionClientState.updatePlacementToolState(CloneToolState.Idle)
        AxionClientState.updateStackToolState(StackToolState.Idle)
        AxionClientState.updateSmearToolState(SmearToolState.Idle)
        AxionClientState.updateEraseToolState(EraseToolState.Idle)
        AxionClientState.updateSelection(SelectionState.Idle)
    }

    @Test
    fun `a move selection carries over to stack`() {
        AxionClientState.updatePlacementToolState(placementSelection(PlacementToolMode.MOVE))

        assertTrue(ToolSelectionHandoff.handOff(from = AxionSubtool.MOVE, to = AxionSubtool.STACK))

        val stack = assertIs<StackToolState.RegionDefined>(AxionClientState.stackToolState)
        assertEquals(firstCorner, stack.firstCorner)
        assertEquals(secondCorner, stack.secondCorner)
        assertEquals(region, stack.region)
        assertEquals(clipboard, stack.clipboardBuffer)
        assertEquals(
            SelectionState.RegionDefined(firstCorner, secondCorner),
            AxionClientState.selectionState,
        )
    }

    @Test
    fun `the outgoing tool is left idle so its end-of-tick reset does not undo the handoff`() {
        AxionClientState.updatePlacementToolState(placementSelection(PlacementToolMode.CLONE))

        ToolSelectionHandoff.handOff(from = AxionSubtool.CLONE, to = AxionSubtool.ERASE)

        assertEquals(CloneToolState.Idle, AxionClientState.placementToolState)
        assertIs<EraseToolState.RegionDefined>(AxionClientState.eraseToolState)
    }

    @Test
    fun `move to clone rewrites the shared placement mode instead of resetting it`() {
        AxionClientState.updatePlacementToolState(placementSelection(PlacementToolMode.MOVE))

        assertTrue(ToolSelectionHandoff.handOff(from = AxionSubtool.MOVE, to = AxionSubtool.CLONE))

        val placement = assertIs<CloneToolState.RegionDefined>(AxionClientState.placementToolState)
        assertEquals(PlacementToolMode.CLONE, placement.mode)
        assertEquals(region, placement.region)
    }

    @Test
    fun `switching is refused once the preview is being scrolled`() {
        AxionClientState.updatePlacementToolState(CloneToolState.PreviewingOffset(preview()))

        assertFalse(ToolSelectionHandoff.handOff(from = AxionSubtool.MOVE, to = AxionSubtool.STACK))
        assertIs<CloneToolState.PreviewingOffset>(AxionClientState.placementToolState)
        assertEquals(StackToolState.Idle, AxionClientState.stackToolState)
    }

    @Test
    fun `switching is refused while a placement preview awaits confirmation`() {
        AxionClientState.updatePlacementToolState(CloneToolState.AwaitingConfirm(preview()))

        assertFalse(ToolSelectionHandoff.handOff(from = AxionSubtool.MOVE, to = AxionSubtool.ERASE))
        assertEquals(EraseToolState.Idle, AxionClientState.eraseToolState)
    }

    @Test
    fun `a half-placed selection is allowed to switch but has nothing to carry`() {
        AxionClientState.updatePlacementToolState(
            CloneToolState.FirstCornerSet(PlacementToolMode.MOVE, firstCorner),
        )

        assertTrue(ToolSelectionHandoff.handOff(from = AxionSubtool.MOVE, to = AxionSubtool.SMEAR))
        assertEquals(SmearToolState.Idle, AxionClientState.smearToolState)
    }

    @Test
    fun `a target that keeps no region leaves the selection where it is`() {
        AxionClientState.updatePlacementToolState(placementSelection(PlacementToolMode.MOVE))

        assertTrue(ToolSelectionHandoff.handOff(from = AxionSubtool.MOVE, to = AxionSubtool.EXTRUDE))

        val placement = assertIs<CloneToolState.RegionDefined>(AxionClientState.placementToolState)
        assertEquals(region, placement.region)
    }

    @Test
    fun `only the previewing phase blocks a switch`() {
        assertTrue(ToolSelectionHandoff.allowsSwitch(ToolSelectionHandoff.Phase.IDLE))
        assertTrue(ToolSelectionHandoff.allowsSwitch(ToolSelectionHandoff.Phase.PARTIAL))
        assertTrue(ToolSelectionHandoff.allowsSwitch(ToolSelectionHandoff.Phase.SELECTED))
        assertFalse(ToolSelectionHandoff.allowsSwitch(ToolSelectionHandoff.Phase.PREVIEWING))
    }

    @Test
    fun `passive extrude hover preview does not block alt tool switching`() {
        assertTrue(ToolSelectionHandoff.allowsSwitch(ToolSelectionHandoff.extrudePhase(hasHoverPreview = true)))
    }

    private fun placementSelection(mode: PlacementToolMode) = CloneToolState.RegionDefined(
        mode = mode,
        firstCorner = firstCorner,
        secondCorner = secondCorner,
        region = region,
        clipboardBuffer = clipboard,
    )

    private fun preview() = ClonePreviewState(
        mode = PlacementToolMode.MOVE,
        firstCorner = firstCorner,
        sourceRegion = region,
        sourceClipboardBuffer = clipboard,
        destinationClipboardBuffer = clipboard,
        anchor = firstCorner,
        offset = Vec3i(0, 1, 0),
        destinationRegion = region,
        entitySelection = EntitySelectionMask.sparseOffsets(emptyList()),
    )
}
