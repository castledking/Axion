package axion.client.hotbar

import axion.client.AxionClientState
import axion.client.config.AxionClientConfig
import axion.client.selection.AxionTarget
import axion.client.selection.SelectionController
import axion.client.selection.blockPosOrNull
import axion.client.symmetry.ActiveSymmetryConfig
import axion.client.tool.AxionToolSelectionController
import axion.client.tool.CloneToolState
import axion.client.tool.EraseToolState
import axion.client.tool.ExtrudeToolState
import axion.client.tool.PlacementToolMode
import axion.client.tool.PlacementMirrorAxis
import axion.client.tool.RepeatRegionPreview
import axion.client.tool.SmearToolState
import axion.client.tool.StackToolState
import axion.client.tool.MagicSelectionService
import axion.client.ui.FormattedNameText
import axion.common.model.AxionSubtool
import axion.common.model.BlockRegion
import axion.common.model.SelectionState
import axion.common.model.SymmetryMirrorAxis
import axion.common.model.SymmetryState
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import kotlin.math.abs

object AxionToolHintProvider {
    private fun middleClickLabel(): String {
        return if (AxionClientState.middleClickMagicSelectEnabled) "Magic select" else "Expand nearest face"
    }

    private fun magicConfigHintEntries(): List<ToolHintEntry> {
        return if (AxionClientState.middleClickMagicSelectEnabled) {
            listOf(ToolHintEntry("Shift+MMB", "Configure templates"))
        } else {
            emptyList()
        }
    }

    private fun appendMagicSelectInfo(lines: MutableList<Text>) {
        if (!AxionClientState.middleClickMagicSelectEnabled) {
            return
        }
        lines += Text.of("Magic Select Info:")
        val templateLine = Text.literal("Template: ").append(FormattedNameText.parse(AxionClientConfig.magicSelectTemplateSummary()))
        lines += templateLine
        lines += Text.of("Brush Size: ${MagicSelectionService.defaultBrushSize()}")
    }

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
                ToolHintEntry("MMB", if (AxionClientState.middleClickMagicSelectEnabled) "Magic select" else "Disabled until region exists"),
                ToolHintEntry("Scroll", "Start preview"),
            ) + magicConfigHintEntries()

            is CloneToolState.FirstCornerSet -> listOf(
                ToolHintEntry("LMB", "Reset first corner"),
                ToolHintEntry("RMB", "Set second corner"),
                ToolHintEntry("MMB", if (AxionClientState.middleClickMagicSelectEnabled) "Magic select" else "Disabled until region exists"),
                ToolHintEntry("Scroll", "Disabled until region exists"),
            ) + magicConfigHintEntries()

            is CloneToolState.RegionDefined -> listOf(
                ToolHintEntry("MMB", middleClickLabel()),
                ToolHintEntry("Scroll", "Move preview"),
                ToolHintEntry("LMB", "Restart source selection"),
                ToolHintEntry("RMB", "Set second corner again"),
            ) + magicConfigHintEntries()

            is CloneToolState.PreviewingOffset,
            is CloneToolState.AwaitingConfirm,
                -> listOf(
                    ToolHintEntry("Scroll", "Adjust preview offset"),
                    ToolHintEntry("Main Mod + R", "Rotate 90 degrees"),
                    ToolHintEntry("Main Mod + F", "Mirror preview"),
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
                currentSelectionSize()?.let { add(Text.of("Selection: $it")) }
                targetSummary()?.let { add(Text.of(it)) }
                preview?.let {
                    add(Text.of("Offset: ${formatAxis(it.offset)} x ${formatStepLength(it.offset)}"))
                    add(Text.of("Destination: ${formatRegionSize(it.destinationRegion)}"))
                    add(Text.of("Rotation: ${it.transform.normalizedRotationQuarterTurns * 90}deg"))
                    add(Text.of("Mirror: ${formatMirrorAxis(it.transform.mirrorAxis)}"))
                }
                appendMagicSelectInfo(this)
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
                ToolHintEntry("MMB", if (AxionClientState.middleClickMagicSelectEnabled) "Magic select" else "Disabled until region exists"),
                ToolHintEntry("Scroll", "Pick axis and repeat"),
            ) + magicConfigHintEntries()

            is StackToolState.FirstCornerSet -> listOf(
                ToolHintEntry("LMB", "Reset first corner"),
                ToolHintEntry("RMB", "Set second corner"),
                ToolHintEntry("MMB", if (AxionClientState.middleClickMagicSelectEnabled) "Magic select" else "Disabled until region exists"),
            ) + magicConfigHintEntries()

            is StackToolState.RegionDefined -> listOf(
                ToolHintEntry("MMB", middleClickLabel()),
                ToolHintEntry("Scroll", "Start stack preview"),
                ToolHintEntry("LMB", "Restart selection"),
                ToolHintEntry("RMB", "Set second corner again"),
            ) + magicConfigHintEntries()

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
                ToolHintEntry("MMB", if (AxionClientState.middleClickMagicSelectEnabled) "Magic select" else "Disabled until region exists"),
                ToolHintEntry("Scroll", "Pick axis and repeat"),
            ) + magicConfigHintEntries()

            is SmearToolState.FirstCornerSet -> listOf(
                ToolHintEntry("LMB", "Reset first corner"),
                ToolHintEntry("RMB", "Set second corner"),
                ToolHintEntry("MMB", if (AxionClientState.middleClickMagicSelectEnabled) "Magic select" else "Disabled until region exists"),
            ) + magicConfigHintEntries()

            is SmearToolState.RegionDefined -> listOf(
                ToolHintEntry("MMB", middleClickLabel()),
                ToolHintEntry("Scroll", "Start smear preview"),
                ToolHintEntry("LMB", "Restart selection"),
                ToolHintEntry("RMB", "Set second corner again"),
            ) + magicConfigHintEntries()

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
                ToolHintEntry("MMB", if (AxionClientState.middleClickMagicSelectEnabled) "Magic select" else "Disabled until region exists"),
            ) + magicConfigHintEntries()

            is EraseToolState.FirstCornerSet -> listOf(
                ToolHintEntry("LMB", "Reset first corner"),
                ToolHintEntry("RMB", "Set second corner"),
                ToolHintEntry("MMB", if (AxionClientState.middleClickMagicSelectEnabled) "Magic select" else "Disabled until region exists"),
            ) + magicConfigHintEntries()

            is EraseToolState.RegionDefined -> listOf(
                ToolHintEntry("Del", "Erase selection"),
                ToolHintEntry("MMB", middleClickLabel()),
                ToolHintEntry("LMB", "Reset first corner"),
                ToolHintEntry("RMB", "Reset second corner"),
            ) + magicConfigHintEntries()
        }
        return ToolHintPanel(
            title = "AXION - Erase",
            subtitle = subtitle,
            entries = entries,
            statusLines = buildList {
                currentSelectionSize()?.let { add(Text.of("Selection: $it")) }
                currentSelectionCorners()?.let { add(Text.of(it)) }
                targetSummary()?.let { add(Text.of(it)) }
                appendMagicSelectInfo(this)
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
                    add(Text.of("Footprint: ${it.footprint.size}"))
                    add(Text.of("Axis: ${formatAxis(it.direction.vector)}"))
                }
                targetSummary()?.let { add(Text.of(it)) }
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
                ToolHintEntry("Main Mod + R", "Toggle rotation"),
                ToolHintEntry("Main Mod + F", "Toggle mirror Y"),
                ToolHintEntry("Del", "Clear symmetry"),
            ),
            statusLines = when (state) {
                SymmetryState.Inactive -> listOfNotNull(targetSummary()?.let(Text::of))
                is SymmetryState.Active -> buildList {
                    add(Text.of("Rot: ${if (state.config.rotationalEnabled) "On" else "Off"}"))
                    add(Text.of("Mirror: ${formatSymmetryMirror(state.config)}"))
                    targetSummary()?.let { add(Text.of(it)) }
                }
            },
        )
    }

    private fun repeatStatusLines(preview: RepeatRegionPreview?, modeText: String): List<Text> {
        return buildList {
            currentSelectionSize()?.let { add(Text.of("Selection: $it")) }
            currentSelectionCorners()?.let { add(Text.of(it)) }
            targetSummary()?.let { add(Text.of(it)) }
            preview?.let {
                add(Text.of("Repeats: ${it.repeatCount}"))
                add(Text.of("Step: ${formatAxis(it.step)} x ${formatStepLength(it.step)}"))
            }
            add(Text.of(modeText))
            appendMagicSelectInfo(this)
        }
    }

    private fun formatMirrorAxis(axis: PlacementMirrorAxis): String {
        return when (axis) {
            PlacementMirrorAxis.NONE -> "Off"
            PlacementMirrorAxis.X -> "X"
            PlacementMirrorAxis.Z -> "Z"
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
        if (config.mirrorEnabled) {
            parts += "Mirror ${config.mirrorAxis.name}"
        }
        return if (parts.isEmpty()) null else "Sym: ${parts.joinToString("+")}"
    }

    private fun formatSymmetryMirror(config: axion.common.model.SymmetryConfig): String {
        return if (!config.mirrorEnabled) {
            "Off"
        } else {
            config.mirrorAxis.name
        }
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
