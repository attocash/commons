# commons-worker-opencl

OpenCL proof‑of‑work (PoW) implementation for JVM. It uses JOCL to run PoW on a GPU or other OpenCL device.

- JVM only
- Device selection by index (default 0)
- Same `AttoWorker` interface as CPU/remote workers

## Usage

```kotlin
// Default device (0)
val worker = AttoWorker.opencl()

// Or pick a specific device
val gpu1 = AttoWorker.opencl(1U)

// Compute work for a block
val work = worker.work(block)

worker.close()
```

Notes:
- Requires OpenCL runtime and drivers installed on the host.
- See `commons-worker-opencl/src/jvmTest` for basic tests.
- There is also a micro-benchmark in `commons-core/benchmarks` using OpenCL.
