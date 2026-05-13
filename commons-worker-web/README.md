# commons-worker-web

Web proof-of-work (PoW) implementations for browser JavaScript and Wasm. WebGPU uses a WGSL compute shader; WebGL uses a WebGL2 fragment shader over nonce batches. Both return the same `AttoWorker` interface as CPU/remote workers.

- JavaScript browser and Wasm browser targets
- WebGPU requires WebGPU support in a secure context
- WebGL requires WebGL2 with integer render target support
- Batches dispatches to avoid long single-shader runs
- Same validation contract as the CPU worker

## Usage

```kotlin
if (!AttoWorker.isWebSupported()) {
    return
}

val worker = AttoWorker.web()
val work = worker.work(block)

worker.close()
```

Notes:
- `AttoWorker.web()` selects WebGPU when available, then falls back to WebGL.
- `AttoWorker.isWebSupported()` checks whether either web worker backend is available.
- Browser WebGPU availability depends on browser version, platform, GPU driver, and secure-context rules.
- `AttoWorker.isWebgpuSupported()` checks the WebGPU API surface and asynchronous adapter discovery.
- `AttoWorker.isWebglSupported()` checks the WebGL2 API surface needed by the fragment-shader worker.
- Browser tests skip the actual GPU work when the required GPU API is not exposed by the test browser.
- Browser tests use the shared root Puppeteer-backed Karma launcher. Kotlin/JS installs npm packages with lifecycle scripts ignored, so `installPuppeteerBrowsers` runs Puppeteer's browser installer explicitly before browser tests.
- Kotlinx benchmark is not configured for this module because its Kotlin/JS runner requires `nodejs()`, while this worker is browser-only.
