# @attocash/commons-test

[![npm version](https://img.shields.io/npm/v/@attocash/commons-test.svg)](https://www.npmjs.com/package/@attocash/commons-test)
[![license](https://img.shields.io/github/license/attocash/commons.svg)](https://github.com/attocash/commons/blob/main/LICENSE)

Test utilities for Atto JavaScript and TypeScript integrations.

`@attocash/commons-test` provides local mock services for applications that use `@attocash/commons-js`. It starts a real Atto node container with a MySQL container and a real Work Server container, then exposes their mapped URLs so your tests can use the normal node, worker, wallet, and monitor clients.

## Install

```sh
npm install --save-dev @attocash/commons-test
npm install @attocash/commons-js
```

This package is published as ESM and includes TypeScript declarations.

## Requirements

- Node.js 18 or newer is recommended.
- Docker or Podman must be installed and running.
- The test process must be allowed to pull and run containers.
- Use ESM imports. In Node.js files, use `.mjs` or set `"type": "module"`.

Node.js examples that use the Atto clients should expose `require` for the underlying runtime:

```js
import { createRequire } from 'node:module'

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
import { createRequire } from 'node:module'
import {
  AttoAmount,
  AttoMnemonic,
  AttoNodeClientAsyncBuilder,
  AttoUnit,
  AttoWalletAsyncBuilder,
  AttoWorkerAsyncBuilder,
  toAttoIndex,
  toPrivateKey,
  toSeedAsync,
} from '@attocash/commons-js'
import {
  AttoNodeMockAsyncBuilder,
  AttoWorkerMockAsyncBuilder,
} from '@attocash/commons-test'

globalThis.require = createRequire(import.meta.url)

const mnemonic = AttoMnemonic.generate()
const seed = await toSeedAsync(mnemonic)
const genesisPrivateKey = toPrivateKey(seed, toAttoIndex(0))

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
  nodeMock.close()
  workerMock.close()
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
  .build()

const workerMock = await new AttoWorkerMockAsyncBuilder()
  .name('atto-worker-test')
  .image('ghcr.io/attocash/work-server:cpu')
  .build()
```

## Lifecycle

Always close mocks in `finally` or your test framework's teardown hook.

```js
let nodeMock
let workerMock

beforeAll(async () => {
  nodeMock = await new AttoNodeMockAsyncBuilder(genesisPrivateKey).build()
  workerMock = await new AttoWorkerMockAsyncBuilder().build()

  await nodeMock.start()
  await workerMock.start()
})

afterAll(() => {
  nodeMock?.close()
  workerMock?.close()
})
```

`baseUrl` is available only after `start()` has completed.

## Full Example

The repository includes a runnable JavaScript example that uses `@attocash/commons-test` with `@attocash/commons-js` to start mock services, create a wallet, open accounts, send transactions, and listen to monitors:

- [examples/js-client](https://github.com/attocash/commons/tree/main/examples/js-client)

## API Summary

| Export | Purpose |
| --- | --- |
| `AttoNodeMockAsyncBuilder` | Builds a node mock backed by an Atto node container and a MySQL container. |
| `AttoNodeMockAsync` | Starts, closes, and exposes the node mock `baseUrl` and `genesisTransaction`. |
| `AttoWorkerMockAsyncBuilder` | Builds a Work Server mock container. |
| `AttoWorkerMockAsync` | Starts, closes, and exposes the worker mock `baseUrl`. |
| `AttoNodeMockConfiguration` | Configuration object for lower-level Kotlin/JVM use. |

## Documentation

- [Atto documentation](https://atto.cash/docs)
- [Integration overview](https://atto.cash/docs/integration)
- [Node integration](https://atto.cash/docs/integration/node)
- [Work Server integration](https://atto.cash/docs/integration/work-server)
- [Atto Commons repository](https://github.com/attocash/commons)

## License

[BSD 3-Clause](https://github.com/attocash/commons/blob/main/LICENSE)
