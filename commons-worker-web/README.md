# commons-worker-web

Generate proof of work in a Kotlin browser application using WebGPU compute shaders or WebGL2 fragment shaders. Both backends implement the [`AttoWorker` API](../commons-worker/README.md) and validate the work they return.

## Check browser support and add the dependency

This module targets Kotlin/JS browser and Kotlin/Wasm browser. It has no JVM or Node.js target.

- WebGPU needs a secure context, an available adapter/device, and suitable compute limits.
- WebGL needs WebGL2, integer framebuffer support, and a maximum texture size of at least 256 × 256.
- Browser, platform, GPU driver, and browser settings determine whether these features are available. Check support at runtime.

Add this dependency to your browser source set; it includes `commons-worker` transitively:

```kotlin
implementation("cash.atto:commons-worker-web:<version>")
```

The generated npm package `@attocash/commons-worker-web` currently exports no public runtime API. Installing it does not provide a JavaScript worker constructor. The example below is for Kotlin browser source; JavaScript clients can use the [remote worker package](../commons-worker-remote/README.md). See [JavaScript package migration](../MIGRATION.md#javascript-packages) and the [root README](../README.md).

## Calculate work in Kotlin browser code

This complete browser entry point checks support, computes work for a local-network test target, and closes the worker:

```kotlin
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.isWebSupported
import cash.atto.commons.worker.web

suspend fun main() {
    if (!AttoWorker.isWebSupported()) {
        println("No supported browser GPU worker available")
        return
    }

    val network = AttoNetwork.LOCAL
    val timestamp = AttoNetwork.INITIAL_INSTANT
    val target = AttoWorkTarget(ByteArray(32))
    val worker = AttoWorker.web()
    try {
        val work = worker.work(network, timestamp, target)
        check(AttoWork.isValid(network, timestamp, target, work.value))
        println(work)
    } finally {
        worker.close()
    }
}
```

For a real block, call `worker.work(block)`.

## Select a backend and manage lifetime

`AttoWorker.web()` checks WebGPU first and selects WebGL when WebGPU is unavailable. It throws if neither is supported. This selection does not retry a later shader or device failure on the other backend.

To choose explicitly, import `webgpu` or `webgl` from `cash.atto.commons.worker` and call `AttoWorker.webgpu()` or `AttoWorker.webgl()`. The corresponding checks are `isWebgpuSupported()` (suspending) and `isWebglSupported()`.

Work runs in batches and observes coroutine cancellation between batches. Reuse the worker while needed and call `close()` after requests finish. Closed workers reject further work; WebGL close also releases its framebuffer, texture, and program.

## Source and browser tests

See the [factory and capability checks](src/commonMain/kotlin/cash/atto/commons/worker/AttoWorkerWeb.kt), [WebGPU implementation](src/webMain/kotlin/cash/atto/commons/worker/AttoWorkerWebGPU.web.kt), [WebGL implementation](src/webMain/kotlin/cash/atto/commons/worker/AttoWorkerWebGL.web.kt), and [browser tests](src/webTest/kotlin/cash/atto/commons/worker).

The tests skip device work when the browser lacks the required API, so a passing suite can include no GPU execution.
Run them on a browser and GPU that expose the backend you intend to use.
