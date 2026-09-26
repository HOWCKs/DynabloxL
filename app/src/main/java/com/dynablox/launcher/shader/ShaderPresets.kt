package com.dynablox.launcher.shader

import androidx.annotation.StringRes
import com.dynablox.launcher.R

/** A tunable uniform exposed in the UI as a fader/knob. */
data class ShaderParam(
    val id: String,
    @StringRes val labelRes: Int,
    val default: Float,
)

/**
 * A GLSL ES 1.00 fragment program plus its tunables.
 *
 * These are real shaders: they are compiled on the GPU by [ShaderLabRenderer] and every preset runs
 * on top of the same procedurally generated test scene, so what you see is what the GPU computed —
 * no bitmaps, no fakery.
 */
data class ShaderPreset(
    val id: String,
    @StringRes val nameRes: Int,
    val params: List<ShaderParam>,
    val effect: String,
)

object ShaderPresets {

    const val VERTEX_SOURCE = """
        attribute vec2 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vUv;
        void main() {
            vUv = aTexCoord;
            gl_Position = vec4(aPosition, 0.0, 1.0);
        }
    """

    /** Shared preamble: uniforms + the animated test scene every preset post-processes. */
    private const val HEADER = """
        precision mediump float;
        varying vec2 vUv;
        uniform float uTime;
        uniform vec2 uResolution;
        uniform float uAmount;
        uniform float uParamB;
        uniform float uParamC;

        float hash(vec2 p) {
            return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
        }

        vec3 scene(vec2 uv) {
            vec3 sky = mix(vec3(0.045, 0.085, 0.135), vec3(0.10, 0.24, 0.33), clamp(uv.y, 0.0, 1.0));
            float horizon = smoothstep(0.40, 0.46, uv.y);
            vec3 ground = mix(vec3(0.02, 0.035, 0.05), vec3(0.05, 0.13, 0.11), 1.0 - clamp(uv.y, 0.0, 1.0));
            vec3 col = mix(sky, ground, horizon);

            for (int i = 0; i < 5; i++) {
                float fi = float(i);
                float t = uTime * (0.16 + 0.05 * fi);
                vec2 centre = vec2(fract(t + fi * 0.37) * 1.4 - 0.2, 0.14 + 0.11 * fi);
                float half_size = 0.045 + 0.012 * fi;
                vec2 d = abs(uv - centre) - vec2(half_size, half_size);
                float box = 1.0 - smoothstep(0.0, 0.006, max(d.x, d.y));
                vec3 tint = vec3(0.18 + 0.10 * fi, 0.52 - 0.05 * fi, 0.92 - 0.10 * fi);
                col = mix(col, tint, box * 0.88);
            }

            float sun = 1.0 - smoothstep(0.025, 0.085, length(uv - vec2(0.74, 0.78)));
            col += vec3(1.0, 0.86, 0.62) * sun * 0.85;

            vec2 g = fract(uv * vec2(15.0, 9.0)) - 0.5;
            float grid = smoothstep(0.44, 0.50, max(abs(g.x), abs(g.y)));
            col += vec3(0.05, 0.11, 0.15) * grid * (1.0 - horizon) * 0.55;
            return col;
        }
    """

    private val PARAM_AMOUNT = ShaderParam("amount", R.string.shader_param_amount, 0.55f)
    private val PARAM_GRAIN = ShaderParam("grain", R.string.shader_param_grain, 0.35f)
    private val PARAM_SPEED = ShaderParam("speed", R.string.shader_param_speed, 0.5f)

    val ALL: List<ShaderPreset> = listOf(
        ShaderPreset(
            id = "crt",
            nameRes = R.string.shader_preset_crt,
            params = listOf(PARAM_AMOUNT, PARAM_GRAIN, PARAM_SPEED),
            effect = """
                vec2 barrel(vec2 uv, float k) {
                    vec2 c = uv - 0.5;
                    return 0.5 + c * (1.0 + k * dot(c, c));
                }
                void main() {
                    vec2 uv = barrel(vUv, 0.20 * uAmount);
                    vec3 col = scene(uv);
                    float scan = sin(uv.y * uResolution.y * 1.7) * 0.5 + 0.5;
                    col *= 1.0 - 0.30 * uAmount * scan;
                    float cell = mod(gl_FragCoord.x, 3.0);
                    vec3 mask = vec3(1.0);
                    if (cell < 1.0) { mask = vec3(1.18, 0.86, 0.86); }
                    else if (cell < 2.0) { mask = vec3(0.86, 1.18, 0.86); }
                    else { mask = vec3(0.86, 0.86, 1.18); }
                    col *= mix(vec3(1.0), mask, 0.38 * uAmount);
                    col += (hash(uv * uResolution + uTime) - 0.5) * 0.10 * uParamB;
                    float vig = smoothstep(0.95, 0.30, length(uv - 0.5));
                    col *= mix(1.0, vig, uAmount);
                    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) { col *= 0.12; }
                    gl_FragColor = vec4(col, 1.0);
                }
            """,
        ),
        ShaderPreset(
            id = "bloom",
            nameRes = R.string.shader_preset_bloom,
            params = listOf(PARAM_AMOUNT, PARAM_GRAIN),
            effect = """
                void main() {
                    vec3 base = scene(vUv);
                    vec2 px = 1.0 / max(uResolution, vec2(1.0));
                    vec3 bloom = vec3(0.0);
                    float radius = (5.0 + 16.0 * uAmount);
                    for (int i = 0; i < 8; i++) {
                        float a = float(i) * 0.7853981;
                        vec2 off = vec2(cos(a), sin(a)) * px * radius;
                        vec3 s = scene(vUv + off);
                        float l = dot(s, vec3(0.299, 0.587, 0.114));
                        bloom += s * smoothstep(0.52, 1.0, l);
                    }
                    bloom /= 8.0;
                    vec3 col = base + bloom * (1.05 * uAmount);
                    col += (hash(vUv * uResolution) - 0.5) * 0.03 * uParamB;
                    gl_FragColor = vec4(col, 1.0);
                }
            """,
        ),
        ShaderPreset(
            id = "chroma",
            nameRes = R.string.shader_preset_chroma,
            params = listOf(PARAM_AMOUNT, PARAM_GRAIN),
            effect = """
                void main() {
                    vec2 c = vUv - 0.5;
                    float k = 0.016 * uAmount;
                    vec3 col;
                    col.r = scene(vUv + c * k).r;
                    col.g = scene(vUv).g;
                    col.b = scene(vUv - c * k).b;
                    col += (hash(vUv * uResolution + uTime) - 0.5) * 0.12 * uParamB;
                    float vig = smoothstep(1.05, 0.30, length(c));
                    col *= mix(1.0, vig, 0.65 * uAmount);
                    gl_FragColor = vec4(col, 1.0);
                }
            """,
        ),
        ShaderPreset(
            id = "noir",
            nameRes = R.string.shader_preset_noir,
            params = listOf(PARAM_AMOUNT, PARAM_GRAIN),
            effect = """
                void main() {
                    vec3 s = scene(vUv);
                    float l = dot(s, vec3(0.299, 0.587, 0.114));
                    l = smoothstep(0.06, 0.94, l);
                    l = mix(l, pow(l, 1.45), uAmount);
                    vec3 col = vec3(l) * vec3(1.0, 0.98, 0.94);
                    col += (hash(vUv * uResolution + uTime * 3.0) - 0.5) * 0.16 * uParamB;
                    col *= mix(1.0, smoothstep(1.05, 0.28, length(vUv - 0.5)), 0.75 * uAmount);
                    gl_FragColor = vec4(col, 1.0);
                }
            """,
        ),
        ShaderPreset(
            id = "vhs",
            nameRes = R.string.shader_preset_vhs,
            params = listOf(PARAM_AMOUNT, PARAM_GRAIN, PARAM_SPEED),
            effect = """
                void main() {
                    float speed = 0.4 + uParamC * 2.2;
                    vec2 uv = vUv;
                    float wobble = sin(uv.y * 62.0 + uTime * 4.0 * speed) * 0.0045 * uAmount;
                    float band = step(0.982, fract(uv.y * 0.5 - uTime * 0.07 * speed));
                    uv.x += wobble + band * 0.022 * uAmount;
                    vec3 col;
                    col.r = scene(uv + vec2(0.009 * uAmount, 0.0)).r;
                    col.g = scene(uv).g;
                    col.b = scene(uv - vec2(0.009 * uAmount, 0.0)).b;
                    float n = hash(uv * uResolution + uTime * 7.0 * speed);
                    col = mix(col, vec3(n), 0.20 * uParamB);
                    col += band * 0.14 * uAmount;
                    gl_FragColor = vec4(col, 1.0);
                }
            """,
        ),
        ShaderPreset(
            id = "thermal",
            nameRes = R.string.shader_preset_thermal,
            params = listOf(PARAM_AMOUNT, PARAM_GRAIN),
            effect = """
                vec3 heat(float t) {
                    t = clamp(t, 0.0, 1.0);
                    vec3 a = vec3(0.02, 0.02, 0.12);
                    vec3 b = vec3(0.22, 0.10, 0.62);
                    vec3 c = vec3(0.90, 0.20, 0.20);
                    vec3 d = vec3(1.00, 0.78, 0.18);
                    vec3 e = vec3(1.00, 1.00, 0.95);
                    if (t < 0.25) { return mix(a, b, t / 0.25); }
                    if (t < 0.50) { return mix(b, c, (t - 0.25) / 0.25); }
                    if (t < 0.75) { return mix(c, d, (t - 0.50) / 0.25); }
                    return mix(d, e, (t - 0.75) / 0.25);
                }
                void main() {
                    vec3 s = scene(vUv);
                    float l = dot(s, vec3(0.299, 0.587, 0.114));
                    float t = mix(l, smoothstep(0.08, 0.92, l), uAmount);
                    vec3 col = heat(t);
                    col += (hash(vUv * uResolution) - 0.5) * 0.05 * uParamB;
                    gl_FragColor = vec4(col, 1.0);
                }
            """,
        ),
        ShaderPreset(
            id = "studio",
            nameRes = R.string.shader_preset_vignette,
            params = listOf(PARAM_AMOUNT),
            effect = """
                void main() {
                    vec3 col = scene(vUv);
                    float vig = smoothstep(1.10, 0.32, length(vUv - 0.5));
                    col *= mix(1.0, vig, uAmount);
                    col = mix(col, col * col * (3.0 - 2.0 * col), 0.35 * uAmount);
                    gl_FragColor = vec4(col, 1.0);
                }
            """,
        ),
    )

    fun byId(id: String): ShaderPreset = ALL.firstOrNull { it.id == id } ?: ALL.first()

    fun source(preset: ShaderPreset): String = HEADER + preset.effect
}
