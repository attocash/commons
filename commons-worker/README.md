# commons-worker

CPU proof‑of‑work (PoW) implementation. Multiplatform (JVM/JS/Wasm). Useful for local development, tests, and environments without OpenCL.

Features:
- `AttoWorker.cpu()` factory (platform default parallelism)
- `AttoWorker.cpu(parallelism: UShort)` to tune threads on JVM/JS
- High‑level helpers: `work(block)` and `work(network, timestamp, target)`

## Basic usage

```kotlin
val worker = AttoWorker.cpu()

// Work for a block (derives threshold and target internally)
val work = worker.work(block)

// Or explicitly with threshold/target
val threshold = AttoWork.getThreshold(block.network, block.timestamp)
val target = block.getTarget()
val work2 = worker.work(threshold, target)

worker.close()
```

## Tune parallelism (JVM)

```kotlin
val worker = AttoWorker.cpu(parallelism = 8U)
```

See tests at `commons-worker/src/commonTest` and integration usages in `commons-core` tests.
