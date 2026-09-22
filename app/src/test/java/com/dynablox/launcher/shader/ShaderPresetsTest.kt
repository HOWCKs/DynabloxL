package com.dynablox.launcher.shader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Shader Lab compiles GLSL ES 1.00 on the device, so a typo only shows up as a driver error at
 * runtime. These tests catch the structural mistakes that make a program fail to link: a missing
 * `main`, an unbalanced brace, a preset that ignores the shared uniforms, or two presets sharing an
 * id (which would silently overwrite each other in settings).
 */
class ShaderPresetsTest {

    @Test
    fun `preset ids are unique and lookup falls back to the first preset`() {
        val ids = ShaderPresets.ALL.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(ShaderPresets.ALL.first(), ShaderPresets.byId("nope"))
        assertEquals(ShaderPresets.ALL.first(), ShaderPresets.byId(ShaderPresets.ALL.first().id))
    }

    @Test
    fun `every preset declares a fragment entry point and writes a colour`() {
        ShaderPresets.ALL.forEach { preset ->
            assertTrue("${preset.id} has no main()", preset.effect.contains("void main()"))
            assertTrue("${preset.id} never writes gl_FragColor", preset.effect.contains("gl_FragColor"))
        }
    }

    @Test
    fun `the assembled source always carries the shared uniforms and scene`() {
        ShaderPresets.ALL.forEach { preset ->
            val source = ShaderPresets.source(preset)
            assertTrue("${preset.id} lost the precision qualifier", source.contains("precision mediump float"))
            listOf("uTime", "uResolution", "uAmount").forEach { uniform ->
                assertTrue("${preset.id} lost $uniform", source.contains("uniform float $uniform") ||
                    source.contains("uniform vec2 $uniform"))
            }
            assertTrue("${preset.id} lost the procedural scene", source.contains("vec3 scene(vec2 uv)"))
        }
    }

    @Test
    fun `braces are balanced in every program`() {
        val vertex = ShaderPresets.VERTEX_SOURCE
        assertEquals("vertex shader braces", vertex.count { it == '{' }, vertex.count { it == '}' })
        ShaderPresets.ALL.forEach { preset ->
            val source = ShaderPresets.source(preset)
            assertEquals(
                "${preset.id} has unbalanced braces",
                source.count { it == '{' },
                source.count { it == '}' },
            )
            assertEquals(
                "${preset.id} has unbalanced parentheses",
                source.count { it == '(' },
                source.count { it == ')' },
            )
        }
    }

    @Test
    fun `loops are bounded by constants, as GLSL ES 1_00 requires`() {
        ShaderPresets.ALL.forEach { preset ->
            Regex("for\\s*\\(([^)]*)\\)").findAll(preset.effect).forEach { match ->
                val header = match.groupValues[1]
                assertTrue(
                    "${preset.id} uses a non-constant loop bound: $header",
                    Regex("i\\s*<\\s*\\d+").containsMatchIn(header),
                )
            }
        }
    }

    @Test
    fun `parameters are unique per preset and stay inside 0__1`() {
        ShaderPresets.ALL.forEach { preset ->
            val ids = preset.params.map { it.id }
            assertEquals("${preset.id} repeats a parameter", ids.size, ids.distinct().size)
            preset.params.forEach { param ->
                assertTrue("${preset.id}/${param.id} default out of range", param.default in 0f..1f)
                assertTrue("${preset.id}/${param.id} has no label", param.labelRes != 0)
            }
        }
    }

    @Test
    fun `the vertex stage feeds the varying the fragments read`() {
        val vertex = ShaderPresets.VERTEX_SOURCE
        assertTrue(vertex.contains("attribute vec2 aPosition"))
        assertTrue(vertex.contains("attribute vec2 aTexCoord"))
        assertTrue(vertex.contains("varying vec2 vUv"))
        ShaderPresets.ALL.forEach { preset ->
            assertTrue("${preset.id} never reads vUv", preset.effect.contains("vUv") ||
                ShaderPresets.source(preset).contains("vUv"))
        }
    }

    @Test
    fun `every preset is named for the user`() {
        ShaderPresets.ALL.forEach { preset ->
            assertTrue("${preset.id} has no name resource", preset.nameRes != 0)
        }
    }
}
