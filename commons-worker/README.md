# commons-worker

Generate proof of work locally on the CPU. This module provides `AttoWorker`, its CPU factory, and worker decorators for Kotlin/JVM, Kotlin/JS (browser and Node.js), and Kotlin/Wasm (browser and Node.js).

[`commons-core`](../commons-core/README.md) provides the PoW values and validation (`AttoWork`, `AttoWorkTarget`, thresholds, and block targets). Add this module when you also need to calculate work.

## Add the dependency

In the Gradle source set that uses the worker:

```kotlin
implementation("cash.atto:commons-worker:<version>")
```

Core types and coroutines are transitive API dependencies. Use matching versions of Commons modules; see the [root README](../README.md) for setup.

The npm package `@attocash/commons-worker` exposes `AttoWorkerAsync` for use by other Commons packages, but has no supported JavaScript CPU-worker constructor. The Kotlin `AttoWorker.cpu()` factory below is not an npm API. Use the [remote worker](../commons-worker-remote/README.md) from JavaScript; see [JavaScript package migration](../MIGRATION.md#javascript-packages).

## Calculate and validate work

This complete Kotlin entry point uses a local-network test target:

```kotlin
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.worker.AttoWorker

suspend fun main() {
    val network = AttoNetwork.LOCAL
    val timestamp = AttoNetwork.INITIAL_INSTANT
    val target = AttoWorkTarget(ByteArray(32))
    val worker = AttoWorker.cpu()
    try {
        val work = worker.work(network, timestamp, target)
        check(AttoWork.isValid(network, timestamp, target, work.value))
        println(work)
    } finally {
        worker.close()
    }
}
```

For a real block, call `worker.work(block)`; it derives the target and threshold from that block. The low-level overload `worker.work(threshold, target)` accepts an explicit `ULong` threshold.

## Choose parallelism and manage lifetime

`AttoWorker.cpu()` uses the number of available processors on JVM and one coroutine on JS/Wasm. Use `AttoWorker.cpu(parallelism = 8U)` to request a positive number of worker coroutines. On JS/Wasm this does not create browser Web Workers or Node.js worker threads.

Reuse a worker across requests and close it when its owner shuts down. CPU work cooperates with caller cancellation; `close()` cancels its worker jobs.

The module also provides these Kotlin extensions (import them from `cash.atto.commons.worker`):

- `cached()` shares work for a target and precomputes the next target after `work(block)`.
- `retry(every)` retries failures until success or caller cancellation.
- `evaluate(network, maxDuration)` returns a flow of estimated works per second.
- `toAsync()` adapts work to JVM `CompletableFuture` or JavaScript `Promise` methods. The wrapper has no `close()` method; close its underlying Kotlin worker.

## Other backends and checks

Use [OpenCL](../commons-worker-opencl/README.md) for a JVM OpenCL device, [WebGPU/WebGL](../commons-worker-web/README.md) for a Kotlin browser application, or [HTTP](../commons-worker-remote/README.md) to offload work.

The [worker API](src/commonMain/kotlin/cash/atto/commons/worker/AttoWorker.kt), [CPU implementation](src/commonMain/kotlin/cash/atto/commons/worker/AttoWorkerCpu.kt), and [CPU tests](src/commonTest/kotlin/cash/atto/commons/worker/AttoWorkerCpuTest.kt) show the overloads, validation, and cancellation behavior. See the [JVM CPU benchmark](src/jvmBenchmark/kotlin/cash/atto/commons/AttoWorkerCpuBenchmark.kt) for performance measurement.
