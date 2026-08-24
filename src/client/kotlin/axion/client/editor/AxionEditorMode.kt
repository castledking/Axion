package axion.client.editor

import axion.client.compat.VersionCompatImpl
import axion.client.network.AxionServerConnection
import axion.client.editor.ui.AxionEditorUi
import axion.protocol.AxionGameMode
import axion.protocol.GameModeChangeRequest
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.math.Vec3d
import org.lwjgl.glfw.GLFW
import kotlin.math.cos
import kotlin.math.sin

/**
 * Axiom-style editor mode, toggled with Right Shift.
 *
 * Enabling requests a spectator swap (which gives vanilla camera-direction
 * flight and server-side phasing), but the editor does not depend on the swap
 * succeeding: while active it is equally usable in creative flight by arming
 * the mod's NoClip machinery and driving velocity itself.
 *
 * While active the OS cursor is freed and the crosshair is hidden; holding
 * right click re-grabs the cursor as a plain vanilla mouselook drag (native
 * sensitivity, inversion and smoothing) during which the crosshair shows.
 * Movement has no momentum — velocity is rebuilt from held keys every tick,
 * so releasing everything stops immediately.
 */
object AxionEditorMode {
    private const val RESTORE_GAME_MODE_ID: String = "creative"
    private const val EDITOR_GAME_MODE_ID: String = "spectator"

    /** Base flight speed multiplier over abilities.flySpeed (~vanilla spectator). */
    private const val FLIGHT_SPEED_MULTIPLIER: Float = 20f
    private const val SPRINT_SPEED_MULTIPLIER: Float = 2f

    private fun speedMultiplier(): Float =
        FLIGHT_SPEED_MULTIPLIER * AxionEditorUi.flightSpeedPercent / 100f

    /** User intent. Editor behaviors only apply once [isActive] confirms a usable mode. */
    var isEnabled: Boolean = false
        private set

    /** True while right click is held to drag the camera angle. */
    var isDraggingCamera: Boolean = false
        private set

    private var lastObservedActive: Boolean = false

    /**
     * True when the editor owns input: enabled, in a world, and in a mode it
     * can drive — spectator (preferred) or creative flight (fallback when the
     * gamemode swap cannot happen, e.g. without permissions).
     */
    fun isActive(): Boolean {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return false
        if (!isEnabled || client.world == null) {
            return false
        }
        return player.isSpectator || player.isInCreativeMode
    }

    /**
     * Consumes a raw mouse button event. Returns true when the event belongs
     * to the editor (left or right click while active) so callers can cancel
     * vanilla interaction — the editor owns both buttons, Axiom-style.
     *
     * Panels get first crack: a press that lands on the UI never starts a
     * camera drag.
     */
    fun onMouseButton(client: MinecraftClient, button: Int, action: Int): Boolean {
        if (!isActive() || client.currentScreen != null) {
            return false
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false
        }

        when (action) {
            GLFW.GLFW_PRESS -> {
                val uiConsumed = AxionEditorUi.onMouseButton(button = button, pressed = true)
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && !uiConsumed) {
                    beginCameraDrag(client)
                }
                return true
            }

            GLFW.GLFW_RELEASE -> {
                AxionEditorUi.onMouseButton(button = button, pressed = false)
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isDraggingCamera) {
                    endCameraDrag(client)
                    return true
                }
            }
        }
        return false
    }

    fun toggle(client: MinecraftClient) {
        val player = client.player ?: return
        if (!isEnabled) {
            if (client.currentScreen != null) return
            if (!player.isInCreativeMode && !player.isSpectator) {
                showStateMessage(client, "requires_creative")
                return
            }
            requestGameMode(client, AxionGameMode.SPECTATOR, EDITOR_GAME_MODE_ID)
            isEnabled = true
            AxionEditorUi.unmount()
            showStateMessage(client, "enabled")
        } else {
            disable(client)
        }
    }

    /**
     * Runs before the player tick so the velocity written here is exactly what
     * vanilla's travel consumes this tick. Rebuilding velocity from scratch
     * every tick is what removes momentum: releasing all keys stops instantly.
     */
    fun onStartTick(client: MinecraftClient) {
        val player = client.player ?: return
        if (!isActive() || !player.abilities.flying) {
            return
        }

        // Screens own the keyboard; bleed off any residual glide instead of
        // flying blind while chat or an inventory is open.
        if (client.currentScreen != null) {
            player.setVelocity(Vec3d.ZERO)
            return
        }

        val forward = (if (client.options.forwardKey.isPressed) 1 else 0) -
            (if (client.options.backKey.isPressed) 1 else 0)
        val strafe = (if (client.options.rightKey.isPressed) 1 else 0) -
            (if (client.options.leftKey.isPressed) 1 else 0)
        val vertical = (if (client.options.jumpKey.isPressed) 1 else 0) -
            (if (client.options.sneakKey.isPressed) 1 else 0)

        if (forward == 0 && strafe == 0 && vertical == 0) {
            player.setVelocity(Vec3d.ZERO)
            return
        }

        // Forward/backward follow the full camera angle including pitch;
        // strafing stays horizontal; jump/sneak move along the world axis.
        val yawRad = Math.toRadians(player.yaw.toDouble())
        val pitchRad = Math.toRadians(player.pitch.toDouble())
        val yawSin = sin(yawRad)
        val yawCos = cos(yawRad)
        val pitchCos = cos(pitchRad)

        val lookX = -yawSin * pitchCos
        val lookY = -sin(pitchRad)
        val lookZ = yawCos * pitchCos
        val flatForwardX = -yawSin
        val flatForwardZ = yawCos
        // Right vector = flatForward x up: (-flatForwardZ, 0, flatForwardX).
        val rightX = -flatForwardZ
        val rightZ = flatForwardX

        var dirX = lookX * forward + rightX * strafe
        var dirY = lookY * forward + vertical.toDouble()
        var dirZ = lookZ * forward + rightZ * strafe
        val length = kotlin.math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
        if (length < 1.0E-6) {
            player.setVelocity(Vec3d.ZERO)
            return
        }

        val sprint = if (client.options.sprintKey.isPressed) SPRINT_SPEED_MULTIPLIER else 1f
        val speed = player.abilities.flySpeed * speedMultiplier() * sprint
        val scale = speed / length
        player.setVelocity(Vec3d(dirX * scale, dirY * scale, dirZ * scale))
    }

    fun onEndTick(client: MinecraftClient) {
        if (client.player == null || client.world == null) {
            // Left the world (disconnect / title). Drop state without writing
            // game modes — there is no player left to restore.
            isEnabled = false
            isDraggingCamera = false
            lastObservedActive = false
            AxionEditorUi.unmount()
            return
        }

        // Safety nets for drags whose release never arrived (alt-tab, screen
        // opened mid-drag): any condition that makes a drag unsafe ends it.
        if (isDraggingCamera) {
            val handle = client.window.handle
            val rightHeld =
                GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS
            if (!rightHeld || !client.isWindowFocused || client.currentScreen != null) {
                endCameraDrag(client)
            }
        }

        syncCursorOwnership(client)
    }

    /**
     * Cursor lock requests from vanilla (screen close, window focus regain)
     * must not steal the free cursor while the editor owns input.
     */
    fun shouldBlockCursorLock(): Boolean = isActive() && !isDraggingCamera

    /**
     * Cursor unlocks from vanilla are blocked mid-drag so nothing but the
     * drag's own release can end it.
     */
    fun shouldBlockCursorUnlock(): Boolean = isActive() && isDraggingCamera

    /** Frees or returns the cursor as the editor becomes active / inactive. */
    private fun syncCursorOwnership(client: MinecraftClient) {
        val active = isActive()
        if (active == lastObservedActive) {
            return
        }

        if (active && !isDraggingCamera) {
            // Entering the editor: hand the cursor over. Vanilla grabbed it
            // for regular gameplay until now.
            client.mouse.unlockCursor()
        } else if (!active && !isEnabled && client.currentScreen == null) {
            // Editor state lost without an explicit disable (the server moved
            // us out of spectator): give vanilla its grab back.
            client.mouse.lockCursor()
        }
        lastObservedActive = active
    }

    private fun beginCameraDrag(client: MinecraftClient) {
        isDraggingCamera = true
        // Vanilla lockCursor applies native sensitivity/inversion/smoothing,
        // which is exactly the feel regular gameplay has. Our lock hook allows
        // this call because the drag flag flips first.
        client.mouse.lockCursor()
    }

    private fun endCameraDrag(client: MinecraftClient) {
        if (!isDraggingCamera) return
        isDraggingCamera = false
        client.mouse.unlockCursor()
    }

    private fun disable(client: MinecraftClient) {
        if (!isEnabled) return
        if (isDraggingCamera) {
            endCameraDrag(client)
        }
        isEnabled = false
        AxionEditorUi.unmount()
        if (client.player?.isSpectator == true) {
            // Only write a gamemode back when we actually switched to
            // spectator; a creative-fallback session must stay creative.
            requestGameMode(client, AxionGameMode.CREATIVE, RESTORE_GAME_MODE_ID)
        }
        // Hand the mouse back to vanilla grab immediately; our interception
        // hooks are already inert because isEnabled flipped first.
        if (client.currentScreen == null) {
            client.mouse.lockCursor()
        }
        showStateMessage(client, "disabled")
    }

    private fun requestGameMode(
        client: MinecraftClient,
        gameMode: AxionGameMode,
        gameModeId: String,
    ) {
        val serverState = AxionServerConnection.state()
        if (serverState is AxionServerConnection.State.Available) {
            AxionServerConnection.sendClientMessage(GameModeChangeRequest(gameMode))
        } else if (!VersionCompatImpl.changeLocalGameMode(client, gameModeId)) {
            VersionCompatImpl.sendGameModeCommand(client, gameModeId)
        }
    }

    private fun showStateMessage(client: MinecraftClient, key: String) {
        client.inGameHud.setOverlayMessage(Text.translatable("axion.editor.$key"), false)
    }
}
