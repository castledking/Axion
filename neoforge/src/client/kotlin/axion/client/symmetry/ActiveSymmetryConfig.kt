package axion.client.symmetry

import axion.client.AxionClientState
import axion.common.model.SymmetryConfig
import axion.common.model.SymmetryState

object ActiveSymmetryConfig {
    fun current(): SymmetryConfig? {
        return when (val state = AxionClientState.symmetryState) {
            SymmetryState.Inactive -> null
            is SymmetryState.Active -> state.config
        }
    }

    fun hasDerivedTransforms(config: SymmetryConfig?): Boolean {
        return config != null && (config.rotationalEnabled || config.mirrorEnabled)
    }
}
