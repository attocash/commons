# commons-core

Core primitives and utilities used across the Atto ecosystem. Multiplatform (JVM/JS/Wasm).

What you get:

- 24-word English mnemonics and BIP-39 seed derivation
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

`fromEntropy()` accepts 32 bytes (256 bits) of raw entropy for a 24-word English mnemonic. `toEntropy()` returns
those 32 bytes; the checksum is added and validated internally. Earlier releases used a 33-byte buffer including
the checksum. Entropy import/export now requires raw 32-byte values; existing mnemonic phrases and their seeds
are unchanged by this representation change.

`toSeed(passphrase)` applies Unicode NFKD normalization to the mnemonic and full `"mnemonic" + passphrase` salt
before PBKDF2-HMAC-SHA512, as required by BIP-39.
The same behavior applies to JavaScript's `toSeedAsync()` and JVM's `toSeedBlocking()`.

Unicode passphrases changed by NFKD can derive different keys and addresses than in previous releases.
Empty and ASCII passphrases are unchanged. There is no legacy or raw derivation mode.

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
  address = AttoAddress(AttoAlgorithm.V1, publicKey),
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

`AttoPublicKey` and `AttoSignature` constructors check byte length, but do not establish that a key can sign or that a
signature is valid. Public keys also appear in receiver and representative addresses, and signatures may be decoded
before validation.

Blocks, votes, accounts, account entries, and receivables store addresses as their canonical identity. Read `algorithm`,
`publicKey`, and the receiver, representative, or subject equivalents as projections of the corresponding address.
OPEN block `height` is a constant projection and always `1`.

The address-based constructor and data-class `copy` API intentionally break source compatibility. Constructors,
`copy` calls, and destructuring now follow the canonical addresses: replace `block.copy(publicKey = key)` with
`block.copy(address = AttoAddress(block.algorithm, key))`, and use `receiverAddress`, `representativeAddress`, or
`subjectAddress` for related identities. OPEN `height` is not a constructor or `copy` parameter.
JavaScript constructors now take addresses in place of the algorithm/public-key pairs. Account operations accept addresses:
`AttoAccount.open(representativeAddress, receivable)`, `account.send(receiverAddress, amount)`, and
`account.change(representativeAddress)`.

JSON preserves its existing fields and defaults. Blocks emit legacy algorithm/public-key projections alongside their
addresses; accounts, receivables, and votes omit addresses, and account entries include addresses only with `encodeDefaults`.
When an address is present in input, decoding ignores its projected fields, including conflicting or malformed
values. Payloads without an address can still supply the legacy algorithm/public-key pair. OPEN `height` is always
emitted as `"1"`, and input `height` is ignored.

JSON output, binary layouts, and hashes remain unchanged for the same values.

### Serialize/deserialize

```kotlin
val bytes = tx.toByteArray()
val txFromBytes = AttoTransaction.fromByteArray(bytes)

val json = tx.toJson()
val txFromJson = AttoTransaction.fromJson(json)
```

JSON encodes amounts, heights, and default timestamps as decimal strings to preserve all 64 bits in JavaScript.
Timestamps remain Unix epoch milliseconds; `AttoInstantAsStringSerializer` still uses ISO dates when explicitly selected.
Small numeric types, including protocol versions and key indexes, remain JSON numbers.

Decoding accepts both decimal strings and legacy numeric fields, including mixed payloads. Binary formats, transaction
bytes, and hashes are unchanged. Consumers that parse JSON directly must accept strings for these fields; use `BigInt`
for exact arithmetic rather than converting them to JavaScript `number`.

### JavaScript height values

Use the decimal string API to convert a height to JavaScript `bigint` without precision or sign loss:

```typescript
import { toAttoHeight } from "@attocash/commons-core";

const height = toAttoHeight("18446744073709551615");
const value = BigInt(height.toString());
```

The Kotlin `height.value: ULong` property is not exported to JavaScript. Replace existing JavaScript `height.value`
reads with `BigInt(height.toString())`; directly exporting `ULong` exposes signed backing values above `2^63 - 1`.

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
`AttoSignature.isValidMessage(publicKey, messageBytes)` verifies the same raw bytes accepted by
`AttoSigner.signMessage(messageBytes)`; the signature uses the `ATTO Signed Message v1` domain-separated framing.
Message verification prechecks point encodings, known small-order points, and signature scalar range before using the
platform verifier.
Use a `Uint8Array` or `Int8Array` for bytes, and do not trim, normalize, or otherwise rewrite signed input.
Deprecated top-level compatibility functions remain available throughout 7.x and will be removed in 8.0.
