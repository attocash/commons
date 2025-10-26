# commons-js

JavaScript/TypeScript bindings for Atto. Published as `@attocash/commons-js`.

- Works in Node.js (ESM)
- Pairs with the Atto node and worker remote services
- Provides utilities to build, sign, and publish transactions

## Install

```sh
npm i @attocash/commons-js
```

## Quick example (Node ESM)

```js
import {
  AttoAddress,
  AttoAlgorithm,
  AttoAmount,
  AttoNetwork,
  AttoPrivateKey,
  AttoTransaction,
  AttoUnit,
  createAttoWorker,
  createCustomAttoNodeClient,
  fromHexToByteArray,
  toHex,
  toSignerJs,
} from '@attocash/commons-js'

// Prepare a signer from a local private key
const privateKey = new AttoPrivateKey(fromHexToByteArray('000000...'))
const signer = toSignerJs(privateKey)
const address = new AttoAddress(AttoAlgorithm.V1, signer.publicKey)

// Remote services
const client = createCustomAttoNodeClient(AttoNetwork.LOCAL, 'http://localhost:8080')
const worker = createAttoWorker('http://localhost:8085')

// Query accounts and process receivables...
const accounts = await client.account([address])

// Build and publish a transaction (see test/test.mjs for full flow)
// const { block } = attoAccountSend(...)
// const signature = await signer.signBlock(block)
// const work = await worker.workBlock(block)
// const tx = new AttoTransaction(block, signature, work)
// await client.publish(tx)
```

For a complete executable example, see `test/test.mjs` and `node-js/index.mjs` in this repository.
