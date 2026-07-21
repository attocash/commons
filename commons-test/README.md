# @attocash/commons-test

[![npm version](https://img.shields.io/npm/v/@attocash/commons-test.svg)](https://www.npmjs.com/package/@attocash/commons-test)
[![license](https://img.shields.io/github/license/attocash/commons.svg)](https://github.com/attocash/commons/blob/main/LICENSE)

Test utilities for Atto JavaScript and TypeScript integrations.

`@attocash/commons-test` provides local mock services for applications that use the individual Atto Commons JavaScript
packages. It starts a real Atto node container with a MySQL container and a real Work Server container, then exposes
their mapped URLs so your tests can use the normal node, worker, wallet, and monitor clients.

## Install

```sh
npm install --save-dev @attocash/commons-test
npm install @attocash/commons-core @attocash/commons-node @attocash/commons-node-remote
npm install @attocash/commons-worker @attocash/commons-worker-remote @attocash/commons-wallet
```

This package is published as ESM and includes TypeScript declarations.

## Requirements

- Node.js 18 or newer is recommended.
- Docker or Podman must be installed and running.
- The test process must be allowed to pull and run containers.
- Use ESM imports. In Node.js files, use `.mjs` or set `"type": "module"`.

Node.js examples that use the Atto clients should expose `require` for the underlying runtime:

```js
import {createRequire} from 'node:module'

globalThis.require = createRequire(import.meta.url)
```

## What It Provides

- `AttoNodeMockAsyncBuilder`: starts a local Atto node backed by MySQL.
- `AttoWorkerMockAsyncBuilder`: starts a local Work Server.
- `baseUrl` properties that plug directly into `AttoNodeClientAsyncBuilder` and `AttoWorkerAsyncBuilder`.
- A generated local genesis transaction so tests can start with a funded account.
- Builder options for container names, images, MySQL settings, and custom genesis transactions.

## Quick Example

```js
import {createRequire} from 'node:module'
import {
  AttoAmount,
  AttoMnemonic,
  AttoUnit,
  toAttoIndex,
  toPrivateKey,
  toSeedAsync,
} from '@attocash/commons-core'
import {AttoNodeClientAsyncBuilder} from '@attocash/commons-node-remote'
import {AttoWorkerAsyncBuilder} from '@attocash/commons-worker-remote'
import {AttoWalletAsyncBuilder} from '@attocash/commons-wallet'
import {
  AttoNodeMockAsyncBuilder,
  AttoWorkerMockAsyncBuilder,
} from '@attocash/commons-test'

globalThis.require = createRequire(import.meta.url)

const mnemonic = await AttoMnemonic.generate()
const seed = await toSeedAsync(mnemonic)
const genesisPrivateKey = await toPrivateKey(seed, toAttoIndex(0))

const nodeMock = await new AttoNodeMockAsyncBuilder(genesisPrivateKey).build()
const workerMock = await new AttoWorkerMockAsyncBuilder().build()

try {
  await nodeMock.start()
  await workerMock.start()

  const nodeClient = new AttoNodeClientAsyncBuilder(nodeMock.baseUrl).build()
  const worker = new AttoWorkerAsyncBuilder(workerMock.baseUrl).build()

  const wallet = new AttoWalletAsyncBuilder(nodeClient, worker)
    .signerProviderSeed(seed)
    .build()

  const sender = toAttoIndex(0)
  const receiver = toAttoIndex(1)

  await wallet.openAccount(sender)
  await wallet.openAccount(receiver)

  const receiverAddress = await wallet.getAddress(receiver)
  const amount = AttoAmount.from(AttoUnit.ATTO, '1')
  const transaction = await wallet.sendByIndex(sender, receiverAddress, amount, null)

  console.log(`Published ${transaction.hash}`)
} finally {
  await workerMock.stop()
  await nodeMock.stop()
}
```

## Custom Images and Names

Use builder methods when a test suite needs pinned images, isolated container names, or custom MySQL settings.

```js
const nodeMock = await new AttoNodeMockAsyncBuilder(genesisPrivateKey)
  .name('atto-node-test')
  .image('ghcr.io/attocash/node:live')
  .mysqlImage('mysql:8.4')
  .dbName('node')
  .dbUser('root')
  .dbPassword('root')
  .pullImages(false)
  .logOutput(false)
  .build()

const workerMock = await new AttoWorkerMockAsyncBuilder()
  .name('atto-worker-test')
  .image('ghcr.io/attocash/work-server:cpu')
  .pullImage(false)
  .logOutput(false)
  .build()
```

For release gates, prefer immutable image digests in `.image(...)` and `.mysqlImage(...)`.
Container logs are disabled by default so generated test keys are not copied into CI logs; enable `.logOutput(true)` only while debugging.

## Lifecycle

Always await `stop()` in `finally` or your test framework's teardown hook. Stop mocks in reverse acquisition order.

```js
let nodeMock
let workerMock

beforeAll(async () => {
  nodeMock = await new AttoNodeMockAsyncBuilder(genesisPrivateKey).build()
  workerMock = await new AttoWorkerMockAsyncBuilder().build()

  await nodeMock.start()
  await workerMock.start()
})

afterAll(async () => {
  await workerMock?.stop()
  await nodeMock?.stop()
})
```

`baseUrl` is available only after `start()` has completed.
`close()` remains available as a synchronous compatibility fallback, but it cannot guarantee teardown has completed before the next test starts.

## Full Example

The repository includes a runnable JavaScript example that uses `@attocash/commons-test` with the individual Atto
Commons packages to start mock services, create a wallet, open accounts, send transactions, and listen to monitors:

- [examples/js-client](https://github.com/attocash/commons/tree/main/examples/js-client)

## API Summary

| Export                       | Purpose                                                                       |
|------------------------------|-------------------------------------------------------------------------------|
| `AttoNodeMockAsyncBuilder`   | Builds a node mock backed by an Atto node container and a MySQL container.    |
| `AttoNodeMockAsync`          | Starts, stops, and exposes the node mock `baseUrl` and `genesisTransaction`.  |
| `AttoWorkerMockAsyncBuilder` | Builds a Work Server mock container.                                          |
| `AttoWorkerMockAsync`        | Starts, stops, and exposes the worker mock `baseUrl`.                         |
| `AttoNodeMockConfiguration`  | Configuration object for lower-level Kotlin/JVM use.                          |

## Documentation

- [Atto documentation](https://atto.cash/docs)
- [Integration overview](https://atto.cash/docs/integration)
- [Node integration](https://atto.cash/docs/integration/node)
- [Work Server integration](https://atto.cash/docs/integration/work-server)
- [Atto Commons repository](https://github.com/attocash/commons)

## License

[BSD 3-Clause](https://github.com/attocash/commons/blob/main/LICENSE)
