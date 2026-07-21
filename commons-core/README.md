# commons-core

Core primitives and utilities used across the Atto ecosystem. Multiplatform (JVM/JS/Wasm).

What you get:

- Mnemonic and seed derivation (BIP‑39 style)
- Keys and addresses
- Blocks and transactions (send/receive/open)
- Serialization (buffer/JSON/Protobuf) and validation helpers
- Hashing and proof‑of‑work interfaces

## Installation

Gradle:

```kotlin
implementation("cash.atto:commons-core:<version>")
```

NPM:

```sh
npm install @attocash/commons-core
```

## Examples (from tests)

### Generate mnemonic, derive seed, and keys

```kotlin
val mnemonic = AttoMnemonic.generate()
val seed = mnemonic.toSeed()

val privateKey = seed.toPrivateKey(0U)
val publicKey = privateKey.toPublicKey()
val address = AttoAddress(AttoAlgorithm.V1, publicKey)
```

### Sign and verify a message

```kotlin
val message = "atto message".encodeToByteArray()
val signature = privateKey.signMessage(message)

check(signature.isValidMessage(publicKey, message))
```

### Build and validate a transaction

```kotlin
val receiveBlock = AttoReceiveBlock(
  version = 0U.toAttoVersion(),
  network = AttoNetwork.LOCAL,
  algorithm = AttoAlgorithm.V1,
  publicKey = publicKey,
  height = 2U.toAttoHeight(),
  balance = AttoAmount.MAX,
  timestamp = AttoInstant.now(),
  previous = AttoHash(Random.nextBytes(ByteArray(32))),
  sendHashAlgorithm = AttoAlgorithm.V1,
  sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
)

val tx = AttoTransaction(
  block = receiveBlock,
  signature = privateKey.sign(receiveBlock.hash),
  work = AttoWorker.cpu().work(receiveBlock),
)

check(tx.isValid())
```

Transaction decoding is structural only: `fromByteArray`, `fromBuffer`, and serializers parse the payload without
performing cryptographic verification. Call the suspend `validate()` or `isValid()` method when verification is
required.

### Serialize/deserialize

```kotlin
val bytes = tx.toByteArray()
val txFromBytes = AttoTransaction.fromByteArray(bytes)

val json = tx.toJson()
val txFromJson = AttoTransaction.fromJson(json)
```

### CPU proof‑of‑work

This example also requires `commons-worker` or `@attocash/commons-worker`.

```kotlin
val work = AttoWorker.cpu().work(receiveBlock)
```

See `commons-core/src/commonTest/kotlin/cash/atto/commons/AttoTransactionTest.kt` for more end‑to‑end examples.

## Suspend crypto API

Browser implementations use WebCrypto, whose digest, key import, signing, and verification operations are
asynchronous. The common Kotlin API therefore suspends for every operation that can reach those platform APIs:

- mnemonic creation and generation: `AttoMnemonic.fromWords`, `fromPhrase`, `fromEntropy`, and `generate`;
- private-key derivation: `AttoSeed.toPrivateKey`;
- Ed25519 key loading and signing: `AttoPrivateKey.toPublicKey`, `toSigner`, `sign`, and `signMessage`;
- Ed25519 verification: `AttoSignature.isValid` and `isValidMessage`;
- verification that depends on a signature: `AttoSignedVote.isValid`, `AttoTransaction.validate`, and
  `AttoTransaction.isValid`.

Pure parsing, serialization, BLAKE2b hashing, address operations, block validation, and proof-of-work checks remain
synchronous. The suspend methods are hidden from Java. JVM callers can use the corresponding `*Blocking` methods
exposed by `AttoMnemonics`, `AttoSeeds`, `AttoPrivateKeys`, `AttoPublicKeys`, `AttoSigners`, `AttoSignatures`,
`AttoVotes`, and `AttoTransactions`. Java callers that need asynchronous execution can schedule those operations on
their own executor.

JavaScript exports suspend members directly as Promise-returning methods, for example
`await mnemonic.toSeedAsync()`, `await seed.toPrivateKey(index)`, and `await privateKey.toPublicKey()`.
Deprecated top-level compatibility functions remain available throughout 7.x and will be removed in 8.0.
