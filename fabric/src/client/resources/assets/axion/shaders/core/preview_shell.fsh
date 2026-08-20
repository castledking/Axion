#version 150

uniform sampler2D Sampler0;

#moj_import <minecraft:dynamictransforms.glsl>

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.1) {
        discard;
    }
    vec4 color = texColor * vertexColor * ColorModulator;
#ifdef IGNORE_TEXTURE_ALPHA
    // Destination ghosts get their opacity from a policy constant
    // (PreviewVisualPolicy.DESTINATION_ALPHA, derived for an exact number of
    // shell crossings), so a translucent block's texel alpha must not compound
    // into it. The texture still decides the silhouette through the cutout
    // above and still supplies the colour. Move-source glass deliberately does
    // not define this: its transmission budget is computed *with* stained-glass
    // texel alpha (PreviewVisualPolicy.compoundedTexturedTransmission).
    color.a = vertexColor.a * ColorModulator.a;
#endif
    fragColor = color;
}
