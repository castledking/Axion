package axion.client.symmetry

import axion.client.AxionClientState
import axion.client.current.SelectionController
import axion.client.itemStack.AxionToolSelectionController
import axion.common.model.AxionSubtool
import axion.common.model.SymmetryConfig
import axion.common.model.SymmetryMirrorAxis
import axion.common.model.SymmetryState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import kotlin.math.round

object SymmetryController {
    private const val NUDGE_STEP: Double = 0.5

    fun onEndTick(client: Minecraft) {
        if (!hasSymmetryConfig()) {
            AxionClientState.updateSymmetryPreview(null)
            return
        }

        if (isSymmetrySetupActive()) {
            val config = currentConfig() ?: run {
                AxionClientState.updateSymmetryPreview(null)
                return
            }

            AxionClientState.updateSymmetryPreview(
                SymmetryPreviewService.createPreview(config, SelectionController.currentTarget()),
            )
            return
        }

        if (!SymmetryPlacementController.updatePreview(client)) {
            AxionClientState.updateSymmetryPreview(null)
        }
    }

    fun handlePrimaryAction(client: Minecraft): Boolean = moveAnchor()

    fun handleSecondaryAction(client: Minecraft): Boolean = moveAnchor()

    fun handleDeleteAction(client: Minecraft): Boolean {
        if (!canToggleFromHotkey() || AxionClientState.symmetryState == SymmetryState.Inactive) {
            return false
        }

        AxionClientState.updateSymmetry(SymmetryState.Inactive)
        AxionClientState.updateSymmetryPreview(null)
        return true
    }

    fun handleScroll(client: Minecraft, scrollAmount: Double): Boolean {
        if (!isSymmetrySetupActive()) {
            return false
        }

        val config = currentConfig() ?: return false
        val direction = SymmetryTargetService.resolveNudgeDirection(client, SelectionController.currentTarget())
        val scrollDirection = -scrollAmount.compareTo(0.0)
        if (scrollDirection == 0) {
            return false
        }

        val nudged = config.copy(
            anchor = config.anchor.copy(
                position = quantizeToHalfGrid(
                    config.anchor.position.add(
                        direction.stepX * scrollDirection.toDouble() * NUDGE_STEP,
                        direction.stepY * scrollDirection.toDouble() * NUDGE_STEP,
                        direction.stepZ * scrollDirection.toDouble() * NUDGE_STEP,
                    ),
                ),
            ),
        )
        AxionClientState.updateSymmetry(SymmetryState.Active(nudged))
        return true
    }

    fun toggleRotational(): Boolean {
        if (!canToggleFromHotkey()) {
            return false
        }

        val config = currentConfig() ?: return false
        val enabled = !config.rotationalEnabled
        AxionClientState.updateSymmetry(SymmetryState.Active(config.copy(rotationalEnabled = enabled)))
        showToggleFeedback("Rotational", enabled)
        return true
    }

    fun toggleConstruct(): Boolean {
        if (!canToggleFromHotkey()) {
            return false
        }

        val config = currentConfig() ?: return false
        val enabled = !config.constructEnabled
        AxionClientState.updateSymmetry(SymmetryState.Active(config.copy(constructEnabled = enabled)))
        showToggleFeedback("Construct", enabled)
        return true
    }

    fun toggleMirror(client: Minecraft): Boolean {
        if (!canToggleFromHotkey()) {
            return false
        }

        val config = currentConfig() ?: return false
        val axis = SymmetryTargetService.resolveMirrorAxis(client)
        val nextConfig = if (config.mirrorEnabled && config.mirrorAxis == axis) {
            config.copy(mirrorEnabled = false)
        } else {
            config.copy(mirrorEnabled = true, mirrorAxis = axis)
        }
        AxionClientState.updateSymmetry(SymmetryState.Active(nextConfig))
        showToggleFeedback(axis.name, nextConfig.mirrorEnabled)
        return true
    }

    fun canToggleMirrorFromHotkey(): Boolean = canToggleFromHotkey()

    private fun moveAnchor(): Boolean {
        if (!isSymmetrySetupActive()) {
            return false
        }

        val anchor = SymmetryTargetService.resolveAnchor(SelectionController.currentTarget()) ?: return false
        val nextConfig = currentConfig()?.copy(anchor = anchor) ?: SymmetryConfig(anchor = anchor)
        AxionClientState.updateSymmetry(SymmetryState.Active(nextConfig))
        return true
    }

    private fun currentConfig(): SymmetryConfig? {
        return when (val state = AxionClientState.symmetryState) {
            SymmetryState.Inactive -> null
            is SymmetryState.Active -> state.config
        }
    }

    private fun hasSymmetryConfig(): Boolean = currentConfig() != null

    private fun canToggleFromHotkey(): Boolean {
        return AxionToolSelectionController.isCreativeModeAllowed() &&
            AxionToolSelectionController.selectedSubtool() == AxionSubtool.SETUP_SYMMETRY &&
            hasSymmetryConfig()
    }

    private fun quantizeToHalfGrid(position: Vec3): Vec3 {
        return Vec3(
            quantizeToHalf(position.x),
            quantizeToHalf(position.y),
            quantizeToHalf(position.z),
        )
    }

    private fun quantizeToHalf(value: Double): Double {
        return round(value * 2.0) / 2.0
    }

    private fun showToggleFeedback(label: String, enabled: Boolean) {
        val client = Minecraft.getInstance()
        val status = if (enabled) "enabled" else "disabled"
        client.gui.setOverlayMessage(Component.literal("Axion $label symmetry $status"), false)
        SystemToast.add(
            client.toastManager,
            SystemToast.Type.PERIODIC_NOTIFICATION,
            Component.literal("Axion $label symmetry"),
            Component.literal(if (enabled) "Enabled" else "Disabled"),
        )
    }

    private fun isSymmetrySetupActive(): Boolean {
        return AxionToolSelectionController.isAxionSlotActive() &&
            AxionToolSelectionController.selectedSubtool() == AxionSubtool.SETUP_SYMMETRY
    }
}
