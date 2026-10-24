# commons-test

Run isolated Atto integration tests against containerized services. The node mock runs a real Atto node with MySQL; the worker mock runs a real Work Server. They expose mapped HTTP URLs for the normal Commons clients. These are local test networks, not in-memory substitutes or access to live-network funds.

## Install and prerequisites

For Kotlin/JVM tests:

```kotlin
testImplementation("cash.atto:commons-test:<version>")
```

For Node.js tests:

```sh
npm install --save-dev @attocash/commons-test
npm install @attocash/commons-core
```

Use matching Commons versions. JavaScript packages are ESM and include TypeScript declarations; use `.mjs` or `"type": "module"`. Use Node.js 24, matching this repository's JavaScript toolchain.

Docker or a compatible Podman API must be running and accessible to Testcontainers. The process needs permission to create containers and networks and to fetch missing images. Installing Podman alone does not expose its API socket to Testcontainers; configure the runtime connection for your environment before starting tests. These services run in Node.js or on the JVM, not in a browser.

The default images are:

| Service | Image |
| --- | --- |
| Node | `ghcr.io/attocash/node:live` |
| Database | `mysql:8.4` |
| Work Server | `ghcr.io/attocash/work-server:cpu` |

Image tags can change. Override the images with compatible pinned tags or digests when reproducibility matters, and ensure they support the container host's architecture.

## Start and stop local services

Save this as `services.mjs` and run `node services.mjs`. The builders prepare mocks and generate a signed local genesis transaction; `start()` launches the containers. Read `baseUrl` only after startup completes.

```js
import {createRequire} from 'node:module'
import {AttoMnemonic, toAttoIndex} from '@attocash/commons-core'
import {AttoNodeMockAsyncBuilder, AttoWorkerMockAsyncBuilder} from '@attocash/commons-test'

globalThis.require = createRequire(import.meta.url)

const seed = await (await AttoMnemonic.generate()).toSeedAsync()
const genesisPrivateKey = await seed.toPrivateKey(toAttoIndex(0))
const node = await new AttoNodeMockAsyncBuilder(genesisPrivateKey).build()
const worker = await new AttoWorkerMockAsyncBuilder().build()

try {
    await node.start()
    await worker.start()
    console.log(`Node: ${node.baseUrl}`)
    console.log(`Worker: ${worker.baseUrl}`)
    // Run client assertions here, while both services are available.
} finally {
    try {
        await worker.stop()
    } finally {
        await node.stop()
    }
}
```

The generated genesis account belongs to the supplied private key and holds the local network's initial supply. Other newly generated accounts have no balance. See the [JavaScript wallet example](../examples/js-client/README.md) for transfers from the genesis account, and the [Java example](../examples/java-client/README.md) for JVM builders.

Always await `stop()` in `finally` or a test teardown hook. Close client subscriptions and wallet jobs before stopping their services. In JavaScript, `close()` schedules cleanup but does not wait for it; it is a compatibility fallback, not a completed teardown boundary. On the JVM, `close()` performs synchronous cleanup. Use a fresh mock instance for each test lifecycle when sharing code across runtimes.

## Configure services

Builders expose names, images, logging, and node database/genesis settings:

```js
const node = await new AttoNodeMockAsyncBuilder(genesisPrivateKey)
    .name('integration-node')
    .image('ghcr.io/attocash/node:live')
    .mysqlImage('mysql:8.4')
    .logOutput(false)
    .build()

const worker = await new AttoWorkerMockAsyncBuilder()
    .name('integration-worker')
    .image('ghcr.io/attocash/work-server:cpu')
    .logOutput(false)
    .build()
```

Node options also include `dbName`, `dbUser`, `dbPassword`, `genesis`, and `pullImages`; the worker exposes `pullImage`. The current JavaScript implementation configures the MySQL root password, so keep `dbUser('root')` when changing database settings. The pull flags force fresh image pulls on the JVM; the JavaScript implementation currently uses Testcontainers' default pull behavior. Container output is disabled by default; enabling it can copy test keys into logs.

## Further reading

- [Node mock configuration and genesis construction](src/commonMain/kotlin/cash/atto/commons/node/AttoNodeMock.kt).
- [JVM lifecycle tests](src/jvmTest/kotlin/cash/atto/commons/node/AttoMockJvmApiTest.kt) and [JavaScript lifecycle tests](src/webTest/kotlin/cash/atto/commons/node/TestcontainersRuntimeTest.kt).
- [Node client](../commons-node-remote/README.md), [Work Server client](../commons-worker-remote/README.md), and [wallet](../commons-wallet/README.md).
- [JavaScript package migration](../MIGRATION.md#javascript-packages), [repository overview](../README.md), and [BSD 3-Clause license](../LICENSE).
