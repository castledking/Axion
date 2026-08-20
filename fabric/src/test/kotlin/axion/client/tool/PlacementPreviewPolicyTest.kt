package axion.client.tool

import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.protocol.EntitySelectionMask
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlacementPreviewPolicyTest {
    @Test
    fun `source replacement is absent throughout selection before scrolling`() {
        val region = BlockRegion(BlockPos.ORIGIN, BlockPos.ORIGIN)
        val clipboard = ClipboardBuffer(Vec3i(1, 1, 1), emptyList())

        assertNull(PlacementPreviewPolicy.activePreview(CloneToolState.Idle))
        assertNull(
            PlacementPreviewPolicy.activePreview(
                CloneToolState.FirstCornerSet(PlacementToolMode.MOVE, BlockPos.ORIGIN),
            ),
        )
        assertNull(
            PlacementPreviewPolicy.activePreview(
                CloneToolState.RegionDefined(
                    mode = PlacementToolMode.MOVE,
                    firstCorner = BlockPos.ORIGIN,
                    secondCorner = BlockPos.ORIGIN,
                    region = region,
                    clipboardBuffer = clipboard,
                ),
            ),
        )
    }

    @Test
    fun `move source replacement starts with the post-scroll preview`() {
        val preview = preview(PlacementToolMode.MOVE)

        assertSame(
            preview,
            PlacementPreviewPolicy.activePreview(CloneToolState.PreviewingOffset(preview)),
        )
        assertSame(
            preview,
            PlacementPreviewPolicy.activePreview(CloneToolState.AwaitingConfirm(preview)),
        )
        assertTrue(PlacementPreviewPolicy.shouldRenderMoveSourceReplacement(preview))
    }

    @Test
    fun `clone preview never renders a move source replacement`() {
        val preview = preview(PlacementToolMode.CLONE)

        assertFalse(PlacementPreviewPolicy.shouldRenderMoveSourceReplacement(preview))
    }

    private fun preview(mode: PlacementToolMode): ClonePreviewState {
        val origin = BlockPos.ORIGIN
        val region = BlockRegion(origin, origin)
        val clipboard = ClipboardBuffer(Vec3i(1, 1, 1), emptyList())
        return ClonePreviewState(
            mode = mode,
            firstCorner = origin,
            sourceRegion = region,
            sourceClipboardBuffer = clipboard,
            destinationClipboardBuffer = clipboard,
            anchor = origin,
            offset = Vec3i.ZERO,
            destinationRegion = region,
            entitySelection = EntitySelectionMask.sparseOffsets(emptyList()),
        )
    }
}
