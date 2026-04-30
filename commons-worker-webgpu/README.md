# commons-worker-webgpu

WebGPU proof-of-work (PoW) implementation for browser JavaScript and Wasm. It uses a WGSL compute shader to search nonce batches on the GPU and returns the same `AttoWorker` interface as CPU/remote workers.

- JavaScript browser and Wasm browser targets
- Requires WebGPU support in a secure context
- Batches dispatches to avoid long single-shader runs
- Same validation contract as the CPU worker

## Usage

```kotlin
if (!AttoWorker.isWebgpuSupported) {
    return
}

val worker = AttoWorker.webgpu()
val work = worker.work(block)

worker.close()
```

Notes:
- This module targets WebGPU, not WebGL. WebGPU exposes compute shaders and storage buffers; WebGL does not provide a good fit for this 64-bit PoW loop.
- Browser WebGPU availability depends on browser version, platform, GPU driver, and secure-context rules.
- `AttoWorker.isWebgpuSupported` checks the synchronous WebGPU API surface. Adapter discovery is asynchronous and still happens when work starts.
- Browser tests skip the actual GPU work when WebGPU is not exposed by the test browser.
- Browser tests use the shared root Puppeteer-backed Karma launcher. Kotlin/JS installs npm packages with lifecycle scripts ignored, so `installPuppeteerBrowsers` runs Puppeteer's browser installer explicitly before browser tests.
- Kotlinx benchmark is not configured for this module because its Kotlin/JS runner requires `nodejs()`, while this worker is browser-only.
