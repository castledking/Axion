package axion.client.hotbar

import axion.client.AxionClientState
import axion.client.tool.AxionToolSelectionController
import axion.client.tool.CloneToolState
import axion.client.tool.EraseToolState
import axion.client.tool.ExtrudeToolState
import axion.client.tool.PlacementToolMode
import axion.client.tool.SmearToolState
import axion.client.tool.StackToolState
import axion.common.model.AxionSubtool

object AxionToolHintProvider {
    fun currentCompactHints(): CompactToolHints? {
        if (!AxionToolSelectionController.isAxionSlotActive()) {
            return null
        }

        return when (AxionToolSelectionController.selectedSubtool()) {
            AxionSubtool.MOVE -> placementCompactHints(PlacementToolMode.MOVE)
            AxionSubtool.CLONE -> placementCompactHints(PlacementToolMode.CLONE)
            AxionSubtool.STACK -> stackCompactHints()
            AxionSubtool.SMEAR -> smearCompactHints()
            AxionSubtool.ERASE -> eraseCompactHints()
            AxionSubtool.EXTRUDE -> extrudeCompactHints()
            AxionSubtool.SETUP_SYMMETRY -> symmetryCompactHints()
        }
    }

    private fun placementCompactHints(mode: PlacementToolMode): CompactToolHints {
        val state = AxionClientState.placementToolState
        val scrollAction = if (mode == PlacementToolMode.MOVE) "Scroll to move" else "Scroll to clone"
        val previewing = state is CloneToolState.PreviewingOffset || state is CloneToolState.AwaitingConfirm
        return CompactToolHints(
            crosshairHints = buildList {
                when (state) {
                    CloneToolState.Idle -> add(left("First Point"))
                    is CloneToolState.FirstCornerSet -> addAll(selectionPointHints())
                    is CloneToolState.RegionDefined -> {
                        addAll(selectionPointHints())
                        add(scroll(scrollAction))
                    }
                    is CloneToolState.PreviewingOffset,
                    is CloneToolState.AwaitingConfirm,
                        -> {
                        addAll(confirmPreviewHints())
                        add(middle("Reanchor preview"))
                    }
                }
                addMagicSelectCrosshair()
            },
            keyHints = buildList {
                if (previewing) {
                    add(ToolHintEntry("Ctrl + R", "Rotate preview"))
                    add(ToolHintEntry("Ctrl + F", "Flip preview"))
                }
                addMagicSelectKeyHints()
            },
        )
    }

    private fun stackCompactHints(): CompactToolHints {
        val state = AxionClientState.stackToolState
        return CompactToolHints(
            crosshairHints = buildList {
                when (state) {
                    StackToolState.Idle -> add(left("First Point"))
                    is StackToolState.FirstCornerSet -> addAll(selectionPointHints())
                    is StackToolState.RegionDefined -> {
                        addAll(selectionPointHints())
                        add(scroll("Scroll to stack"))
                    }
                    is StackToolState.PreviewingStack -> addAll(confirmPreviewHints())
                }
                addMagicSelectCrosshair()
            },
            keyHints = buildList {
                addMagicSelectKeyHints()
            },
        )
    }

    private fun smearCompactHints(): CompactToolHints {
        val state = AxionClientState.smearToolState
        val preview = (state as? SmearToolState.PreviewingSmear)?.preview
        return CompactToolHints(
            crosshairHints = buildList {
                when (state) {
                    SmearToolState.Idle -> add(left("First Point"))
                    is SmearToolState.FirstCornerSet -> addAll(selectionPointHints())
                    is SmearToolState.RegionDefined -> {
                        addAll(selectionPointHints())
                        add(scroll("Scroll to smear"))
                    }
                    is SmearToolState.PreviewingSmear -> addAll(confirmPreviewHints())
                }
                addMagicSelectCrosshair()
            },
            keyHints = buildList {
                addMagicSelectKeyHints()
            },
            hotbarStatus = preview?.let {
                "Node offset: ${formatSigned(it.step.x)}, ${formatSigned(it.step.y)}, ${formatSigned(it.step.z)}"
            },
        )
    }

    private fun eraseCompactHints(): CompactToolHints {
        val state = AxionClientState.eraseToolState
        return CompactToolHints(
            crosshairHints = buildList {
                when (state) {
                    EraseToolState.Idle -> {
                        add(left("First Point"))
                        add(right("Erase connected"))
                    }
                    is EraseToolState.FirstCornerSet -> addAll(selectionPointHints())
                    is EraseToolState.RegionDefined -> {
                        addAll(selectionPointHints())
                        add(key("Del", "Erase selection"))
                    }
                }
                addMagicSelectCrosshair()
            },
            keyHints = buildList {
                if (state == EraseToolState.Idle) {
                    add(ToolHintEntry("Ctrl + Scroll", "Erase brush size"))
                } else {
                    addMagicSelectKeyHints()
                }
            },
        )
    }

    private fun extrudeCompactHints(): CompactToolHints {
        return CompactToolHints(
            crosshairHints = listOf(
                left("Shrink"),
                right("Extrude"),
            ),
        )
    }

    private fun symmetryCompactHints(): CompactToolHints {
        return CompactToolHints(
            crosshairHints = listOf(
                left("Place Symmetry"),
                right("Move Symmetry"),
                key("Del", "Clear symmetry"),
            ),
            keyHints = listOf(
                ToolHintEntry("Ctrl + Scroll", "Nudge symmetry"),
                ToolHintEntry("Ctrl + R", "Rotate symmetry"),
                ToolHintEntry("Ctrl + F", "Flip symmetry"),
            ),
        )
    }

    private fun MutableList<CrosshairHint>.addMagicSelectCrosshair() {
        if (!AxionClientState.middleClickMagicSelectEnabled) {
            return
        }
        add(middle("Magic Select"))
    }

    private fun MutableList<ToolHintEntry>.addMagicSelectKeyHints() {
        if (!AxionClientState.middleClickMagicSelectEnabled) {
            return
        }
        add(ToolHintEntry("Ctrl + MMB", "Configure templates"))
        add(ToolHintEntry("Ctrl + Scroll", "Adjust brush size"))
    }

    private fun selectionPointHints(): List<CrosshairHint> {
        return listOf(
            left("First Point"),
            right("Second Point"),
        )
    }

    private fun confirmPreviewHints(): List<CrosshairHint> {
        return listOf(
            left("Cancel"),
            right("Confirm"),
        )
    }

    private fun left(action: String): CrosshairHint = CrosshairHint.Mouse(MouseHintIcon.LEFT, action)

    private fun right(action: String): CrosshairHint = CrosshairHint.Mouse(MouseHintIcon.RIGHT, action)

    private fun scroll(action: String): CrosshairHint = CrosshairHint.Mouse(MouseHintIcon.SCROLL, action)

    private fun middle(action: String): CrosshairHint = CrosshairHint.Mouse(MouseHintIcon.SCROLL, action)

    private fun key(label: String, action: String): CrosshairHint = CrosshairHint.Key(label, action)

    private fun formatSigned(value: Int): String {
        return if (value > 0) "+$value" else value.toString()
    }
}
