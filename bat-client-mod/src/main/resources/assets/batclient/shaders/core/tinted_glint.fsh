#version 150
#moj_import <fog.glsl>
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 BatTint;
uniform float FogStart;
uniform float FogEnd;
uniform float GlintAlpha;
in float vertexDistance;
in vec2 texCoord0;
out vec4 fragColor;
void main() {
    vec4 sampleColor = texture(Sampler0, texCoord0);
    if (sampleColor.a * ColorModulator.a < 0.1) discard;
    float brightness = max(sampleColor.r, max(sampleColor.g, sampleColor.b));
    float fade = linear_fog_fade(vertexDistance, FogStart, FogEnd) * GlintAlpha;
    fragColor = vec4(vec3(brightness) * BatTint.rgb * ColorModulator.rgb * fade * BatTint.a,
                     sampleColor.a * ColorModulator.a);
}
