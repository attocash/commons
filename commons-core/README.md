# commons-core

Core primitives and utilities used across the Atto ecosystem. Multiplatform (JVM/JS/Wasm).

What you get:

- Mnemonic and seed derivation (BIP‑39 style)
- Keys and addresses
- Blocks and transactions (send/receive/open)
- Serialization (buffer/JSON/Protobuf) and validation helpers
- Hashing and proof‑of‑work interfaces

## Examples (from tests)

### Generate mnemonic, derive seed, and keys

```kotlin
val mnemonic = AttoMnemonic.generate()
val seed = mnemonic.toSeed() // suspend

val privateKey = seed.toPrivateKey(0U)
val publicKey = privateKey.toPublicKey()
val address = AttoAddress(AttoAlgorithm.V1, publicKey)
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

### Serialize/deserialize

```kotlin
val buf = tx.toBuffer()
val txFromBuf = AttoTransaction.fromBuffer(buf)

val json = Json.encodeToString(tx)
val txFromJson = Json.decodeFromString<AttoTransaction>(json)
```

### CPU proof‑of‑work

```kotlin
val work = AttoWorker.cpu().work(receiveBlock)
```

See `commons-core/src/commonTest/kotlin/cash/atto/commons/AttoTransactionTest.kt` for more end‑to‑end examples.
