package axion.client.editor.ui

import io.wispforest.owo.ui.component.ButtonComponent
import io.wispforest.owo.ui.component.Components
import io.wispforest.owo.ui.component.DiscreteSliderComponent
import io.wispforest.owo.ui.component.LabelComponent
import io.wispforest.owo.ui.component.TextureComponent
import io.wispforest.owo.ui.container.CollapsibleContainer
import io.wispforest.owo.ui.container.Containers
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.core.Sizing
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * Legacy owo generation (0.12.x): factories live on Components/Containers.
 * Shared editor code only ever sees these [owo*] wrappers.
 */
internal fun owoHorizontalFlow(horizontal: Sizing, vertical: Sizing): FlowLayout =
    Containers.horizontalFlow(horizontal, vertical)

internal fun owoVerticalFlow(horizontal: Sizing, vertical: Sizing): FlowLayout =
    Containers.verticalFlow(horizontal, vertical)

internal fun owoCollapsible(
    horizontal: Sizing,
    vertical: Sizing,
    title: Text,
    expanded: Boolean,
): CollapsibleContainer = Containers.collapsible(horizontal, vertical, title, expanded)

internal fun owoLabel(text: Text): LabelComponent = Components.label(text)

internal fun owoButton(text: Text, onPress: () -> Unit): ButtonComponent =
    Components.button(text) { _ -> onPress() }

internal fun owoDiscreteSlider(width: Sizing, min: Double, max: Double): DiscreteSliderComponent =
    Components.discreteSlider(width, min, max)

internal fun owoTexture(
    texture: Identifier,
    u: Int,
    v: Int,
    regionWidth: Int,
    regionHeight: Int,
    textureWidth: Int,
    textureHeight: Int,
): TextureComponent = Components.texture(texture, u, v, regionWidth, regionHeight, textureWidth, textureHeight)

internal fun owoGrayLabel(text: String): LabelComponent =
    Components.label(Text.literal(text).formatted(net.minecraft.util.Formatting.GRAY))

internal fun owoBoldLabel(text: String): LabelComponent =
    Components.label(Text.literal(text).formatted(net.minecraft.util.Formatting.BOLD))
