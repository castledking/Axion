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
    vec4 color = texColor * vertexColor;
    fragColor = color * ColorModulator;
}
