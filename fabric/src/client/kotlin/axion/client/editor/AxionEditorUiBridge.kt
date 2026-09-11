package axion.client.editor

import axion.client.editor.ui.AxionEditorUi
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import org.slf4j.LoggerFactory

/**
 * The only door from the rest of Axion into the owo-backed editor panels.
 *
 * owo-lib is an optional dependency: Axion compiles against it but never
 * requires it at runtime. That only holds if nothing touches
 * [AxionEditorUi] when owo is absent, because loading that class links owo
 * types and throws `NoClassDefFoundError`. Two of the call sites that did
 * this ran unconditionally — the HUD render hook registered at client init,
 * and the no-world branch of the editor tick on the title screen — so a soft
 * dependency on its own would still have crashed on launch.
 *
 * Every call therefore routes through here and becomes a no-op without owo.
 * A present-but-incompatible owo (an API that moved between releases) fails
 * the first call with a [LinkageError]; that disables the editor for the
 * session instead of taking the game down.
 *
 * verifyOwoOptional enforces the boundary: outside `axion/client/editor/ui`,
 * this is the only class allowed to reference the editor UI, and no class
 * outside that package may reference owo at all.
 */
object AxionEditorUiBridge {
    private const val OWO_MOD_ID: String = "owo"
    private const val DEFAULT_FLIGHT_SPEED_PERCENT: Int = 100

    private val logger = LoggerFactory.getLogger("axion")

    private val owoLoaded: Boolean by lazy {
        FabricLoader.getInstance().isModLoaded(OWO_MOD_ID)
    }

    @Volatile
    private var linkageFailed: Boolean = false

    /** True when the editor panels can actually be used this session. */
    fun isAvailable(): Boolean = owoLoaded && !linkageFailed

    val flightSpeedPercent: Int
        get() = call(DEFAULT_FLIGHT_SPEED_PERCENT) { AxionEditorUi.flightSpeedPercent }

    fun render(context: DrawContext, tickCounter: RenderTickCounter) {
        call(Unit) { AxionEditorUi.render(context, tickCounter) }
    }

    fun onMouseButton(button: Int, pressed: Boolean): Boolean =
        call(false) { AxionEditorUi.onMouseButton(button = button, pressed = pressed) }

    fun onMouseScroll(vertical: Double): Boolean =
        call(false) { AxionEditorUi.onMouseScroll(vertical) }

    fun unmount() {
        call(Unit) { AxionEditorUi.unmount() }
    }

    private inline fun <T> call(fallback: T, block: () -> T): T {
        if (!isAvailable()) {
            return fallback
        }
        return try {
            block()
        } catch (error: LinkageError) {
            linkageFailed = true
            logger.error(
                "[Axion/Editor] The installed owo-lib is incompatible with this Axion build; " +
                    "the editor is disabled for this session.",
                error,
            )
            fallback
        }
    }
}
