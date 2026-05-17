# @attocash/commons-js

[![npm version](https://img.shields.io/npm/v/@attocash/commons-js.svg)](https://www.npmjs.com/package/@attocash/commons-js)
[![license](https://img.shields.io/github/license/attocash/commons.svg)](https://github.com/attocash/commons/blob/main/LICENSE)

Deprecated aggregate JavaScript and TypeScript package for Atto Commons.

`@attocash/commons-js` still exists as a compatibility package for applications that import everything from one module.
New code should install and import the individual packages instead.

## Install

Prefer individual packages:

```sh
npm install @attocash/commons-core @attocash/commons-node @attocash/commons-node-remote
npm install @attocash/commons-worker @attocash/commons-worker-remote @attocash/commons-wallet
npm install --save-dev @attocash/commons-test
```

The deprecated aggregate package remains available during the transition:

```sh
npm install @attocash/commons-js
```

Both styles are published as ESM and include TypeScript declarations.

## Import Migration

Old aggregate import:

```js
import {
  AttoAddress,
  AttoMnemonic,
  AttoNodeClientAsyncBuilder,
  AttoWalletAsyncBuilder,
  AttoWorkerAsyncBuilder,
  toSeedAsync,
} from '@attocash/commons-js'
```

New individual imports:

```js
import {AttoAddress, AttoMnemonic, toSeedAsync} from '@attocash/commons-core'
import {AttoNodeClientAsyncBuilder} from '@attocash/commons-node-remote'
import {AttoWalletAsyncBuilder} from '@attocash/commons-wallet'
import {AttoWorkerAsyncBuilder} from '@attocash/commons-worker-remote'
```

## Package Map

| Package                           | Use it for                                                                                            |
|-----------------------------------|-------------------------------------------------------------------------------------------------------|
| `@attocash/commons-core`          | Protocol primitives, amounts, mnemonics, keys, addresses, blocks, transactions, hashes, JSON helpers. |
| `@attocash/commons-node`          | Node operations, account monitors, transaction monitors, account-entry monitors, receivable helpers.  |
| `@attocash/commons-node-remote`   | HTTP node client builder.                                                                             |
| `@attocash/commons-worker`        | Local CPU proof-of-work helpers.                                                                      |
| `@attocash/commons-worker-remote` | HTTP Work Server client builder.                                                                      |
| `@attocash/commons-worker-web`    | Browser WebGPU/WebGL proof-of-work worker.                                                            |
| `@attocash/commons-wallet`        | Wallet builder and async wallet helpers.                                                              |
| `@attocash/commons-gatekeeper`    | Gatekeeper client helpers.                                                                            |
| `@attocash/commons-test`          | Mock node and worker services for tests and demos.                                                    |

## Requirements

- Node.js 18 or newer is recommended.
- Use ESM imports. In Node.js files, use `.mjs` or set `"type": "module"`.
- Remote node and worker clients still need `require` exposed for the underlying runtime:

```js
import {createRequire} from 'node:module'

globalThis.require = createRequire(import.meta.url)
```

## Examples

- [examples/js-client](https://github.com/attocash/commons/tree/main/examples/js-client) shows the individual package
  imports with local mock node and worker services.
- [Atto documentation](https://atto.cash/docs) has integration guides for nodes, work servers, wallet servers, and
  offline signing.

## License

[BSD 3-Clause](https://github.com/attocash/commons/blob/main/LICENSE)
