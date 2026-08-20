package axion.client.tool

import axion.client.AxionClientState
import axion.common.model.AxionSubtool
import axion.common.model.BlockRegion
import axion.common.model.ClipboardBuffer
import axion.common.model.SelectionState
import net.minecraft.util.math.BlockPos

/**
 * Carries a region selection across a subtool switch.
 *
 * A selection costs two placed corners, so switching Move to Stack should not
 * silently throw it away. Once a tool has started previewing, though, the
 * selection is no longer just a region — it has an offset, a transform and a
 * captured clipboard, none of which another tool can adopt — so the switch is
 * refused instead of quietly discarding that work. Extrude is the exception:
 * its passive hover preview is rebuilt each tick and contains no user work.
 *
 * The handoff has to move the state itself rather than call the outgoing
 * controller's `reset()`. Every `reset()` also clears the shared
 * [AxionClientState.clipboardState] and [AxionClientState.selectionState], which
 * are exactly the things being handed over. Each controller's `onEndTick` only
 * resets when its own state is not `Idle`, so leaving the outgoing tool `Idle`
 * is what stops the next tick from undoing the handoff.
 */
object ToolSelectionHandoff {
    /** A two-corner region selection, in the form every region tool stores it. */
    data class RegionSelection(
        val firstCorner: BlockPos,
        val secondCorner: BlockPos,
        val region: BlockRegion,
        val clipboardBuffer: ClipboardBuffer?,
    )

    /** How far a tool has taken its selection, in the order it passes through. */
    enum class Phase {
        IDLE,
        PARTIAL,
        SELECTED,
        PREVIEWING,
    }

    /** Switching is allowed right up until the tool starts previewing. */
    fun allowsSwitch(phase: Phase): Boolean = phase != Phase.PREVIEWING

    /** True when [phase] holds a selection another region tool can adopt. */
    fun carriesSelection(phase: Phase): Boolean = phase == Phase.SELECTED

    /** Extrude's hover result is rebuilt every tick and carries no user work. */
    fun extrudePhase(@Suppress("UNUSED_PARAMETER") hasHoverPreview: Boolean): Phase = Phase.IDLE

    fun phaseOf(subtool: AxionSubtool): Phase = when (subtool) {
        AxionSubtool.MOVE, AxionSubtool.CLONE -> when (AxionClientState.placementToolState) {
            CloneToolState.Idle -> Phase.IDLE
            is CloneToolState.FirstCornerSet -> Phase.PARTIAL
            is CloneToolState.RegionDefined -> Phase.SELECTED
            is CloneToolState.PreviewingOffset,
            is CloneToolState.AwaitingConfirm,
                -> Phase.PREVIEWING
        }

        AxionSubtool.STACK -> when (AxionClientState.stackToolState) {
            StackToolState.Idle -> Phase.IDLE
            is StackToolState.FirstCornerSet -> Phase.PARTIAL
            is StackToolState.RegionDefined -> Phase.SELECTED
            is StackToolState.PreviewingStack -> Phase.PREVIEWING
        }

        AxionSubtool.SMEAR -> when (AxionClientState.smearToolState) {
            SmearToolState.Idle -> Phase.IDLE
            is SmearToolState.FirstCornerSet -> Phase.PARTIAL
            is SmearToolState.RegionDefined -> Phase.SELECTED
            is SmearToolState.PreviewingSmear -> Phase.PREVIEWING
        }

        AxionSubtool.ERASE -> when (AxionClientState.eraseToolState) {
            EraseToolState.Idle -> Phase.IDLE
            is EraseToolState.FirstCornerSet -> Phase.PARTIAL
            // Erase commits straight from its region, so it never previews.
            is EraseToolState.RegionDefined -> Phase.SELECTED
        }

        AxionSubtool.EXTRUDE -> extrudePhase(AxionClientState.extrudeToolState is ExtrudeToolState.Previewing)

        // Symmetry keeps no region of its own, so it never blocks or carries.
        AxionSubtool.SETUP_SYMMETRY -> Phase.IDLE
    }

    /**
     * Moves any adoptable selection from [from] to [to] and reports whether the
     * switch may happen at all.
     *
     * Returns false without touching any state when [from] is already previewing.
     * A target that keeps no region of its own (Extrude, Symmetry) is still a
     * legal switch; the selection simply stays where it is, so stepping back to
     * the original tool finds it intact.
     */
    fun handOff(from: AxionSubtool, to: AxionSubtool): Boolean {
        if (from == to) return true
        val phase = phaseOf(from)
        if (!allowsSwitch(phase)) return false
        if (!carriesSelection(phase) || !to.usesRegionSelection) return true

        val selection = selectionOf(from) ?: return true
        clear(from)
        adopt(to, selection)
        return true
    }

    private fun selectionOf(subtool: AxionSubtool): RegionSelection? = when (subtool) {
        AxionSubtool.MOVE, AxionSubtool.CLONE ->
            (AxionClientState.placementToolState as? CloneToolState.RegionDefined)?.let {
                RegionSelection(it.firstCorner, it.secondCorner, it.region, it.clipboardBuffer)
            }

        AxionSubtool.STACK ->
            (AxionClientState.stackToolState as? StackToolState.RegionDefined)?.let {
                RegionSelection(it.firstCorner, it.secondCorner, it.region, it.clipboardBuffer)
            }

        AxionSubtool.SMEAR ->
            (AxionClientState.smearToolState as? SmearToolState.RegionDefined)?.let {
                RegionSelection(it.firstCorner, it.secondCorner, it.region, it.clipboardBuffer)
            }

        AxionSubtool.ERASE ->
            (AxionClientState.eraseToolState as? EraseToolState.RegionDefined)?.let {
                RegionSelection(it.firstCorner, it.secondCorner, it.region, it.clipboardBuffer)
            }

        AxionSubtool.EXTRUDE, AxionSubtool.SETUP_SYMMETRY -> null
    }

    private fun clear(subtool: AxionSubtool) {
        when (subtool) {
            AxionSubtool.MOVE, AxionSubtool.CLONE ->
                AxionClientState.updatePlacementToolState(CloneToolState.Idle)

            AxionSubtool.STACK -> AxionClientState.updateStackToolState(StackToolState.Idle)
            AxionSubtool.SMEAR -> AxionClientState.updateSmearToolState(SmearToolState.Idle)
            AxionSubtool.ERASE -> AxionClientState.updateEraseToolState(EraseToolState.Idle)
            AxionSubtool.EXTRUDE, AxionSubtool.SETUP_SYMMETRY -> Unit
        }
    }

    private fun adopt(subtool: AxionSubtool, selection: RegionSelection) {
        when (subtool) {
            AxionSubtool.MOVE, AxionSubtool.CLONE -> {
                // Placement shares one state between Move and Clone and resets
                // itself on the next tick if the mode does not match the subtool.
                val mode = PlacementToolMode.fromSubtool(subtool) ?: return
                AxionClientState.updatePlacementToolState(
                    CloneToolState.RegionDefined(
                        mode = mode,
                        firstCorner = selection.firstCorner,
                        secondCorner = selection.secondCorner,
                        region = selection.region,
                        clipboardBuffer = selection.clipboardBuffer,
                    ),
                )
            }

            AxionSubtool.STACK -> AxionClientState.updateStackToolState(
                StackToolState.RegionDefined(
                    firstCorner = selection.firstCorner,
                    secondCorner = selection.secondCorner,
                    region = selection.region,
                    clipboardBuffer = selection.clipboardBuffer,
                ),
            )

            AxionSubtool.SMEAR -> AxionClientState.updateSmearToolState(
                SmearToolState.RegionDefined(
                    firstCorner = selection.firstCorner,
                    secondCorner = selection.secondCorner,
                    region = selection.region,
                    clipboardBuffer = selection.clipboardBuffer,
                ),
            )

            AxionSubtool.ERASE -> AxionClientState.updateEraseToolState(
                EraseToolState.RegionDefined(
                    firstCorner = selection.firstCorner,
                    secondCorner = selection.secondCorner,
                    region = selection.region,
                    clipboardBuffer = selection.clipboardBuffer,
                ),
            )

            AxionSubtool.EXTRUDE, AxionSubtool.SETUP_SYMMETRY -> return
        }

        AxionClientState.updateSelection(
            SelectionState.RegionDefined(selection.firstCorner, selection.secondCorner),
        )
    }
}
