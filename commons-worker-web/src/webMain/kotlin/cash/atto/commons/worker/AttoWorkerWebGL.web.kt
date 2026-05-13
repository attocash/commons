@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.worker

import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.isValid
import cash.atto.commons.toByteArray
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.js.ExperimentalWasmJsInterop

actual fun AttoWorker.Companion.isWebglSupported(): Boolean = isWebGLSupported()

actual class AttoWorkerWebGL actual constructor() : AttoWorker {
    private var state: WebGLState? = null
    private var closed = false

    init {
        require(isWebGLSupported()) { "WebGL2 API not available." }
    }

    actual override suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork {
        check(!closed) { "AttoWorkerWebGL is closed." }
        require(target.value.size == WEBGL_WORK_TARGET_SIZE) {
            "WebGL worker target must contain $WEBGL_WORK_TARGET_SIZE bytes but contains ${target.value.size}."
        }

        val state = state()
        val targetWords = target.value.toWebGLWords()
        val batchSize = state.configuration.invocations.toULong() * state.configuration.iterations.toULong()
        var startNonce = 0UL

        while (true) {
            currentCoroutineContext().ensureActive()

            state.dispatch(
                thresholdLow = threshold.webglLowInt(),
                thresholdHigh = threshold.webglHighInt(),
                startNonceLow = startNonce.webglLowInt(),
                startNonceHigh = startNonce.webglHighInt(),
                targetWords = targetWords,
            )

            val result = state.readResult()
            if (result.found) {
                val nonceLow = result.nonceLow.toUInt().toULong()
                val nonceHigh = result.nonceHigh.toUInt().toULong()
                val nonce = nonceLow or (nonceHigh shl 32)
                val work = AttoWork(nonce.toByteArray())
                check(AttoWork.isValid(threshold, target, work.value)) {
                    "WebGL worker returned invalid work."
                }
                return work
            }

            startNonce += batchSize
        }
    }

    actual override fun close() {
        closed = true
        state?.close()
        state = null
    }

    private fun state(): WebGLState {
        state?.let { return it }

        val gl = createWebGLContext() ?: throw IllegalStateException("WebGL2 API not available.")
        val configuration = createWebGLConfiguration(gl)
        val program = createWebGLProgram(gl, webGLVertexShaderSource(), webGLFragmentShaderSource(configuration.iterations))
        val texture = createWebGLResultTexture(gl, configuration.width, configuration.height)
        val framebuffer = createWebGLFramebuffer(gl, texture)

        val created =
            WebGLState(
                gl = gl,
                program = program,
                texture = texture,
                framebuffer = framebuffer,
                configuration = configuration,
            )
        state = created
        return created
    }
}

private class WebGLState(
    private val gl: WebGL2RenderingContext,
    private val program: WebGLProgram,
    private val texture: WebGLTexture,
    private val framebuffer: WebGLFramebuffer,
    val configuration: WebGLWorkConfiguration,
) {
    private val pixels = uint32Array(configuration.invocations * WEBGL_RESULT_WORDS)

    fun dispatch(
        thresholdLow: Int,
        thresholdHigh: Int,
        startNonceLow: Int,
        startNonceHigh: Int,
        targetWords: IntArray,
    ) {
        useWebGLProgram(gl, program)
        bindWebGLFramebuffer(gl, framebuffer)
        setWebGLViewport(gl, configuration.width, configuration.height)
        clearWebGL(gl)

        setWebGLUIntUniform(gl, program, "u_thresholdLow", thresholdLow)
        setWebGLUIntUniform(gl, program, "u_thresholdHigh", thresholdHigh)
        setWebGLUIntUniform(gl, program, "u_startNonceLow", startNonceLow)
        setWebGLUIntUniform(gl, program, "u_startNonceHigh", startNonceHigh)
        setWebGLUIntUniform(gl, program, "u_width", configuration.width)
        repeat(WEBGL_TARGET_WORDS) { index ->
            setWebGLUIntUniform(gl, program, "u_hash$index", targetWords[index])
        }

        drawWebGLBatch(gl)
    }

    fun readResult(): WebGLWorkResult {
        readWebGLPixels(gl, configuration.width, configuration.height, pixels)

        repeat(configuration.invocations) { index ->
            val offset = index * WEBGL_RESULT_WORDS
            if (uint32ArrayGet(pixels, offset + 2) != 0) {
                return WebGLWorkResult(
                    nonceLow = uint32ArrayGet(pixels, offset),
                    nonceHigh = uint32ArrayGet(pixels, offset + 1),
                    found = true,
                )
            }
        }

        return WebGLWorkResult(0, 0, false)
    }

    fun close() {
        deleteWebGLFramebuffer(gl, framebuffer)
        deleteWebGLTexture(gl, texture)
        deleteWebGLProgram(gl, program)
    }
}

private external interface WebGL2RenderingContext : JsAny

private external interface WebGLProgram : JsAny

private external interface WebGLShader : JsAny

private external interface WebGLTexture : JsAny

private external interface WebGLFramebuffer : JsAny

private external interface Uint32Array : JsAny

private data class WebGLWorkConfiguration(
    val width: Int,
    val height: Int,
    val iterations: Int,
) {
    val invocations: Int = width * height
}

private data class WebGLWorkResult(
    val nonceLow: Int,
    val nonceHigh: Int,
    val found: Boolean,
)

private const val WEBGL_WORK_TARGET_SIZE = 32
private const val WEBGL_TARGET_WORDS = 8
private const val WEBGL_RESULT_WORDS = 4
private const val WEBGL_BATCH_WIDTH = 256
private const val WEBGL_BATCH_HEIGHT = 256
private const val WEBGL_ITERATIONS = 64

private fun ULong.webglLowInt(): Int = (this and UInt.MAX_VALUE.toULong()).toUInt().toInt()

private fun ULong.webglHighInt(): Int = (this shr 32).toUInt().toInt()

private fun ByteArray.toWebGLWords(): IntArray =
    IntArray(WEBGL_TARGET_WORDS) { index ->
        val offset = index * 4
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)
    }

private fun createWebGLConfiguration(gl: WebGL2RenderingContext): WebGLWorkConfiguration {
    val maxTextureSize = getWebGLParameterInt(gl, "MAX_TEXTURE_SIZE")
    require(maxTextureSize >= WEBGL_BATCH_WIDTH && maxTextureSize >= WEBGL_BATCH_HEIGHT) {
        "WebGL2 device reports a texture size below the worker batch size."
    }

    return WebGLWorkConfiguration(
        width = WEBGL_BATCH_WIDTH,
        height = WEBGL_BATCH_HEIGHT,
        iterations = WEBGL_ITERATIONS,
    )
}

private fun isWebGLSupported(): Boolean =
    try {
        val gl = createWebGLContext() ?: return false
        val texture = createWebGLResultTexture(gl, 1, 1)
        val framebuffer = createWebGLFramebuffer(gl, texture)
        val supported = isWebGLFramebufferComplete(gl)
        deleteWebGLFramebuffer(gl, framebuffer)
        deleteWebGLTexture(gl, texture)
        supported
    } catch (_: Throwable) {
        false
    }

private fun createWebGLContext(): WebGL2RenderingContext? =
    js(
        """
        (() => {
            const canvas = typeof OffscreenCanvas === "function"
                ? new OffscreenCanvas(1, 1)
                : (globalThis.document && globalThis.document.createElement("canvas"));
            if (!canvas || typeof canvas.getContext !== "function") return null;
            return canvas.getContext("webgl2", {
                alpha: false,
                antialias: false,
                depth: false,
                stencil: false,
                preserveDrawingBuffer: false,
                powerPreference: "high-performance"
            });
        })()
        """,
    )

private fun getWebGLParameterInt(
    gl: WebGL2RenderingContext,
    parameterName: String,
): Int = js("gl.getParameter(gl[parameterName])")

private fun createWebGLProgram(
    gl: WebGL2RenderingContext,
    vertexSource: String,
    fragmentSource: String,
): WebGLProgram {
    val vertexShader = compileWebGLShader(gl, "VERTEX_SHADER", vertexSource)
    val fragmentShader = compileWebGLShader(gl, "FRAGMENT_SHADER", fragmentSource)
    val program = linkWebGLProgram(gl, vertexShader, fragmentShader)
    deleteWebGLShader(gl, vertexShader)
    deleteWebGLShader(gl, fragmentShader)
    return program
}

private fun compileWebGLShader(
    gl: WebGL2RenderingContext,
    typeName: String,
    source: String,
): WebGLShader =
    js(
        """
        (() => {
            const shader = gl.createShader(gl[typeName]);
            gl.shaderSource(shader, source);
            gl.compileShader(shader);
            if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
                const log = gl.getShaderInfoLog(shader) || "unknown shader compile error";
                gl.deleteShader(shader);
                throw new Error(log);
            }
            return shader;
        })()
        """,
    )

private fun linkWebGLProgram(
    gl: WebGL2RenderingContext,
    vertexShader: WebGLShader,
    fragmentShader: WebGLShader,
): WebGLProgram =
    js(
        """
        (() => {
            const program = gl.createProgram();
            gl.attachShader(program, vertexShader);
            gl.attachShader(program, fragmentShader);
            gl.linkProgram(program);
            if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
                const log = gl.getProgramInfoLog(program) || "unknown program link error";
                gl.deleteProgram(program);
                throw new Error(log);
            }
            return program;
        })()
        """,
    )

private fun createWebGLResultTexture(
    gl: WebGL2RenderingContext,
    width: Int,
    height: Int,
): WebGLTexture =
    js(
        """
        (() => {
            const texture = gl.createTexture();
            gl.bindTexture(gl.TEXTURE_2D, texture);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
            gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
            gl.texStorage2D(gl.TEXTURE_2D, 1, gl.RGBA32UI, width, height);
            gl.bindTexture(gl.TEXTURE_2D, null);
            return texture;
        })()
        """,
    )

private fun createWebGLFramebuffer(
    gl: WebGL2RenderingContext,
    texture: WebGLTexture,
): WebGLFramebuffer =
    js(
        """
        (() => {
            const framebuffer = gl.createFramebuffer();
            gl.bindFramebuffer(gl.FRAMEBUFFER, framebuffer);
            gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, texture, 0);
            if (gl.checkFramebufferStatus(gl.FRAMEBUFFER) !== gl.FRAMEBUFFER_COMPLETE) {
                gl.bindFramebuffer(gl.FRAMEBUFFER, null);
                gl.deleteFramebuffer(framebuffer);
                throw new Error("WebGL2 integer framebuffer is incomplete.");
            }
            return framebuffer;
        })()
        """,
    )

private fun isWebGLFramebufferComplete(gl: WebGL2RenderingContext): Boolean =
    js("gl.checkFramebufferStatus(gl.FRAMEBUFFER) === gl.FRAMEBUFFER_COMPLETE")

private fun useWebGLProgram(
    gl: WebGL2RenderingContext,
    program: WebGLProgram,
): Unit = js("gl.useProgram(program)")

private fun bindWebGLFramebuffer(
    gl: WebGL2RenderingContext,
    framebuffer: WebGLFramebuffer,
): Unit = js("gl.bindFramebuffer(gl.FRAMEBUFFER, framebuffer)")

private fun setWebGLViewport(
    gl: WebGL2RenderingContext,
    width: Int,
    height: Int,
): Unit = js("gl.viewport(0, 0, width, height)")

private fun clearWebGL(gl: WebGL2RenderingContext): Unit =
    js(
        """
        (() => {
            const zero = new Uint32Array([0, 0, 0, 0]);
            gl.clearBufferuiv(gl.COLOR, 0, zero);
        })()
        """,
    )

private fun setWebGLUIntUniform(
    gl: WebGL2RenderingContext,
    program: WebGLProgram,
    name: String,
    value: Int,
): Unit = js("gl.uniform1ui(gl.getUniformLocation(program, name), value >>> 0)")

private fun drawWebGLBatch(gl: WebGL2RenderingContext): Unit = js("gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4)")

private fun readWebGLPixels(
    gl: WebGL2RenderingContext,
    width: Int,
    height: Int,
    output: Uint32Array,
): Unit = js("gl.readPixels(0, 0, width, height, gl.RGBA_INTEGER, gl.UNSIGNED_INT, output)")

private fun deleteWebGLFramebuffer(
    gl: WebGL2RenderingContext,
    framebuffer: WebGLFramebuffer,
): Unit = js("gl.deleteFramebuffer(framebuffer)")

private fun deleteWebGLTexture(
    gl: WebGL2RenderingContext,
    texture: WebGLTexture,
): Unit = js("gl.deleteTexture(texture)")

private fun deleteWebGLProgram(
    gl: WebGL2RenderingContext,
    program: WebGLProgram,
): Unit = js("gl.deleteProgram(program)")

private fun deleteWebGLShader(
    gl: WebGL2RenderingContext,
    shader: WebGLShader,
): Unit = js("gl.deleteShader(shader)")

private fun uint32Array(size: Int): Uint32Array = js("new Uint32Array(size)")

private fun uint32ArrayGet(
    array: Uint32Array,
    index: Int,
): Int = js("array[index]")

private fun webGLVertexShaderSource(): String =
    """
    #version 300 es

    void main() {
        vec2 positions[4] = vec2[4](
            vec2(-1.0, -1.0),
            vec2(1.0, -1.0),
            vec2(-1.0, 1.0),
            vec2(1.0, 1.0)
        );
        gl_Position = vec4(positions[gl_VertexID], 0.0, 1.0);
    }
    """.trimIndent()

private fun webGLFragmentShaderSource(iterations: Int): String =
    """
    #version 300 es
    precision highp float;
    precision highp int;
    precision highp uint;

    struct U64 {
        uint low;
        uint high;
    };

    uniform uint u_thresholdLow;
    uniform uint u_thresholdHigh;
    uniform uint u_startNonceLow;
    uniform uint u_startNonceHigh;
    uniform uint u_width;
    uniform uint u_hash0;
    uniform uint u_hash1;
    uniform uint u_hash2;
    uniform uint u_hash3;
    uniform uint u_hash4;
    uniform uint u_hash5;
    uniform uint u_hash6;
    uniform uint u_hash7;

    layout(location = 0) out uvec4 outResult;

    const U64 ZERO = U64(0u, 0u);
    const U64 IV0 = U64(0xf3bcc908u, 0x6a09e667u);
    const U64 IV1 = U64(0x84caa73bu, 0xbb67ae85u);
    const U64 IV2 = U64(0xfe94f82bu, 0x3c6ef372u);
    const U64 IV3 = U64(0x5f1d36f1u, 0xa54ff53au);
    const U64 IV4 = U64(0xade682d1u, 0x510e527fu);
    const U64 IV5 = U64(0x2b3e6c1fu, 0x9b05688cu);
    const U64 IV6 = U64(0xfb41bd6bu, 0x1f83d9abu);
    const U64 IV7 = U64(0x137e2179u, 0x5be0cd19u);
    const U64 XOR_IV0 = U64(0xf2bdc900u, 0x6a09e667u);
    const U64 XOR_IV4 = U64(0xade682f9u, 0x510e527fu);
    const U64 XOR_IV6 = U64(0x04be4294u, 0xe07c2654u);

    U64 add64(U64 a, U64 b) {
        uint low = a.low + b.low;
        uint carry = low < a.low ? 1u : 0u;
        return U64(low, a.high + b.high + carry);
    }

    U64 xor64(U64 a, U64 b) {
        return U64(a.low ^ b.low, a.high ^ b.high);
    }

    U64 rotr64(U64 value, uint shift) {
        if (shift == 32u) {
            return U64(value.high, value.low);
        }
        if (shift < 32u) {
            return U64(
                (value.low >> shift) | (value.high << (32u - shift)),
                (value.high >> shift) | (value.low << (32u - shift))
            );
        }

        uint shifted = shift - 32u;
        return U64(
            (value.high >> shifted) | (value.low << (32u - shifted)),
            (value.low >> shifted) | (value.high << (32u - shifted))
        );
    }

    void g(inout U64 a, inout U64 b, inout U64 c, inout U64 d, U64 mx, U64 my) {
        a = add64(add64(a, b), mx);
        d = rotr64(xor64(d, a), 32u);
        c = add64(c, d);
        b = rotr64(xor64(b, c), 24u);
        a = add64(add64(a, b), my);
        d = rotr64(xor64(d, a), 16u);
        c = add64(c, d);
        b = rotr64(xor64(b, c), 63u);
    }

    bool lessOrEqual64(U64 value, U64 threshold) {
        return value.high < threshold.high || (value.high == threshold.high && value.low <= threshold.low);
    }

    U64 blake2b(U64 nonce) {
        U64 h0 = U64(u_hash0, u_hash1);
        U64 h1 = U64(u_hash2, u_hash3);
        U64 h2 = U64(u_hash4, u_hash5);
        U64 h3 = U64(u_hash6, u_hash7);

        U64 v0 = XOR_IV0;
        U64 v1 = IV1;
        U64 v2 = IV2;
        U64 v3 = IV3;
        U64 v4 = IV4;
        U64 v5 = IV5;
        U64 v6 = IV6;
        U64 v7 = IV7;
        U64 v8 = IV0;
        U64 v9 = IV1;
        U64 v10 = IV2;
        U64 v11 = IV3;
        U64 v12 = XOR_IV4;
        U64 v13 = IV5;
        U64 v14 = XOR_IV6;
        U64 v15 = IV7;

        g(v0, v4, v8, v12, nonce, h0);
        g(v1, v5, v9, v13, h1, h2);
        g(v2, v6, v10, v14, h3, ZERO);
        g(v3, v7, v11, v15, ZERO, ZERO);
        g(v0, v5, v10, v15, ZERO, ZERO);
        g(v1, v6, v11, v12, ZERO, ZERO);
        g(v2, v7, v8, v13, ZERO, ZERO);
        g(v3, v4, v9, v14, ZERO, ZERO);

        g(v0, v4, v8, v12, ZERO, ZERO);
        g(v1, v5, v9, v13, h3, ZERO);
        g(v2, v6, v10, v14, ZERO, ZERO);
        g(v3, v7, v11, v15, ZERO, ZERO);
        g(v0, v5, v10, v15, h0, ZERO);
        g(v1, v6, v11, v12, nonce, h1);
        g(v2, v7, v8, v13, ZERO, ZERO);
        g(v3, v4, v9, v14, ZERO, h2);

        g(v0, v4, v8, v12, ZERO, ZERO);
        g(v1, v5, v9, v13, ZERO, nonce);
        g(v2, v6, v10, v14, ZERO, h1);
        g(v3, v7, v11, v15, ZERO, ZERO);
        g(v0, v5, v10, v15, ZERO, ZERO);
        g(v1, v6, v11, v12, h2, ZERO);
        g(v2, v7, v8, v13, ZERO, h0);
        g(v3, v4, v9, v14, ZERO, h3);

        g(v0, v4, v8, v12, ZERO, ZERO);
        g(v1, v5, v9, v13, h2, h0);
        g(v2, v6, v10, v14, ZERO, ZERO);
        g(v3, v7, v11, v15, ZERO, ZERO);
        g(v0, v5, v10, v15, h1, ZERO);
        g(v1, v6, v11, v12, ZERO, ZERO);
        g(v2, v7, v8, v13, h3, nonce);
        g(v3, v4, v9, v14, ZERO, ZERO);

        g(v0, v4, v8, v12, ZERO, nonce);
        g(v1, v5, v9, v13, ZERO, ZERO);
        g(v2, v6, v10, v14, h1, h3);
        g(v3, v7, v11, v15, ZERO, ZERO);
        g(v0, v5, v10, v15, ZERO, h0);
        g(v1, v6, v11, v12, ZERO, ZERO);
        g(v2, v7, v8, v13, ZERO, ZERO);
        g(v3, v4, v9, v14, h2, ZERO);

        g(v0, v4, v8, v12, h1, ZERO);
        g(v1, v5, v9, v13, ZERO, ZERO);
        g(v2, v6, v10, v14, nonce, ZERO);
        g(v3, v7, v11, v15, ZERO, h2);
        g(v0, v5, v10, v15, h3, ZERO);
        g(v1, v6, v11, v12, ZERO, ZERO);
        g(v2, v7, v8, v13, ZERO, ZERO);
        g(v3, v4, v9, v14, h0, ZERO);

        g(v0, v4, v8, v12, ZERO, ZERO);
        g(v1, v5, v9, v13, h0, ZERO);
        g(v2, v6, v10, v14, ZERO, ZERO);
        g(v3, v7, v11, v15, h3, ZERO);
        g(v0, v5, v10, v15, nonce, ZERO);
        g(v1, v6, v11, v12, ZERO, h2);
        g(v2, v7, v8, v13, ZERO, h1);
        g(v3, v4, v9, v14, ZERO, ZERO);

        g(v0, v4, v8, v12, ZERO, ZERO);
        g(v1, v5, v9, v13, ZERO, ZERO);
        g(v2, v6, v10, v14, ZERO, h0);
        g(v3, v7, v11, v15, h2, ZERO);
        g(v0, v5, v10, v15, ZERO, nonce);
        g(v1, v6, v11, v12, ZERO, h3);
        g(v2, v7, v8, v13, ZERO, ZERO);
        g(v3, v4, v9, v14, h1, ZERO);

        g(v0, v4, v8, v12, ZERO, ZERO);
        g(v1, v5, v9, v13, ZERO, ZERO);
        g(v2, v6, v10, v14, ZERO, h2);
        g(v3, v7, v11, v15, nonce, ZERO);
        g(v0, v5, v10, v15, ZERO, h1);
        g(v1, v6, v11, v12, ZERO, ZERO);
        g(v2, v7, v8, v13, h0, h3);
        g(v3, v4, v9, v14, ZERO, ZERO);

        g(v0, v4, v8, v12, ZERO, h1);
        g(v1, v5, v9, v13, ZERO, h3);
        g(v2, v6, v10, v14, ZERO, ZERO);
        g(v3, v7, v11, v15, h0, ZERO);
        g(v0, v5, v10, v15, ZERO, ZERO);
        g(v1, v6, v11, v12, ZERO, ZERO);
        g(v2, v7, v8, v13, h2, ZERO);
        g(v3, v4, v9, v14, ZERO, nonce);

        g(v0, v4, v8, v12, nonce, h0);
        g(v1, v5, v9, v13, h1, h2);
        g(v2, v6, v10, v14, h3, ZERO);
        g(v3, v7, v11, v15, ZERO, ZERO);
        g(v0, v5, v10, v15, ZERO, ZERO);
        g(v1, v6, v11, v12, ZERO, ZERO);
        g(v2, v7, v8, v13, ZERO, ZERO);
        g(v3, v4, v9, v14, ZERO, ZERO);

        g(v0, v4, v8, v12, ZERO, ZERO);
        g(v1, v5, v9, v13, h3, ZERO);
        g(v2, v6, v10, v14, ZERO, ZERO);
        g(v3, v7, v11, v15, ZERO, ZERO);
        g(v0, v5, v10, v15, h0, ZERO);
        g(v1, v6, v11, v12, nonce, h1);
        g(v2, v7, v8, v13, ZERO, ZERO);
        g(v3, v4, v9, v14, ZERO, h2);

        return xor64(xor64(XOR_IV0, v0), v8);
    }

    void main() {
        uint invocation = uint(gl_FragCoord.x) + uint(gl_FragCoord.y) * u_width;
        U64 threshold = U64(u_thresholdLow, u_thresholdHigh);
        U64 nonce = add64(U64(u_startNonceLow, u_startNonceHigh), U64(invocation, 0u));
        U64 step = U64(u_width * uint($WEBGL_BATCH_HEIGHT), 0u);

        for (int iteration = 0; iteration < $iterations; iteration++) {
            if (lessOrEqual64(blake2b(nonce), threshold)) {
                outResult = uvec4(nonce.low, nonce.high, 1u, 0u);
                return;
            }
            nonce = add64(nonce, step);
        }

        outResult = uvec4(0u, 0u, 0u, 0u);
    }
    """.trimIndent()
