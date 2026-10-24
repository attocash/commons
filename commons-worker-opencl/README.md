# commons-worker-opencl

Generate proof of work on an OpenCL device from a Kotlin/JVM application. This backend implements the [`AttoWorker` API](../commons-worker/README.md) using JOCL and the bundled OpenCL kernel.

## Prepare the host and dependency

You need an installed OpenCL runtime, a compatible device driver, and JOCL native-library support for the host OS and architecture. The runtime must support the OpenCL calls used by this backend, including `clCreateCommandQueueWithProperties`. Adding the Maven dependency does not install a host driver or OpenCL runtime.

Add this dependency to your JVM source set; it includes `commons-worker` and JOCL transitively:

```kotlin
implementation("cash.atto:commons-worker-opencl:<version>")
```

This module has only a JVM target and no npm package. For Kotlin browser applications use [WebGPU/WebGL](../commons-worker-web/README.md); for JavaScript clients use a [remote worker](../commons-worker-remote/README.md). See the [root README](../README.md) for shared setup.

## Calculate work

This complete JVM entry point selects device zero and computes work for a local-network test target:

```kotlin
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.opencl

suspend fun main() {
    val network = AttoNetwork.LOCAL
    val timestamp = AttoNetwork.INITIAL_INSTANT
    val target = AttoWorkTarget(ByteArray(32))
    val worker = AttoWorker.opencl()
    try {
        val work = worker.work(network, timestamp, target)
        check(AttoWork.isValid(network, timestamp, target, work.value))
        println(work)
    } finally {
        worker.close()
    }
}
```

For a real block, use `worker.work(block)`.

## Select a device and manage lifetime

`AttoWorker.opencl(deviceNumber = 1U)` selects the second device on the **first OpenCL platform**. Device indices are zero-based `UByte` values. The implementation enumerates all device types on that platform, so the selected device is not necessarily a GPU. An out-of-range index throws `IllegalArgumentException`; there is no CPU fallback.

Keep one request active per worker instance: it shares a native command queue and kernel. Although `work` is a suspending API, its OpenCL calls block the calling thread while the kernel runs. Close the worker after its requests finish to release the kernel, program, queue, and context.

## Source and verification

See the [JVM implementation](src/jvmMain/kotlin/cash/atto/commons/worker/AttoWorkerOpenCL.jvm.kt), [kernel](src/jvmMain/resources/kernels/work.cl), [OpenCL test](src/jvmTest/kotlin/cash/atto/commons/worker/AttoWorkerOpenclTest.kt), and [JVM benchmark](src/jvmBenchmark/kotlin/cash/atto/commons/AttoWorkerOpenClBenchmark.kt). Tests and benchmarks require a working host OpenCL installation; a successful JVM compilation alone does not verify device execution.
