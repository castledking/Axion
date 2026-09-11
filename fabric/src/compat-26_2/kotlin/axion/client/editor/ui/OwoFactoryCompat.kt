package axion.client.editor.ui

import io.wispforest.owo.ui.component.ButtonComponent
import io.wispforest.owo.ui.component.DiscreteSliderComponent
import io.wispforest.owo.ui.component.LabelComponent
import io.wispforest.owo.ui.component.TextureComponent
import io.wispforest.owo.ui.component.UIComponents
import io.wispforest.owo.ui.container.CollapsibleContainer
import io.wispforest.owo.ui.container.FlowLayout
import io.wispforest.owo.ui.container.UIContainers
import io.wispforest.owo.ui.core.Sizing
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.Identifier

/**
 * Modern owo generation (0.13.x): factories live on UIComponents/UIContainers.
 * Shared editor code only ever sees these [owo*] wrappers.
 */
internal fun owoHorizontalFlow(horizontal: Sizing, vertical: Sizing): FlowLayout =
    UIContainers.horizontalFlow(horizontal, vertical)

internal fun owoVerticalFlow(horizontal: Sizing, vertical: Sizing): FlowLayout =
    UIContainers.verticalFlow(horizontal, vertical)

internal fun owoCollapsible(
    horizontal: Sizing,
    vertical: Sizing,
    title: Text,
    expanded: Boolean,
): CollapsibleContainer = UIContainers.collapsible(horizontal, vertical, title, expanded)

internal fun owoLabel(text: Text): LabelComponent = UIComponents.label(text)

internal fun owoButton(text: Text, onPress: () -> Unit): ButtonComponent =
    UIComponents.button(text) { _ -> onPress() }

internal fun owoDiscreteSlider(width: Sizing, min: Double, max: Double): DiscreteSliderComponent =
    UIComponents.discreteSlider(width, min, max)

internal fun owoTexture(
    texture: Identifier,
    u: Int,
    v: Int,
    regionWidth: Int,
    regionHeight: Int,
    textureWidth: Int,
    textureHeight: Int,
): TextureComponent = UIComponents.texture(texture, u, v, regionWidth, regionHeight, textureWidth, textureHeight)

internal fun owoGrayLabel(text: String): LabelComponent =
    UIComponents.label(Text.literal(text).withStyle(net.minecraft.ChatFormatting.GRAY))

internal fun owoBoldLabel(text: String): LabelComponent =
    UIComponents.label(Text.literal(text).withStyle(net.minecraft.ChatFormatting.BOLD))


val PlayerEntity.mainHandStack: ItemStack
    get() = getItemInHand(Hand.MAIN_HAND)

val ItemStack.name: Text
    get() = hoverName
