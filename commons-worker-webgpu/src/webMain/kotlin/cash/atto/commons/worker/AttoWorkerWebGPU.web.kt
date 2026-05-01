@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.worker

import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.isValid
import cash.atto.commons.toByteArray
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

actual val AttoWorker.Companion.isWebgpuSupported: Boolean
    get() = isWebGPUSupported()

actual class AttoWorkerWebGPU actual constructor() : AttoWorker {
    private var state: WebGPUState? = null
    private var closed = false

    init {
        require(isWebGPUSupported()) { "WebGPU API not available." }
    }

    actual override suspend fun work(
        threshold: ULong,
        target: AttoWorkTarget,
    ): AttoWork {
        check(!closed) { "AttoWorkerWebGPU is closed." }
        require(target.value.size == WORK_TARGET_SIZE) {
            "WebGPU worker target must contain $WORK_TARGET_SIZE bytes but contains ${target.value.size}."
        }

        val state = state()
        val configuration = state.configuration
        val targetBytes = target.value.copyOf().toUint8Array()
        val totalInvocations = configuration.workgroups.toULong() * configuration.workgroupSize.toULong()
        val batchSize = totalInvocations * configuration.iterationsPerInvocation.toULong()
        var startNonce = 0UL

        val inputBuffer = state.createBuffer(INPUT_BUFFER_SIZE, inputBufferUsage())
        val resultBuffer = state.createBuffer(RESULT_BUFFER_SIZE, resultBufferUsage())
        val readBuffer = state.createBuffer(RESULT_BUFFER_SIZE, readBufferUsage())

        try {
            while (true) {
                currentCoroutineContext().ensureActive()

                writeBuffer(
                    state.device,
                    inputBuffer,
                    createInputData(
                        INPUT_BUFFER_SIZE,
                        threshold.lowInt(),
                        threshold.highInt(),
                        startNonce.lowInt(),
                        startNonce.highInt(),
                        configuration.iterationsPerInvocation.toInt(),
                        targetBytes,
                    ),
                )
                writeBuffer(state.device, resultBuffer, zeroedData(RESULT_BUFFER_SIZE))

                state.dispatch(inputBuffer, resultBuffer, readBuffer)
                readBuffer.mapAsync(gpuMapModeRead()).await()

                val result = readMappedResult(readBuffer, RESULT_BUFFER_SIZE)
                readBuffer.unmap()

                if (result.found != 0) {
                    val nonceLow = result.nonceLow.toUInt().toULong()
                    val nonceHigh = result.nonceHigh.toUInt().toULong()
                    val nonce = nonceLow or (nonceHigh shl 32)
                    val work = AttoWork(nonce.toByteArray())
                    check(AttoWork.isValid(threshold, target, work.value)) {
                        "WebGPU worker returned invalid work."
                    }
                    return work
                }

                startNonce += batchSize
            }
        } finally {
            inputBuffer.destroy()
            resultBuffer.destroy()
            readBuffer.destroy()
        }
    }

    actual override fun close() {
        closed = true
        state?.device?.destroy()
        state = null
    }

    private suspend fun state(): WebGPUState {
        state?.let { return it }

        val gpu = getWebGPU()
        val adapter = gpu.requestAdapter().await() ?: throw IllegalStateException("No WebGPU adapter is available.")

        val device = adapter.requestDevice().await()
        val configuration = createWorkConfiguration(device)

        val shaderModule = device.createShaderModule(shaderModuleDescriptor(shaderSource(configuration.workgroupSize.toInt())))
        val pipeline = device.createComputePipeline(computePipelineDescriptor(shaderModule))
        val created =
            WebGPUState(
                device = device,
                pipeline = pipeline,
                configuration = configuration,
            )
        state = created
        return created
    }
}

private class WebGPUState(
    val device: GPUDevice,
    private val pipeline: GPUComputePipeline,
    val configuration: WebGPUWorkConfiguration,
) {
    fun createBuffer(
        size: Int,
        usage: Int,
    ): GPUBuffer = device.createBuffer(bufferDescriptor(size, usage))

    fun dispatch(
        inputBuffer: GPUBuffer,
        resultBuffer: GPUBuffer,
        readBuffer: GPUBuffer,
    ) {
        val encoder = device.createCommandEncoder()
        val pass = encoder.beginComputePass()
        val bindGroup = device.createBindGroup(bindGroupDescriptor(pipeline.getBindGroupLayout(0), inputBuffer, resultBuffer))

        pass.setPipeline(pipeline)
        pass.setBindGroup(0, bindGroup)
        pass.dispatchWorkgroups(configuration.workgroups.toInt())
        pass.end()

        encoder.copyBufferToBuffer(resultBuffer, 0, readBuffer, 0, RESULT_BUFFER_SIZE)
        device.queue.submit(
            JsArray<GPUCommandBuffer>().also {
                it[0] = encoder.finish()
            },
        )
    }
}

private external interface GPU : JsAny {
    fun requestAdapter(): Promise<GPUAdapter?>
}

private external interface GPUAdapter : JsAny {
    fun requestDevice(): Promise<GPUDevice>
}

private external interface GPUDevice : JsAny {
    val limits: GPUSupportedLimits
    val queue: GPUQueue

    fun createBuffer(descriptor: GPUBufferDescriptor): GPUBuffer

    fun createShaderModule(descriptor: GPUShaderModuleDescriptor): GPUShaderModule

    fun createComputePipeline(descriptor: GPUComputePipelineDescriptor): GPUComputePipeline

    fun createBindGroup(descriptor: GPUBindGroupDescriptor): GPUBindGroup

    fun createCommandEncoder(): GPUCommandEncoder

    fun destroy()
}

private external interface GPUQueue : JsAny {
    fun writeBuffer(
        buffer: GPUBuffer,
        bufferOffset: Int,
        data: ArrayBuffer,
    )

    fun submit(commandBuffers: JsArray<GPUCommandBuffer>)
}

private external interface ArrayBuffer : JsAny

private external interface Uint8Array : JsAny

private external interface DataView : JsAny

private external interface GPUSupportedLimits : JsAny {
    val maxComputeInvocationsPerWorkgroup: Int
    val maxComputeWorkgroupSizeX: Int
    val maxComputeWorkgroupsPerDimension: Int
}

private external interface GPUBuffer : JsAny {
    fun mapAsync(mode: Int): Promise<JsAny?>

    fun getMappedRange(
        offset: Int,
        size: Int,
    ): ArrayBuffer

    fun unmap()

    fun destroy()
}

private external interface GPUShaderModule : JsAny

private external interface GPUComputePipeline : JsAny {
    fun getBindGroupLayout(index: Int): GPUBindGroupLayout
}

private external interface GPUBindGroupLayout : JsAny

private external interface GPUBindGroup : JsAny

private external interface GPUCommandBuffer : JsAny

private external interface GPUCommandEncoder : JsAny {
    fun beginComputePass(): GPUComputePassEncoder

    fun copyBufferToBuffer(
        source: GPUBuffer,
        sourceOffset: Int,
        destination: GPUBuffer,
        destinationOffset: Int,
        size: Int,
    )

    fun finish(): GPUCommandBuffer
}

private external interface GPUComputePassEncoder : JsAny {
    fun setPipeline(pipeline: GPUComputePipeline)

    fun setBindGroup(
        index: Int,
        bindGroup: GPUBindGroup,
    )

    fun dispatchWorkgroups(workgroupCountX: Int)

    fun end()
}

private external interface GPUBufferDescriptor : JsAny

private external interface GPUShaderModuleDescriptor : JsAny

private external interface GPUComputePipelineDescriptor : JsAny

private external interface GPUBindGroupDescriptor : JsAny

private data class WebGPUWorkResult(
    val nonceLow: Int,
    val nonceHigh: Int,
    val found: Int,
)

private data class WebGPUWorkConfiguration(
    val workgroups: UInt,
    val workgroupSize: UInt,
    val iterationsPerInvocation: UInt,
)

private const val WORK_TARGET_SIZE = 32
private const val INPUT_BUFFER_SIZE = 64
private const val RESULT_BUFFER_SIZE = 16
private const val PREFERRED_WORKGROUPS = 256
private const val PREFERRED_WORKGROUP_SIZE = 256
private const val PREFERRED_ITERATIONS_PER_INVOCATION = 1024

private fun ULong.lowInt(): Int = (this and UInt.MAX_VALUE.toULong()).toUInt().toInt()

private fun ULong.highInt(): Int = (this shr 32).toUInt().toInt()

private suspend fun <T : JsAny?> Promise<T>.await(): T =
    suspendCoroutine { continuation ->
        then(
            {
                continuation.resume(it)
                null
            },
            {
                continuation.resumeWithException(Throwable(it.toString()))
                null
            },
        )
    }

private fun ByteArray.toUint8Array(): Uint8Array {
    val output = uint8Array(size)
    repeat(size) { index ->
        setUint8(output, index, this[index])
    }
    return output
}

private fun Uint8Array.unsignedByteAt(index: Int): Int = getUint8(this, index) and 0xff

private fun createWorkConfiguration(device: GPUDevice): WebGPUWorkConfiguration {
    val maxWorkgroupSize =
        minOf(
            device.limits.maxComputeInvocationsPerWorkgroup,
            device.limits.maxComputeWorkgroupSizeX,
        )
    require(maxWorkgroupSize > 0) {
        "WebGPU device reports no compute workgroup capacity."
    }

    val maxWorkgroups = device.limits.maxComputeWorkgroupsPerDimension
    require(maxWorkgroups > 0) {
        "WebGPU device reports no compute workgroup dimension capacity."
    }

    return WebGPUWorkConfiguration(
        workgroups = minOf(PREFERRED_WORKGROUPS, maxWorkgroups).toUInt(),
        workgroupSize = minOf(PREFERRED_WORKGROUP_SIZE, maxWorkgroupSize).toUInt(),
        iterationsPerInvocation = PREFERRED_ITERATIONS_PER_INVOCATION.toUInt(),
    )
}

private fun validateWebGPUSupported() {
    if (!isWebGPUSupported()) {
        throw IllegalStateException("WebGPU API not available.")
    }
}

private fun isWebGPUSupported(): Boolean =
    js(
        """
        !!(
            globalThis.navigator &&
            globalThis.navigator.gpu &&
            typeof globalThis.navigator.gpu.requestAdapter === "function" &&
            globalThis.GPUBufferUsage &&
            globalThis.GPUMapMode
        )
        """,
    )

private fun getWebGPU(): GPU {
    validateWebGPUSupported()
    return globalWebGPU()
}

private fun globalWebGPU(): GPU = js("globalThis.navigator.gpu")

private fun inputBufferUsage(): Int = js("globalThis.GPUBufferUsage.STORAGE | globalThis.GPUBufferUsage.COPY_DST")

private fun resultBufferUsage(): Int =
    js("globalThis.GPUBufferUsage.STORAGE | globalThis.GPUBufferUsage.COPY_SRC | globalThis.GPUBufferUsage.COPY_DST")

private fun readBufferUsage(): Int = js("globalThis.GPUBufferUsage.COPY_DST | globalThis.GPUBufferUsage.MAP_READ")

private fun gpuMapModeRead(): Int = js("globalThis.GPUMapMode.READ")

private fun bufferDescriptor(
    size: Int,
    usage: Int,
): GPUBufferDescriptor = js("({ size: size, usage: usage })")

private fun shaderModuleDescriptor(code: String): GPUShaderModuleDescriptor = js("({ code: code })")

private fun computePipelineDescriptor(shaderModule: GPUShaderModule): GPUComputePipelineDescriptor =
    js("({ layout: 'auto', compute: { module: shaderModule, entryPoint: 'main' } })")

private fun bindGroupDescriptor(
    layout: GPUBindGroupLayout,
    inputBuffer: GPUBuffer,
    resultBuffer: GPUBuffer,
): GPUBindGroupDescriptor =
    js(
        """
        ({
            layout: layout,
            entries: [
                { binding: 0, resource: { buffer: inputBuffer } },
                { binding: 1, resource: { buffer: resultBuffer } }
            ]
        })
        """,
    )

private fun createInputData(
    size: Int,
    thresholdLow: Int,
    thresholdHigh: Int,
    startNonceLow: Int,
    startNonceHigh: Int,
    iterations: Int,
    target: Uint8Array,
): ArrayBuffer {
    val data = arrayBuffer(size)
    val view = dataView(data)
    setUint32(view, 0, thresholdLow, true)
    setUint32(view, 4, thresholdHigh, true)
    setUint32(view, 8, startNonceLow, true)
    setUint32(view, 12, startNonceHigh, true)

    repeat(8) { index ->
        val offset = index * 4
        val word =
            target.unsignedByteAt(offset) or
                (target.unsignedByteAt(offset + 1) shl 8) or
                (target.unsignedByteAt(offset + 2) shl 16) or
                (target.unsignedByteAt(offset + 3) shl 24)
        setUint32(view, 16 + offset, word, true)
    }

    setUint32(view, 48, iterations, true)
    return data
}

private fun zeroedData(size: Int): ArrayBuffer = arrayBuffer(size)

private fun writeBuffer(
    device: GPUDevice,
    buffer: GPUBuffer,
    data: ArrayBuffer,
) {
    device.queue.writeBuffer(buffer, 0, data)
}

private fun readMappedResult(
    buffer: GPUBuffer,
    size: Int,
): WebGPUWorkResult {
    val view = dataView(buffer.getMappedRange(0, size))
    return WebGPUWorkResult(
        nonceLow = getUint32(view, 0, true),
        nonceHigh = getUint32(view, 4, true),
        found = getUint32(view, 8, true),
    )
}

private fun arrayBuffer(size: Int): ArrayBuffer = js("new ArrayBuffer(size)")

private fun uint8Array(size: Int): Uint8Array = js("new Uint8Array(size)")

private fun dataView(buffer: ArrayBuffer): DataView = js("new DataView(buffer)")

private fun getUint8(
    array: Uint8Array,
    index: Int,
): Int = js("array[index]")

private fun setUint8(
    array: Uint8Array,
    index: Int,
    value: Byte,
): Unit = js("array[index] = value")

private fun getUint32(
    view: DataView,
    offset: Int,
    littleEndian: Boolean,
): Int = js("view.getUint32(offset, littleEndian)")

private fun setUint32(
    view: DataView,
    offset: Int,
    value: Int,
    littleEndian: Boolean,
): Unit = js("view.setUint32(offset, value, littleEndian)")

private fun shaderSource(workgroupSize: Int): String =
    """
    alias U64 = vec2<u32>;

    struct Input {
        thresholdLow: u32,
        thresholdHigh: u32,
        startNonceLow: u32,
        startNonceHigh: u32,
        hash: array<u32, 8>,
        iterations: u32,
    }

    struct Result {
        nonceLow: u32,
        nonceHigh: u32,
        found: atomic<u32>,
    }

    @group(0) @binding(0) var<storage, read> input: Input;
    @group(0) @binding(1) var<storage, read_write> result: Result;

    const ZERO: U64 = vec2<u32>(0u, 0u);
    const IV0: U64 = vec2<u32>(0xf3bcc908u, 0x6a09e667u);
    const IV1: U64 = vec2<u32>(0x84caa73bu, 0xbb67ae85u);
    const IV2: U64 = vec2<u32>(0xfe94f82bu, 0x3c6ef372u);
    const IV3: U64 = vec2<u32>(0x5f1d36f1u, 0xa54ff53au);
    const IV4: U64 = vec2<u32>(0xade682d1u, 0x510e527fu);
    const IV5: U64 = vec2<u32>(0x2b3e6c1fu, 0x9b05688cu);
    const IV6: U64 = vec2<u32>(0xfb41bd6bu, 0x1f83d9abu);
    const IV7: U64 = vec2<u32>(0x137e2179u, 0x5be0cd19u);
    const XOR_IV0: U64 = vec2<u32>(0xf2bdc900u, 0x6a09e667u);
    const XOR_IV4: U64 = vec2<u32>(0xade682f9u, 0x510e527fu);
    const XOR_IV6: U64 = vec2<u32>(0x04be4294u, 0xe07c2654u);

    fn add64(a: U64, b: U64) -> U64 {
        let low = a.x + b.x;
        let carry = select(0u, 1u, low < a.x);
        return vec2<u32>(low, a.y + b.y + carry);
    }

    fn xor64(a: U64, b: U64) -> U64 {
        return vec2<u32>(a.x ^ b.x, a.y ^ b.y);
    }

    fn rotr64(value: U64, shift: u32) -> U64 {
        if (shift == 32u) {
            return vec2<u32>(value.y, value.x);
        }
        if (shift < 32u) {
            return vec2<u32>(
                (value.x >> shift) | (value.y << (32u - shift)),
                (value.y >> shift) | (value.x << (32u - shift))
            );
        }

        let shifted = shift - 32u;
        return vec2<u32>(
            (value.y >> shifted) | (value.x << (32u - shifted)),
            (value.x >> shifted) | (value.y << (32u - shifted))
        );
    }

    fn g(
        a: ptr<function, U64>,
        b: ptr<function, U64>,
        c: ptr<function, U64>,
        d: ptr<function, U64>,
        mx: U64,
        my: U64
    ) {
        *a = add64(add64(*a, *b), mx);
        *d = rotr64(xor64(*d, *a), 32u);
        *c = add64(*c, *d);
        *b = rotr64(xor64(*b, *c), 24u);
        *a = add64(add64(*a, *b), my);
        *d = rotr64(xor64(*d, *a), 16u);
        *c = add64(*c, *d);
        *b = rotr64(xor64(*b, *c), 63u);
    }

    fn lessOrEqual64(value: U64, threshold: U64) -> bool {
        return value.y < threshold.y || (value.y == threshold.y && value.x <= threshold.x);
    }

    fn blake2b(nonce: U64) -> U64 {
        let h0 = vec2<u32>(input.hash[0], input.hash[1]);
        let h1 = vec2<u32>(input.hash[2], input.hash[3]);
        let h2 = vec2<u32>(input.hash[4], input.hash[5]);
        let h3 = vec2<u32>(input.hash[6], input.hash[7]);

        var v0 = XOR_IV0;
        var v1 = IV1;
        var v2 = IV2;
        var v3 = IV3;
        var v4 = IV4;
        var v5 = IV5;
        var v6 = IV6;
        var v7 = IV7;
        var v8 = IV0;
        var v9 = IV1;
        var v10 = IV2;
        var v11 = IV3;
        var v12 = XOR_IV4;
        var v13 = IV5;
        var v14 = XOR_IV6;
        var v15 = IV7;

        g(&v0, &v4, &v8, &v12, nonce, h0);
        g(&v1, &v5, &v9, &v13, h1, h2);
        g(&v2, &v6, &v10, &v14, h3, ZERO);
        g(&v3, &v7, &v11, &v15, ZERO, ZERO);
        g(&v0, &v5, &v10, &v15, ZERO, ZERO);
        g(&v1, &v6, &v11, &v12, ZERO, ZERO);
        g(&v2, &v7, &v8, &v13, ZERO, ZERO);
        g(&v3, &v4, &v9, &v14, ZERO, ZERO);

        g(&v0, &v4, &v8, &v12, ZERO, ZERO);
        g(&v1, &v5, &v9, &v13, h3, ZERO);
        g(&v2, &v6, &v10, &v14, ZERO, ZERO);
        g(&v3, &v7, &v11, &v15, ZERO, ZERO);
        g(&v0, &v5, &v10, &v15, h0, ZERO);
        g(&v1, &v6, &v11, &v12, nonce, h1);
        g(&v2, &v7, &v8, &v13, ZERO, ZERO);
        g(&v3, &v4, &v9, &v14, ZERO, h2);

        g(&v0, &v4, &v8, &v12, ZERO, ZERO);
        g(&v1, &v5, &v9, &v13, ZERO, nonce);
        g(&v2, &v6, &v10, &v14, ZERO, h1);
        g(&v3, &v7, &v11, &v15, ZERO, ZERO);
        g(&v0, &v5, &v10, &v15, ZERO, ZERO);
        g(&v1, &v6, &v11, &v12, h2, ZERO);
        g(&v2, &v7, &v8, &v13, ZERO, h0);
        g(&v3, &v4, &v9, &v14, ZERO, h3);

        g(&v0, &v4, &v8, &v12, ZERO, ZERO);
        g(&v1, &v5, &v9, &v13, h2, h0);
        g(&v2, &v6, &v10, &v14, ZERO, ZERO);
        g(&v3, &v7, &v11, &v15, ZERO, ZERO);
        g(&v0, &v5, &v10, &v15, h1, ZERO);
        g(&v1, &v6, &v11, &v12, ZERO, ZERO);
        g(&v2, &v7, &v8, &v13, h3, nonce);
        g(&v3, &v4, &v9, &v14, ZERO, ZERO);

        g(&v0, &v4, &v8, &v12, ZERO, nonce);
        g(&v1, &v5, &v9, &v13, ZERO, ZERO);
        g(&v2, &v6, &v10, &v14, h1, h3);
        g(&v3, &v7, &v11, &v15, ZERO, ZERO);
        g(&v0, &v5, &v10, &v15, ZERO, h0);
        g(&v1, &v6, &v11, &v12, ZERO, ZERO);
        g(&v2, &v7, &v8, &v13, ZERO, ZERO);
        g(&v3, &v4, &v9, &v14, h2, ZERO);

        g(&v0, &v4, &v8, &v12, h1, ZERO);
        g(&v1, &v5, &v9, &v13, ZERO, ZERO);
        g(&v2, &v6, &v10, &v14, nonce, ZERO);
        g(&v3, &v7, &v11, &v15, ZERO, h2);
        g(&v0, &v5, &v10, &v15, h3, ZERO);
        g(&v1, &v6, &v11, &v12, ZERO, ZERO);
        g(&v2, &v7, &v8, &v13, ZERO, ZERO);
        g(&v3, &v4, &v9, &v14, h0, ZERO);

        g(&v0, &v4, &v8, &v12, ZERO, ZERO);
        g(&v1, &v5, &v9, &v13, h0, ZERO);
        g(&v2, &v6, &v10, &v14, ZERO, ZERO);
        g(&v3, &v7, &v11, &v15, h3, ZERO);
        g(&v0, &v5, &v10, &v15, nonce, ZERO);
        g(&v1, &v6, &v11, &v12, ZERO, h2);
        g(&v2, &v7, &v8, &v13, ZERO, h1);
        g(&v3, &v4, &v9, &v14, ZERO, ZERO);

        g(&v0, &v4, &v8, &v12, ZERO, ZERO);
        g(&v1, &v5, &v9, &v13, ZERO, ZERO);
        g(&v2, &v6, &v10, &v14, ZERO, h0);
        g(&v3, &v7, &v11, &v15, h2, ZERO);
        g(&v0, &v5, &v10, &v15, ZERO, nonce);
        g(&v1, &v6, &v11, &v12, ZERO, h3);
        g(&v2, &v7, &v8, &v13, ZERO, ZERO);
        g(&v3, &v4, &v9, &v14, h1, ZERO);

        g(&v0, &v4, &v8, &v12, ZERO, ZERO);
        g(&v1, &v5, &v9, &v13, ZERO, ZERO);
        g(&v2, &v6, &v10, &v14, ZERO, h2);
        g(&v3, &v7, &v11, &v15, nonce, ZERO);
        g(&v0, &v5, &v10, &v15, ZERO, h1);
        g(&v1, &v6, &v11, &v12, ZERO, ZERO);
        g(&v2, &v7, &v8, &v13, h0, h3);
        g(&v3, &v4, &v9, &v14, ZERO, ZERO);

        g(&v0, &v4, &v8, &v12, ZERO, h1);
        g(&v1, &v5, &v9, &v13, ZERO, h3);
        g(&v2, &v6, &v10, &v14, ZERO, ZERO);
        g(&v3, &v7, &v11, &v15, h0, ZERO);
        g(&v0, &v5, &v10, &v15, ZERO, ZERO);
        g(&v1, &v6, &v11, &v12, ZERO, ZERO);
        g(&v2, &v7, &v8, &v13, h2, ZERO);
        g(&v3, &v4, &v9, &v14, ZERO, nonce);

        g(&v0, &v4, &v8, &v12, nonce, h0);
        g(&v1, &v5, &v9, &v13, h1, h2);
        g(&v2, &v6, &v10, &v14, h3, ZERO);
        g(&v3, &v7, &v11, &v15, ZERO, ZERO);
        g(&v0, &v5, &v10, &v15, ZERO, ZERO);
        g(&v1, &v6, &v11, &v12, ZERO, ZERO);
        g(&v2, &v7, &v8, &v13, ZERO, ZERO);
        g(&v3, &v4, &v9, &v14, ZERO, ZERO);

        g(&v0, &v4, &v8, &v12, ZERO, ZERO);
        g(&v1, &v5, &v9, &v13, h3, ZERO);
        g(&v2, &v6, &v10, &v14, ZERO, ZERO);
        g(&v3, &v7, &v11, &v15, ZERO, ZERO);
        g(&v0, &v5, &v10, &v15, h0, ZERO);
        g(&v1, &v6, &v11, &v12, nonce, h1);
        g(&v2, &v7, &v8, &v13, ZERO, ZERO);
        g(&v3, &v4, &v9, &v14, ZERO, h2);

        return xor64(xor64(XOR_IV0, v0), v8);
    }

    @compute @workgroup_size($workgroupSize)
    fn main(
        @builtin(global_invocation_id) globalId: vec3<u32>,
        @builtin(num_workgroups) numWorkgroups: vec3<u32>
    ) {
        let threshold = vec2<u32>(input.thresholdLow, input.thresholdHigh);
        let startNonce = vec2<u32>(input.startNonceLow, input.startNonceHigh);
        let globalSize = numWorkgroups.x * ${workgroupSize}u;
        let step = vec2<u32>(globalSize, 0u);
        var nonce = add64(startNonce, vec2<u32>(globalId.x, 0u));
        var iteration = 0u;

        loop {
            if (iteration >= input.iterations || atomicLoad(&result.found) != 0u) {
                break;
            }

            if (lessOrEqual64(blake2b(nonce), threshold)) {
                loop {
                    let exchange = atomicCompareExchangeWeak(&result.found, 0u, 1u);
                    if (exchange.exchanged) {
                        result.nonceLow = nonce.x;
                        result.nonceHigh = nonce.y;
                    }
                    if (exchange.exchanged || exchange.old_value != 0u) {
                        break;
                    }
                }
                break;
            }

            nonce = add64(nonce, step);
            iteration = iteration + 1u;
        }
    }
    """.trimIndent()
