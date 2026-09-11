package axion.client.editor.ui

import axion.client.compat.VersionCompatImpl
import axion.client.editor.AxionEditorMode
import axion.client.editor.AxionLayout
import axion.client.editor.EditorFrameController
import axion.client.editor.EditorFramePlatform
import axion.client.history.UndoRedoController
import io.wispforest.owo.ui.component.ButtonComponent
import io.wispforest.owo.ui.component.DiscreteSliderComponent
import io.wispforest.owo.ui.component.LabelComponent
import io.wispforest.owo.ui.container.CollapsibleContainer
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.core.Insets
import io.wispforest.owo.ui.core.OwoUIAdapter
import io.wispforest.owo.ui.core.Positioning
import io.wispforest.owo.ui.core.Sizing
import io.wispforest.owo.ui.core.Surface
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import org.lwjgl.system.MemoryStack

/**
 * The in-game editor panels, built on owo-ui.
 *
 * Layout mirrors Axiom's chrome: a top menu bar, a views bar under it, the
 * tools panel on the left (uniform square tool tiles), status panels stacked
 * on the right (Clipboard, Palette, Active Block, History, World Properties),
 * and flight/stabilization sliders with a hint bar along the bottom.
 *
 * The adapter is created screen-less and rendered from the HUD layer while
 * the editor is active. Everything that touches owo's event/render entry
 * points goes through the per-branch [owoMouseClick]/[owoMouseRelease]/
 * [owoMouseDrag]/[owoSubscribeMouseDown]/[owoRender] glue — owo changed those
 * signatures between the 0.12.x and 0.13.x generations, so they cannot live
 * in shared code.
 */
object AxionEditorUi {
    private const val TOP_BAR_HEIGHT = AxionLayout.TOP_BAR_HEIGHT
    private const val VIEWS_BAR_HEIGHT = AxionLayout.VIEWS_BAR_HEIGHT
    private const val BOTTOM_BAR_HEIGHT = AxionLayout.BOTTOM_BAR_HEIGHT
    private const val PANEL_WIDTH = AxionLayout.PANEL_WIDTH
    private const val TOOL_TILE = 34
    private const val TOOLS_PER_ROW = 4

    private val CHROME = Surface.flat(0xF0101013.toInt())
    private val TILE = Surface.flat(0xF1A1A1E)
    private val TILE_SELECTED = Surface.flat(0xF274B23)

    /** Axiom's ToolManager order; ids map to textures/gui/editor/tools/<id>.png. */
    private val TOOLS: List<Pair<String, String>> = listOf(
        "magic_select" to "Magic Select",
        "box_select" to "Box Select",
        "freehand_select" to "Freehand Select",
        "lasso_select" to "Lasso Select",
        "ruler" to "Ruler",
        "annotation" to "Annotation",
        "painter" to "Painter",
        "noise_painter" to "Noise Painter",
        "biome_painter" to "Biome Painter",
        "gradient_painter" to "Gradient Painter",
        "script_brush" to "Script Brush",
        "freehand_draw" to "Freehand Draw",
        "sculpt_draw" to "Sculpt Draw",
        "rock" to "Rock",
        "weld" to "Weld",
        "melt" to "Melt",
        "stamp" to "Stamp",
        "text" to "Text",
        "shape" to "Shape",
        "path" to "Path",
        "modelling" to "Modelling",
        "floodfill" to "Floodfill",
        "fluid_ball" to "Fluid Ball",
        "elevation" to "Elevation",
        "slope" to "Slope",
        "smooth" to "Smooth",
        "distort" to "Distort",
        "roughen" to "Roughen",
        "blend" to "Blend",
        "shatter" to "Shatter",
        "extrude" to "Extrude",
        "modify" to "Modify",
    )

    private var adapter: OwoUIAdapter<*>? = null
    private var rootFlow: FlowLayout? = null

    private var mouseX = 0.0
    private var mouseY = 0.0

    var selectedToolIndex: Int = -1
        private set

    /** Flight speed slider state, applied by [AxionEditorMode]'s movement. */
    @Volatile
    var flightSpeedPercent: Int = 100
        private set

    /** Brush stabilization slider; consumed by brush tools once they exist. */
    @Volatile
    var stabilizationPercent: Int = 100
        private set

    private var toolsTileHost: FlowLayout? = null
    private var flightSlider: DiscreteSliderComponent? = null
    private var stabilizationSlider: DiscreteSliderComponent? = null
    private var activeBlockLabel: LabelComponent? = null
    private var lastFlightSliderValue: Double = Double.NaN
    private var lastStabilizationSliderValue: Double = Double.NaN

    fun mount() {
        val client = MinecraftClient.getInstance()
        if (adapter != null) {
            return
        }
        val width = client.window.scaledWidth
        val height = client.window.scaledHeight

        val newAdapter: OwoUIAdapter<FlowLayout> =
            OwoUIAdapter.createWithoutScreen(0, 0, width, height) { horizontal, vertical ->
                owoHorizontalFlow(horizontal, vertical)
            }
        newAdapter.inflateAndMount()
        buildLayout(newAdapter.rootComponent, width, height)
        rootFlow = newAdapter.rootComponent
        adapter = newAdapter
    }

    fun unmount() {
        adapter?.dispose()
        adapter = null
        rootFlow = null
        toolsTileHost = null
        flightSlider = null
        stabilizationSlider = null
        activeBlockLabel = null
        lastFlightSliderValue = Double.NaN
        lastStabilizationSliderValue = Double.NaN
    }

    fun render(context: DrawContext, tickCounter: RenderTickCounter) {
        if (!AxionEditorMode.isActive()) {
            return
        }
        mount()
        val current = adapter ?: return
        updateCursorPosition(current)
        pollSliders()

        owoRender(
            current,
            context,
            mouseX.toInt(),
            mouseY.toInt(),
            owoPartialTick(tickCounter),
        )
    }

    /**
     * Routes a raw button event to the panels.
     * Returns true when a panel component consumed the click.
     */
    fun onMouseButton(button: Int, pressed: Boolean): Boolean {
        val current = adapter ?: return false
        return if (pressed) {
            owoMouseClick(current, mouseX, mouseY, button)
        } else {
            owoMouseRelease(current, mouseX, mouseY, button)
        }
    }

    fun onMouseDrag(button: Int): Boolean {
        val current = adapter ?: return false
        return owoMouseDrag(current, mouseX, mouseY, button, 0.0, 0.0)
    }

    fun onMouseScroll(vertical: Double): Boolean {
        val current = adapter ?: return false
        return owoMouseScroll(current, mouseX, mouseY, 0.0, vertical)
    }

    fun selectTool(index: Int) {
        selectedToolIndex = index
        rebuildToolTiles()
    }

    private fun updateCursorPosition(current: OwoUIAdapter<*>) {
        val root = rootFlow ?: return
        val client = MinecraftClient.getInstance()
        val window = client.window
        val windowWidth = window.width.toDouble().coerceAtLeast(1.0)
        val windowHeight = window.height.toDouble().coerceAtLeast(1.0)
        MemoryStack.stackPush().use { stack ->
            val xBuffer = stack.mallocDouble(1)
            val yBuffer = stack.mallocDouble(1)
            GLFW.glfwGetCursorPos(window.handle, xBuffer, yBuffer)
            mouseX = xBuffer.get(0) / windowWidth * root.width().toDouble()
            mouseY = yBuffer.get(0) / windowHeight * root.height().toDouble()
        }
    }

    private fun pollSliders() {
        flightSlider?.let { slider ->
            val value = slider.value()
            if (value != lastFlightSliderValue) {
                lastFlightSliderValue = value
                flightSpeedPercent = value.toInt().coerceIn(25, 400)
            }
        }
        stabilizationSlider?.let { slider ->
            val value = slider.value()
            if (value != lastStabilizationSliderValue) {
                lastStabilizationSliderValue = value
                stabilizationPercent = value.toInt().coerceIn(0, 100)
            }
        }
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private fun buildLayout(root: FlowLayout, width: Int, height: Int) {
        // Opaque backing behind everything: with the frame squish active the
        // margins hold stale framebuffer pixels until this repaints them.
        root.child(buildMarginCover(width, height))
        root.child(buildTopBar(width))
        root.child(buildViewsBar())
        root.child(buildToolsPanel())
        root.child(buildRightColumn(width))
        root.child(buildBottomBar(height))
    }

    /**
     * Covers every pixel outside the game frame with editor chrome. The frame
     * rectangle itself stays untouched so the world shows through.
     */
    private fun buildMarginCover(width: Int, height: Int): FlowLayout {
        val cover = owoHorizontalFlow(Sizing.fill(), Sizing.fill())
        cover.surface(Surface.BLANK)

        val f = EditorFrameController
        if (!AxionEditorMode.isActive() || !EditorFramePlatform.supportsFraming) {
            return cover
        }

        fun block(x: Int, y: Int, w: Int, h: Int) {
            if (w <= 0 || h <= 0) return
            val rect = owoHorizontalFlow(Sizing.fixed(w), Sizing.fixed(h))
            rect.surface(Surface.flat(0xF0101013.toInt()))
            rect.positioning(Positioning.absolute(x, y))
            cover.child(rect)
        }

        val top = TOP_BAR_HEIGHT + VIEWS_BAR_HEIGHT
        val bottomStart = height - BOTTOM_BAR_HEIGHT

        block(0, top, width, f.frameY - top)                       // above frame (views bar gap)
        block(0, bottomStart, width, height - bottomStart)         // below frame (hint bar gap)
        block(0, f.frameY, f.frameX, f.frameHeightGui)             // left of frame
        block(f.frameX + f.frameWidthGui, f.frameY, width - f.frameX - f.frameWidthGui, f.frameHeightGui) // right of frame
        return cover
    }

    private fun buildTopBar(width: Int): FlowLayout {
        val bar = owoHorizontalFlow(Sizing.fill(), Sizing.fixed(TOP_BAR_HEIGHT))
        bar.surface(CHROME)
        bar.padding(Insets.of(3, 2, 6, 6))

        for (menu in listOf("File", "Edit", "Create", "Operations", "Help")) {
            bar.child(grayLabel(menu).margins(Insets.horizontal(7)))
        }

        val title = owoBoldLabel("Axion")
        title.positioning(Positioning.absolute(width - 42, 5))
        bar.child(title)
        return bar
    }

    private fun buildViewsBar(): FlowLayout {
        val bar = owoHorizontalFlow(Sizing.fill(), Sizing.fixed(VIEWS_BAR_HEIGHT))
        bar.surface(CHROME)
        bar.padding(Insets.of(2, 2, 8, 6))
        bar.child(grayLabel("Main View"))
        return bar
    }

    private fun buildToolsPanel(): FlowLayout {
        val panel = owoVerticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.content())
        panel.surface(CHROME)
        panel.positioning(Positioning.absolute(8, TOP_BAR_HEIGHT + VIEWS_BAR_HEIGHT + 8))
        panel.padding(Insets.of(6))

        panel.child(boldLabel("Tools").margins(Insets.bottom(4)))

        val tileHost = owoVerticalFlow(Sizing.fill(), Sizing.content())
        toolsTileHost = tileHost
        panel.child(tileHost)
        rebuildToolTiles()

        val speedRow = buildSliderRow("Speed", 25, 400)
        flightSlider = speedRow.second
        flightSlider?.value(flightSpeedPercent.toDouble())
        panel.child(speedRow.first)

        val stabilRow = buildSliderRow("Stabilization", 0, 100)
        stabilizationSlider = stabilRow.second
        stabilizationSlider?.value(stabilizationPercent.toDouble())
        panel.child(stabilRow.first)

        return panel
    }

    private fun rebuildToolTiles() {
        val host = toolsTileHost ?: return
        host.clearChildren()

        var row: FlowLayout? = null
        for ((index, tool) in TOOLS.withIndex()) {
            if (index % TOOLS_PER_ROW == 0) {
                row = owoHorizontalFlow(Sizing.content(), Sizing.fixed(TOOL_TILE))
                row.margins(Insets.bottom(2))
                host.child(row!!)
            }
            row!!.child(toolTile(tool.first, index, tool.second))
        }
    }

    private fun toolTile(id: String, index: Int, label: String): FlowLayout {
        val tile = owoHorizontalFlow(Sizing.fixed(TOOL_TILE), Sizing.fixed(TOOL_TILE))
        tile.surface(if (index == selectedToolIndex) TILE_SELECTED else TILE)
        tile.margins(Insets.right(2))

        val icon = owoTexture(
            VersionCompatImpl.identifierOf("axion", "textures/gui/editor/tools/$id"),
            0, 0,
            TOOL_TILE - 2, TOOL_TILE - 2,
            32, 32,
        )
        icon.positioning(Positioning.absolute(1, 1))
        tile.child(icon)

        owoSubscribeMouseDown(tile) { _ ->
            selectTool(index)
            true
        }
        return tile
    }

    private fun buildSliderRow(
        label: String,
        min: Int,
        max: Int,
    ): Pair<FlowLayout, DiscreteSliderComponent> {
        val row = owoVerticalFlow(Sizing.fill(), Sizing.content())
        row.margins(Insets.bottom(3))

        row.child(grayLabel(label))

        val slider = owoDiscreteSlider(Sizing.fill(), min.toDouble(), max.toDouble())
        slider.decimalPlaces(0)
        row.child(slider)
        return row to slider
    }

    private fun buildRightColumn(width: Int): FlowLayout {
        val column = owoVerticalFlow(Sizing.fixed(PANEL_WIDTH), Sizing.content())
        column.positioning(Positioning.absolute(width - PANEL_WIDTH - 8, TOP_BAR_HEIGHT + VIEWS_BAR_HEIGHT + 8))

        column.child(collapsibleSection("Clipboard", ::buildClipboardBody))
        column.child(collapsibleSection("Palette") { buildPaletteBody(it) })
        column.child(collapsibleSection("Active Block") { buildActiveBlockBody(it) })
        column.child(collapsibleSection("History") { buildHistoryBody(it) })
        column.child(collapsibleSection("World Properties") { buildWorldPropertiesBody(it) })
        return column
    }

    private fun collapsibleSection(
        title: String,
        body: (CollapsibleContainer) -> Unit,
    ): CollapsibleContainer {
        val section = owoCollapsible(
            Sizing.fill(),
            Sizing.content(),
            Text.literal(title),
            true,
        )
        section.surface(CHROME)
        section.padding(Insets.of(4))
        body(section)
        return section
    }

    private fun buildClipboardBody(section: CollapsibleContainer) {
        section.child(grayLabel("Copy: Ctrl+C • Paste: Ctrl+V"))
        section.child(grayLabel("Cut: Ctrl+X • Delete: Del"))
    }

    private fun buildPaletteBody(section: CollapsibleContainer) {
        section.child(grayLabel("Palette arrives with block picking"))
    }

    private fun buildActiveBlockBody(section: CollapsibleContainer) {
        val label = owoLabel(Text.literal(heldItemName()))
        activeBlockLabel = label
        section.child(label)
    }

    private fun buildHistoryBody(section: CollapsibleContainer) {
        val row = owoHorizontalFlow(Sizing.content(), Sizing.content())

        val undo = owoButton(Text.literal("Undo")) {
            UndoRedoController.undo(MinecraftClient.getInstance())
        }
        undo.sizing(Sizing.fixed(88), Sizing.fixed(16))
        undo.margins(Insets.right(4))

        val redo = owoButton(Text.literal("Redo")) {
            UndoRedoController.redo(MinecraftClient.getInstance())
        }
        redo.sizing(Sizing.fixed(88), Sizing.fixed(16))

        row.child(undo)
        row.child(redo)
        section.child(row)
    }

    private fun buildWorldPropertiesBody(section: CollapsibleContainer) {
        val time = owoCollapsible(Sizing.fill(), Sizing.content(), Text.literal("Time"), false)
        time.padding(Insets.of(3))
        val timeSlider = owoDiscreteSlider(Sizing.fill(), 0.0, 24000.0)
        timeSlider.decimalPlaces(0)
        time.child(timeSlider)
        time.child(commandButton("Set Time") {
            "time set ${timeSlider.value().toInt()}"
        })

        val weather = owoCollapsible(Sizing.fill(), Sizing.content(), Text.literal("Weather"), false)
        weather.padding(Insets.of(3))
        weather.child(
            commandButtonRow(
                listOf(
                    "Clear" to "weather clear",
                    "Rain" to "weather rain",
                    "Thunder" to "weather thunder",
                ),
            ),
        )

        section.child(time)
        section.child(weather)
    }

    private fun commandButton(label: String, command: () -> String): ButtonComponent {
        val button = owoButton(Text.literal(label)) {
            VersionCompatImpl.sendGameModeCommand(MinecraftClient.getInstance(), command())
        }
        button.sizing(Sizing.fill(), Sizing.fixed(14))
        return button
    }

    private fun commandButtonRow(commands: List<Pair<String, String>>): FlowLayout {
        val row = owoHorizontalFlow(Sizing.content(), Sizing.content())
        for ((label, command) in commands) {
            val button = owoButton(Text.literal(label)) {
                VersionCompatImpl.sendGameModeCommand(MinecraftClient.getInstance(), command)
            }
            button.sizing(Sizing.fixed(56), Sizing.fixed(14))
            button.margins(Insets.right(3))
            row.child(button)
        }
        return row
    }

    private fun buildBottomBar(height: Int): FlowLayout {
        val bar = owoHorizontalFlow(Sizing.fill(), Sizing.fixed(BOTTOM_BAR_HEIGHT))
        bar.surface(CHROME)
        bar.positioning(Positioning.absolute(0, height - BOTTOM_BAR_HEIGHT))
        bar.padding(Insets.of(4, 0, 8, 0))
        bar.child(grayLabel("LMB Tool • RMB Drag Look • WASD Fly • Ctrl Sprint • RShift Exit"))
        return bar
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun grayLabel(text: String): LabelComponent = owoGrayLabel(text)

    private fun boldLabel(text: String): LabelComponent = owoBoldLabel(text)

    private fun heldItemName(): String {
        val stack = MinecraftClient.getInstance().player?.mainHandStack ?: return "—"
        if (stack.isEmpty) return "—"
        return stack.name.string
    }
}
