# Atto Commons

A multiplatform library of building blocks for Atto applications. It provides primitives (mnemonics, keys, addresses, blocks), client tooling to talk to a node, wallet utilities, and proof‑of‑work workers (CPU/OpenCL/remote). Kotlin/JVM, Kotlin/JS, and Kotlin/Wasm are supported where applicable.

NOTE: The API is evolving and may include breaking changes between releases.

## Modules

- commons-core — primitives: mnemonic/seed, keys, addresses, blocks/transactions, serialization, utilities.
- commons-node — node operations and monitors (account membership, transactions, account entries).
- commons-node-remote — remote HTTP client for talking to a node.
- commons-signer-remote — remote signer client for external key management.
- commons-wallet — simple wallet utilities built on top of node + worker.
- commons-worker — CPU proof‑of‑work implementation.
- commons-worker-opencl — OpenCL proof‑of‑work implementation (JVM only).
- commons-worker-remote — talk to a remote worker service.
- commons-spring-boot-starter — Spring Boot integrations for Atto services.
- commons-js — JavaScript/TypeScript bindings packaged as `@attocash/commons-js`.

Each module has its own README with detailed examples based on tests. Start here for a quick taste.

## Installation

Gradle coordinates vary per module, for example:

```kotlin
dependencies {
  // Core primitives
  implementation("cash.atto:commons-core:<version>")
  // Node client (remote over HTTP)
  implementation("cash.atto:commons-node-remote:<version>")
  // Wallet utilities
  implementation("cash.atto:commons-wallet:<version>")
  // CPU worker (pure Kotlin)
  implementation("cash.atto:commons-worker:<version>")
  // OpenCL worker (JVM)
  runtimeOnly("cash.atto:commons-worker-opencl:<version>")
}
```

On JS/Node you can use the NPM package:

```sh
npm i @attocash/commons-js
```

## Quick start

- Generate a mnemonic and derive a seed (suspend):

```kotlin
val mnemonic = AttoMnemonic.generate()
val seed = mnemonic.toSeed()
```

- Derive keys and address:

```kotlin
val privateKey = seed.toPrivateKey(0U)
val publicKey = privateKey.toPublicKey()
val address = AttoAddress(AttoAlgorithm.V1, publicKey)
```

- Connect to a node and a worker (remote services):

```kotlin
val client = AttoNodeClient.remote("http://localhost:8080")
val worker = AttoWorker.remote("http://localhost:8085")
```

- Create a wallet, open accounts, and send funds (based on tests):

```kotlin
val wallet = AttoWallet.create(client, worker, seed)

// Open index 0 (often the genesis in tests) and a few more
wallet.openAccount(0U.toAttoIndex())
wallet.openAccount(1U.toAttoIndex(), 3U.toAttoIndex())

// Query balances
val balance0 = wallet.getAccount(0U.toAttoIndex())!!.balance

// Send 1 ATTO from account 0 to account 2
val amount = AttoAmount.from(AttoUnit.ATTO, "1")
val toAddress = wallet.getAddress(2U.toAttoIndex())
val tx = wallet.send(0U.toAttoIndex(), toAddress, amount)
```

- Listen to node monitors and acknowledge progress:

```kotlin
val accountMonitor = client.createAccountMonitor()
val txnMonitor = accountMonitor.toTransactionMonitor { 1U.toAttoHeight() }

// Collect first message
val msg = txnMonitor.stream().first()
msg.acknowledge()
```

For full examples and advanced flows (auto-receive, account-entry monitor, OpenCL), see the module READMEs below.

## Module READMEs

- commons-core/README.md — primitives and serialization examples
- commons-wallet/README.md — wallet setup, send/receive, auto-receiver, monitors
- commons-node/README.md — node client and monitors
- commons-node-remote/README.md — remote node client
- commons-signer-remote/README.md — remote signer client
- commons-worker/README.md — CPU PoW
- commons-worker-opencl/README.md — OpenCL PoW
- commons-worker-remote/README.md — remote worker client
- commons-spring-boot-starter/README.md — Spring Boot integration
- commons-js/README.md — JS/TS usage
