# commons-worker-remote

Offload proof of work to an HTTP work server. This client implements the [`AttoWorker` API](../commons-worker/README.md), sends network-aware requests to `POST /works`, and validates returned work locally.

## Prepare the server and dependency

You need a running work server that accepts the [`AttoWorkerOperations` request and response](../commons-worker/src/commonMain/kotlin/cash/atto/commons/worker/AttoWorkerOperations.kt). Supply its base URL, not the `/works` endpoint. This module provides the client, not the server. For local test infrastructure, see [`commons-test`](../commons-test/README.md).

Kotlin/JVM, Kotlin/JS (browser and Node.js), and Kotlin/Wasm (browser and Node.js) targets are configured. Browser requests also need the server's CORS policy to allow the application origin and headers; HTTPS pages need an endpoint permitted by browser mixed-content rules.

Gradle dependency (core and worker types are transitive):

```kotlin
implementation("cash.atto:commons-worker-remote:<version>")
```

For JavaScript, use matching versions of the split packages:

```sh
npm install @attocash/commons-core @attocash/commons-worker @attocash/commons-worker-remote
```

See the [root README](../README.md) for shared setup and [JavaScript package migration](../MIGRATION.md#javascript-packages) for the package layout.

## Calculate work from Kotlin

Start a compatible work server at `http://localhost:8085` before running this complete entry point:

```kotlin
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.remote
import kotlin.time.Duration.Companion.minutes

suspend fun main() {
    val network = AttoNetwork.LOCAL
    val timestamp = AttoNetwork.INITIAL_INSTANT
    val target = AttoWorkTarget(ByteArray(32))
    val worker = AttoWorker.remote(
        url = "http://localhost:8085",
        timeout = 5.minutes,
    )
    try {
        val work = worker.work(network, timestamp, target)
        check(AttoWork.isValid(network, timestamp, target, work.value))
        println(work)
    } finally {
        worker.close()
    }
}
```

Use `worker.work(block)` for an existing block. The explicit `work(threshold, target)` overload is **not implemented by this backend** and throws `NotImplementedError`; the server needs the network and timestamp.

For authentication, pass `headerProvider = { mapOf("Authorization" to "Bearer <token>") }` to `remote`. The suspending provider runs for each request, so it can refresh credentials. `timeout` defaults to five minutes and configures the Ktor socket timeout, rather than a total request deadline. The remote worker uses a shared HTTP client; its own `close()` is currently a no-op.

## Use the JavaScript async builder

The builder exports Promise methods `workBlock`, `workNetwork`, and `workThreshold`; only the first two are supported by the remote backend. Prefer `header(name, value)` and `timeoutSeconds(bigint)` from JavaScript, since Kotlin maps and durations have generated interop types.

This TypeScript function accepts an existing Commons block and requests work for it:

```typescript
import type { AttoBlock } from "@attocash/commons-core";
import { AttoWorkerAsyncBuilder } from "@attocash/commons-worker-remote";

export async function workFor(block: AttoBlock) {
  const worker = new AttoWorkerAsyncBuilder("http://localhost:8085")
    .timeoutSeconds(30n)
    .cached(false)
    .build();
  return await worker.workBlock(block);
}
```

The builder defaults to caching enabled. `cached(false)` above avoids the next-target precomputation that follows `workBlock`. The async wrapper has no public `close()` method; Kotlin callers can close `asyncWorker.worker`.

On JVM, the same builder returns `CompletableFuture` methods and accepts a coroutine dispatcher or executor in `build`. Its `retryEvery` settings apply on JVM. The JS/Wasm builder currently stores those settings without applying a retry decorator; add retries in the calling code when needed.

## Low-level requests and source

For protocol integration, cast the result of `AttoWorker.remote(...)` to `AttoWorkerOperations` and pass a `Request(network, timestamp, target.value.toHex())` to `work(request)`. This is a partial API recipe; import `AttoWorkerOperations` and the core `toHex` extension. The response contains an `AttoWork`, and invalid work is rejected.

See the [remote client](src/commonMain/kotlin/cash/atto/commons/worker/AttoWorkerRemote.kt), [JVM builder](src/jvmMain/kotlin/cash/atto/commons/worker/AttoWorkerAsyncBuilder.jvm.kt), [JS/Wasm builder](src/webMain/kotlin/cash/atto/commons/worker/AttoWorkerAsyncBuilder.web.kt), and [shared transport](../commons-transport/README.md).
