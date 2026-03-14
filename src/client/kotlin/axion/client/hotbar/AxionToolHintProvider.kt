package axion.client.hotbar

import axion.client.AxionClientState
import axion.client.selection.AxionTarget
import axion.client.selection.SelectionController
import axion.client.selection.blockPosOrNull
import axion.client.symmetry.ActiveSymmetryConfig
import axion.client.tool.AxionToolSelectionController
import axion.client.tool.CloneToolState
import axion.client.tool.EraseToolState
import axion.client.tool.ExtrudeToolState
import axion.client.tool.PlacementToolMode
import axion.client.tool.RepeatRegionPreview
import axion.client.tool.SmearToolState
import axion.client.tool.StackToolState
import axion.common.model.AxionSubtool
import axion.common.model.BlockRegion
import axion.common.model.SelectionState
import axion.common.model.SymmetryState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import kotlin.math.abs

object AxionToolHintProvider {
    fun currentPanel(): ToolHintPanel? {
        if (!AxionToolSelectionController.isAxionSlotActive()) {
            return null
        }

        return when (val subtool = AxionToolSelectionController.selectedSubtool()) {
            AxionSubtool.MOVE -> placementPanel(PlacementToolMode.MOVE)
            AxionSubtool.CLONE -> placementPanel(PlacementToolMode.CLONE)
            AxionSubtool.STACK -> stackPanel()
            AxionSubtool.SMEAR -> smearPanel()
            AxionSubtool.ERASE -> erasePanel()
            AxionSubtool.EXTRUDE -> extrudePanel()
            AxionSubtool.SETUP_SYMMETRY -> symmetryPanel()
        }
    }

    private fun placementPanel(mode: PlacementToolMode): ToolHintPanel {
        val state = AxionClientState.placementToolState
        val preview = when (state) {
            is CloneToolState.PreviewingOffset -> state.preview
            is CloneToolState.AwaitingConfirm -> state.preview
            else -> null
        }
        val title = if (mode == PlacementToolMode.MOVE) "AXION - Move" else "AXION - Clone"
        val subtitle = when (state) {
            CloneToolState.Idle -> "Selecting source"
            is CloneToolState.FirstCornerSet -> "First corner set"
            is CloneToolState.RegionDefined -> "Selection ready"
            is CloneToolState.PreviewingOffset,
            is CloneToolState.AwaitingConfirm,
                -> "Previewing destination"
        }
        val entries = when (state) {
            CloneToolState.Idle -> listOf(
                ToolHintEntry("LMB", "Set first corner"),
                ToolHintEntry("RMB", "Set second corner"),
                ToolHintEntry("MMB", "Expand face after selection"),
                ToolHintEntry("Scroll", "Start preview"),
            )

            is CloneToolState.FirstCornerSet -> listOf(
                ToolHintEntry("LMB", "Reset first corner"),
                ToolHintEntry("RMB", "Set second corner"),
                ToolHintEntry("Scroll", "Disabled until region exists"),
            )

            is CloneToolState.RegionDefined -> listOf(
                ToolHintEntry("MMB", "Expand nearest face"),
                ToolHintEntry("Scroll", "Move preview"),
                ToolHintEntry("LMB", "Restart source selection"),
                ToolHintEntry("RMB", "Set second corner again"),
            )

            is CloneToolState.PreviewingOffset,
            is CloneToolState.AwaitingConfirm,
                -> listOf(
                    ToolHintEntry("Scroll", "Adjust preview offset"),
                    ToolHintEntry("RMB", "Confirm placement"),
                    ToolHintEntry("LMB", "Cancel preview"),
                    ToolHintEntry("MMB", "Reanchor preview"),
                )
        }

        return ToolHintPanel(
            title = title,
            subtitle = subtitle,
            entries = entries,
            statusLines = buildList {
                currentSelectionSize()?.let { add("Selection: $it") }
                targetSummary()?.let { add(it) }
                preview?.let {
                    add("Offset: ${formatAxis(it.offset)} x ${formatStepLength(it.offset)}")
                    add("Destination: ${formatRegionSize(it.destinationRegion)}")
                }
            },
            footer = symmetrySummary(),
        )
    }

    private fun stackPanel(): ToolHintPanel {
        val state = AxionClientState.stackToolState
        val preview = (state as? StackToolState.PreviewingStack)?.preview
        val subtitle = when (state) {
            StackToolState.Idle -> "Selecting source"
            is StackToolState.FirstCornerSet -> "First corner set"
            is StackToolState.RegionDefined -> "Selection ready"
            is StackToolState.PreviewingStack -> "Previewing repeats"
        }
        val entries = when (state) {
            StackToolState.Idle -> listOf(
                ToolHintEntry("LMB", "Set first corner"),
                ToolHintEntry("RMB", "Set second corner"),
                ToolHintEntry("MMB", "Expand face after selection"),
                ToolHintEntry("Scroll", "Pick axis and repeat"),
            )

            is StackToolState.FirstCornerSet -> listOf(
                ToolHintEntry("LMB", "Reset first corner"),
                ToolHintEntry("RMB", "Set second corner"),
            )

            is StackToolState.RegionDefined -> listOf(
                ToolHintEntry("MMB", "Expand nearest face"),
                ToolHintEntry("Scroll", "Start stack preview"),
                ToolHintEntry("LMB", "Restart selection"),
                ToolHintEntry("RMB", "Set second corner again"),
            )

            is StackToolState.PreviewingStack -> listOf(
                ToolHintEntry("Scroll", "Adjust repeat count"),
                ToolHintEntry("RMB", "Confirm stack"),
                ToolHintEntry("LMB", "Cancel preview"),
            )
        }

        return ToolHintPanel(
            title = "AXION - Stack",
            subtitle = subtitle,
            entries = entries,
            statusLines = repeatStatusLines(preview, "Mode: Replace-all"),
            footer = symmetrySummary(),
        )
    }

    private fun smearPanel(): ToolHintPanel {
        val state = AxionClientState.smearToolState
        val preview = (state as? SmearToolState.PreviewingSmear)?.preview
        val subtitle = when (state) {
            SmearToolState.Idle -> "Selecting source"
            is SmearToolState.FirstCornerSet -> "First corner set"
            is SmearToolState.RegionDefined -> "Selection ready"
            is SmearToolState.PreviewingSmear -> "Previewing air-only smear"
        }
        val entries = when (state) {
            SmearToolState.Idle -> listOf(
                ToolHintEntry("LMB", "Set first corner"),
                ToolHintEntry("RMB", "Set second corner"),
                ToolHintEntry("MMB", "Expand face after selection"),
                ToolHintEntry("Scroll", "Pick axis and repeat"),
            )

            is SmearToolState.FirstCornerSet -> listOf(
                ToolHintEntry("LMB", "Reset first corner"),
                ToolHintEntry("RMB", "Set second corner"),
            )

            is SmearToolState.RegionDefined -> listOf(
                ToolHintEntry("MMB", "Expand nearest face"),
                ToolHintEntry("Scroll", "Start smear preview"),
                ToolHintEntry("LMB", "Restart selection"),
                ToolHintEntry("RMB", "Set second corner again"),
            )

            is SmearToolState.PreviewingSmear -> listOf(
                ToolHintEntry("Scroll", "Adjust repeat count"),
                ToolHintEntry("RMB", "Confirm smear"),
                ToolHintEntry("LMB", "Cancel preview"),
            )
        }

        return ToolHintPanel(
            title = "AXION - Smear",
            subtitle = subtitle,
            entries = entries,
            statusLines = repeatStatusLines(preview, "Mode: Air-only"),
            footer = symmetrySummary(),
        )
    }

    private fun erasePanel(): ToolHintPanel {
        val state = AxionClientState.eraseToolState
        val subtitle = when (state) {
            EraseToolState.Idle -> "Selecting region"
            is EraseToolState.FirstCornerSet -> "First corner set"
            is EraseToolState.RegionDefined -> "Selection ready"
        }
        val entries = when (state) {
            EraseToolState.Idle -> listOf(
                ToolHintEntry("LMB", "Set first corner"),
                ToolHintEntry("RMB", "Set second corner"),
                ToolHintEntry("MMB", "Expand face after selection"),
            )

            is EraseToolState.FirstCornerSet -> listOf(
                ToolHintEntry("LMB", "Reset first corner"),
                ToolHintEntry("RMB", "Set second corner"),
            )

            is EraseToolState.RegionDefined -> listOf(
                ToolHintEntry("Del", "Erase selection"),
                ToolHintEntry("MMB", "Expand nearest face"),
                ToolHintEntry("LMB", "Reset first corner"),
                ToolHintEntry("RMB", "Reset second corner"),
            )
        }
        return ToolHintPanel(
            title = "AXION - Erase",
            subtitle = subtitle,
            entries = entries,
            statusLines = buildList {
                currentSelectionSize()?.let { add("Selection: $it") }
                currentSelectionCorners()?.let { add(it) }
                targetSummary()?.let { add(it) }
            },
        )
    }

    private fun extrudePanel(): ToolHintPanel {
        val preview = when (val state = AxionClientState.extrudeToolState) {
            ExtrudeToolState.Idle -> null
            is ExtrudeToolState.Previewing -> state.preview
        }
        return ToolHintPanel(
            title = "AXION - Extrude",
            subtitle = if (preview == null) "Aim at a planar surface" else "Topology preview ready",
            entries = listOf(
                ToolHintEntry("RMB", "Extrude outward by 1"),
                ToolHintEntry("LMB", "Shrink by 1"),
                ToolHintEntry("Face", "Uses clicked face axis"),
            ),
            statusLines = buildList {
                preview?.let {
                    add("Footprint: ${it.footprint.size}")
                    add("Axis: ${formatAxis(it.direction.vector)}")
                }
                targetSummary()?.let { add(it) }
            },
        )
    }

    private fun symmetryPanel(): ToolHintPanel {
        val state = AxionClientState.symmetryState
        val subtitle = when (state) {
            SymmetryState.Inactive -> "No anchor set"
            is SymmetryState.Active -> "Configuring symmetry"
        }
        return ToolHintPanel(
            title = "AXION - Symmetry",
            subtitle = subtitle,
            entries = listOf(
                ToolHintEntry("LMB / RMB", "Place or move anchor"),
                ToolHintEntry("Ctrl + Scroll", "Nudge anchor"),
                ToolHintEntry("R", "Toggle rotation"),
                ToolHintEntry("F", "Toggle mirror Y"),
                ToolHintEntry("Del", "Clear symmetry"),
            ),
            statusLines = when (state) {
                SymmetryState.Inactive -> listOfNotNull(targetSummary())
                is SymmetryState.Active -> buildList {
                    add("Rot: ${if (state.config.rotationalEnabled) "On" else "Off"}")
                    add("Mirror: ${if (state.config.mirrorYEnabled) "On" else "Off"}")
                    targetSummary()?.let { add(it) }
                }
            },
        )
    }

    private fun repeatStatusLines(preview: RepeatRegionPreview?, modeText: String): List<String> {
        return buildList {
            currentSelectionSize()?.let { add("Selection: $it") }
            currentSelectionCorners()?.let { add(it) }
            targetSummary()?.let { add(it) }
            preview?.let {
                add("Repeats: ${it.repeatCount}")
                add("Step: ${formatAxis(it.step)} x ${formatStepLength(it.step)}")
            }
            add(modeText)
        }
    }

    private fun currentSelectionSize(): String? {
        return when (val state = AxionClientState.selectionState) {
            SelectionState.Idle -> null
            is SelectionState.FirstCornerSet -> "1 x 1 x 1"
            is SelectionState.RegionDefined -> formatRegionSize(state.region())
        }
    }

    private fun currentSelectionCorners(): String? {
        return when (val state = AxionClientState.selectionState) {
            SelectionState.Idle -> null
            is SelectionState.FirstCornerSet -> "First: ${formatPos(state.firstCorner)}"
            is SelectionState.RegionDefined -> "Corners: ${formatPos(state.firstCorner)} -> ${formatPos(state.secondCorner)}"
        }
    }

    private fun formatRegionSize(region: BlockRegion): String {
        val size = region.normalized().size()
        return "${size.x} x ${size.y} x ${size.z}"
    }

    private fun formatAxis(vector: Vec3i): String {
        return when {
            vector.x > 0 -> "+X"
            vector.x < 0 -> "-X"
            vector.y > 0 -> "+Y"
            vector.y < 0 -> "-Y"
            vector.z > 0 -> "+Z"
            vector.z < 0 -> "-Z"
            else -> "None"
        }
    }

    private fun formatStepLength(vector: Vec3i): Int {
        return maxOf(abs(vector.x), abs(vector.y), abs(vector.z))
    }

    private fun symmetrySummary(): String? {
        val config = ActiveSymmetryConfig.current()?.takeIf(ActiveSymmetryConfig::hasDerivedTransforms) ?: return null
        val parts = mutableListOf<String>()
        if (config.rotationalEnabled) {
            parts += "Rot"
        }
        if (config.mirrorYEnabled) {
            parts += "Mirror"
        }
        return if (parts.isEmpty()) null else "Sym: ${parts.joinToString("+")}"
    }

    private fun targetSummary(): String? {
        return when (val target = SelectionController.currentTarget()) {
            AxionTarget.MissTarget -> "Target: none"
            is AxionTarget.BlockTarget -> "Target: block ${formatPos(target.blockPos)}"
            is AxionTarget.FaceTarget -> "Target: face ${target.face.name.lowercase()} ${formatPos(target.blockPos)}"
        }
    }

    private fun formatPos(pos: BlockPos): String {
        return "${pos.x},${pos.y},${pos.z}"
    }
}
