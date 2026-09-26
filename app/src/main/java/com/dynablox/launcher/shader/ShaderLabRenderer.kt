package com.dynablox.launcher.shader

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES 2.0 renderer for the Shader Lab.
 *
 * Compiles [ShaderPresets] at runtime, reports the driver's info log when compilation fails and
 * falls back to the simplest preset, measures its own render rate and drives `uTime` from a
 * monotonic clock so animation speed is independent of frame rate.
 */
class ShaderLabRenderer : GLSurfaceView.Renderer {

    @Volatile
    var preset: ShaderPreset = ShaderPresets.ALL.first()
        set(value) {
            if (field.id != value.id) {
                field = value
                rebuildRequested = true
            }
        }

    @Volatile
    var amount: Float = 0.55f

    @Volatile
    var paramB: Float = 0.35f

    @Volatile
    var paramC: Float = 0.5f

    @Volatile
    var timeScale: Float = 1f

    @Volatile
    var paused: Boolean = false

    /** Driver string captured on the GL thread — safe to read from the UI. */
    @Volatile
    var glVersion: String? = null

    var onCompileError: ((String) -> Unit)? = null
    var onPresetReady: ((String) -> Unit)? = null
    var onRenderFps: ((Float) -> Unit)? = null

    private var program = 0
    private var positionLocation = -1
    private var texCoordLocation = -1
    private var timeUniform = -1
    private var resolutionUniform = -1
    private var amountUniform = -1
    private var paramBUniform = -1
    private var paramCUniform = -1

    private var rebuildRequested = true
    private var width = 1
    private var height = 1

    private var frames = 0
    private var fpsWindowStart = 0L
    private var elapsedSeconds = 0f
    private var lastFrameMs = 0L

    private val vertexBuffer: FloatBuffer = floatBuffer(
        floatArrayOf(
            // x, y, u, v
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        ),
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        rebuildRequested = true
        elapsedSeconds = 0f
        glVersion = try {
            GLES20.glGetString(GLES20.GL_VERSION)
        } catch (_: Throwable) {
            null
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, this.width, this.height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (rebuildRequested) {
            rebuild()
            rebuildRequested = false
        }
        if (program == 0) return

        val now = SystemClock.elapsedRealtime()
        if (lastFrameMs != 0L && !paused) {
            elapsedSeconds += (now - lastFrameMs) / 1000f * timeScale.coerceIn(0f, 3f)
        }
        lastFrameMs = now

        GLES20.glClearColor(0.03f, 0.04f, 0.05f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, STRIDE, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordLocation)
        vertexBuffer.position(2)
        GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT, false, STRIDE, vertexBuffer)
        vertexBuffer.position(0)

        if (timeUniform >= 0) GLES20.glUniform1f(timeUniform, elapsedSeconds)
        if (resolutionUniform >= 0) GLES20.glUniform2f(resolutionUniform, width.toFloat(), height.toFloat())
        if (amountUniform >= 0) GLES20.glUniform1f(amountUniform, amount.coerceIn(0f, 1f))
        if (paramBUniform >= 0) GLES20.glUniform1f(paramBUniform, paramB.coerceIn(0f, 1f))
        if (paramCUniform >= 0) GLES20.glUniform1f(paramCUniform, paramC.coerceIn(0f, 1f))

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(texCoordLocation)

        frames++
        if (fpsWindowStart == 0L) fpsWindowStart = now
        val elapsed = now - fpsWindowStart
        if (elapsed >= 500L) {
            onRenderFps?.invoke(frames * 1000f / elapsed)
            frames = 0
            fpsWindowStart = now
        }
    }

    private fun rebuild() {
        deleteProgram()
        val target = preset
        val fragment = ShaderPresets.source(target)
        val vertex = compile(GLES20.GL_VERTEX_SHADER, ShaderPresets.VERTEX_SOURCE)
        val frag = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        if (vertex == 0 || frag == 0) {
            val fallback = ShaderPresets.byId("studio")
            if (fallback.id != target.id) {
                preset = fallback
                rebuildRequested = true
            }
            return
        }

        val linked = GLES20.glCreateProgram()
        GLES20.glAttachShader(linked, vertex)
        GLES20.glAttachShader(linked, frag)
        GLES20.glLinkProgram(linked)
        val status = IntArray(1)
        GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(frag)

        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(linked)
            Log.e(TAG, "link failed: $log")
            onCompileError?.invoke(log)
            GLES20.glDeleteProgram(linked)
            return
        }

        program = linked
        positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord")
        timeUniform = GLES20.glGetUniformLocation(program, "uTime")
        resolutionUniform = GLES20.glGetUniformLocation(program, "uResolution")
        amountUniform = GLES20.glGetUniformLocation(program, "uAmount")
        paramBUniform = GLES20.glGetUniformLocation(program, "uParamB")
        paramCUniform = GLES20.glGetUniformLocation(program, "uParamC")
        onPresetReady?.invoke(target.id)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "shader compile failed: $log")
            onCompileError?.invoke(log)
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun deleteProgram() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        positionLocation = -1
        texCoordLocation = -1
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(values); position(0) }

    companion object {
        private const val TAG = "ShaderLabRenderer"
        private const val STRIDE = 4 * 4
    }
}
