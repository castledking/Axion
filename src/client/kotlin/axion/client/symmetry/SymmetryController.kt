package axion.client.symmetry

import axion.client.AxionClientState
import axion.client.selection.SelectionController
import axion.common.model.AxionSubtool
import axion.common.model.SymmetryConfig
import axion.common.model.SymmetryState
import net.minecraft.client.MinecraftClient

object SymmetryController {
    fun onEndTick(client: MinecraftClient) {
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

    fun handlePrimaryAction(client: MinecraftClient): Boolean = moveAnchor()

    fun handleSecondaryAction(client: MinecraftClient): Boolean = moveAnchor()

    fun handleDeleteAction(client: MinecraftClient): Boolean {
        if (!isSymmetrySetupActive() || AxionClientState.symmetryState == SymmetryState.Inactive) {
            return false
        }

        AxionClientState.updateSymmetry(SymmetryState.Inactive)
        AxionClientState.updateSymmetryPreview(null)
        return true
    }

    fun handleScroll(client: MinecraftClient, scrollAmount: Double): Boolean {
        if (!isSymmetrySetupActive()) {
            return false
        }

        val config = currentConfig() ?: return false
        val direction = SymmetryTargetService.resolveNudgeDirection(client, SelectionController.currentTarget())
        val scrollDirection = scrollAmount.compareTo(0.0)
        if (scrollDirection == 0) {
            return false
        }

        val nudged = config.copy(
            anchor = config.anchor.copy(
                position = config.anchor.position.add(
                    direction.offsetX * scrollDirection.toDouble(),
                    direction.offsetY * scrollDirection.toDouble(),
                    direction.offsetZ * scrollDirection.toDouble(),
                ),
            ),
        )
        AxionClientState.updateSymmetry(SymmetryState.Active(nudged))
        return true
    }

    fun toggleRotational(): Boolean {
        if (!isSymmetrySetupActive()) {
            return false
        }

        val config = currentConfig() ?: return false
        AxionClientState.updateSymmetry(
            SymmetryState.Active(config.copy(rotationalEnabled = !config.rotationalEnabled)),
        )
        return true
    }

    fun toggleMirrorY(): Boolean {
        if (!isSymmetrySetupActive()) {
            return false
        }

        val config = currentConfig() ?: return false
        AxionClientState.updateSymmetry(
            SymmetryState.Active(config.copy(mirrorYEnabled = !config.mirrorYEnabled)),
        )
        return true
    }

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

    private fun isSymmetrySetupActive(): Boolean {
        return axion.client.tool.AxionToolSelectionController.isAxionSlotActive() &&
            axion.client.tool.AxionToolSelectionController.selectedSubtool() == AxionSubtool.SETUP_SYMMETRY
    }
}
