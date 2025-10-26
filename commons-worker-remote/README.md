# commons-worker-remote

Remote HTTP worker implementation of `AttoWorker`. Useful to offload PoW to a separate service.

Highlights:
- `AttoWorker.remote(url, headerProvider)` convenience
- Same `AttoWorker` API as CPU/OpenCL: `work(block)` and `work(network, timestamp, target)`
- Low-level request API via `AttoWorkerOperations.Request`

## Quick start

```kotlin
// Optional headers (JWT/API keys)
suspend fun headers(): Map<String, String> = mapOf("Authorization" to "Bearer <jwt>")

val worker = AttoWorker.remote("http://localhost:8085", ::headers)

// Compute work for a block
val work = worker.work(block)
```

## Low-level API

```kotlin
val request = AttoWorkerOperations.Request(
  network = block.network,
  timestamp = block.timestamp,
  target = block.getTarget().value.toHex(),
)

val response = (worker as AttoWorkerOperations).work(request)
val work = response.work
```

Implementation details: see `AttoWorkerRemote.kt`. The client uses Ktor and posts to `/works` with JSON, expecting a JSON response.
