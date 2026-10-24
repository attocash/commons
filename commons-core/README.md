# commons-core

Protocol values and cryptography for Atto applications: mnemonics, keys, addresses, amounts, blocks,
transactions, signing, hashing, and serialization. Available for Kotlin/JVM, Kotlin/JS, and Kotlin/Wasm,
with Java blocking facades and a JavaScript/TypeScript npm package.

Core operations run without a node. For networking, use [commons-node-remote](../commons-node-remote/README.md);
for proof-of-work generation, use a [worker implementation](../README.md#choose-a-module).

## Install

```kotlin
dependencies {
    implementation("cash.atto:commons-core:<version>")
}
```

```sh
npm install @attocash/commons-core
```

JavaScript packages use ESM. Node.js 24 is used in CI; the current hash dependency requires Node.js 20.19
or newer. Browsers need WebCrypto. Key derivation, signing, and signature verification use suspending Kotlin
functions and Promise-returning JavaScript methods.

## Derive an address

### Kotlin

```kotlin
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoMnemonic

suspend fun main() {
    val mnemonic = AttoMnemonic.generate()
    val seed = mnemonic.toSeed()
    val privateKey = seed.toPrivateKey(0U)
    val publicKey = privateKey.toPublicKey()
    val address = publicKey.toAddress(AttoAlgorithm.V1)
    println(address)
}
```

### JavaScript and TypeScript

Save this as an `.mjs` file, or use a project configured for ESM:

```javascript
import { AttoAlgorithm, AttoMnemonic, toAttoIndex } from "@attocash/commons-core";

const mnemonic = await AttoMnemonic.generate();
const seed = await mnemonic.toSeedAsync();
const privateKey = await seed.toPrivateKey(toAttoIndex(0));
const publicKey = await privateKey.toPublicKey();
const address = publicKey.toAddress(AttoAlgorithm.V1);
console.log(address.toString());
```

Mnemonics use 24 English words. `fromPhrase()` validates the words and checksum; `fromEntropy()` accepts
32 bytes of raw entropy, and `toEntropy()` returns those 32 bytes without the checksum.
`toSeed(passphrase)` uses BIP-39 NFKD normalization and PBKDF2-HMAC-SHA512 to derive a 64-byte seed.
See the [migration notes](../MIGRATION.md#mnemonic-entropy) when upgrading entropy or passphrase handling.

## Sign a message

The following Kotlin snippet continues from the address example:

```kotlin
val message = "hello".encodeToByteArray()
val signature = privateKey.signMessage(message)
check(signature.isValidMessage(publicKey, message))
```

In JavaScript, create a signer from the private key:

```javascript
const signer = await privateKey.toSigner();
const message = Int8Array.from(new TextEncoder().encode("hello"));
const signature = await signer.signMessage(message);
console.log(await signature.isValidMessage(signer.publicKey, message));
```

Message signing uses the `ATTO Signed Message v1` domain-separated format. Verify the exact bytes that were
signed; changing the text or its encoding changes the message. Use `Int8Array` for the generated TypeScript byte APIs.

## Decode and validate a transaction

Given an encoded transaction, decode it and explicitly validate it:

```kotlin
import cash.atto.commons.AttoTransaction

suspend fun decodeTransaction(json: String): AttoTransaction {
    val transaction = AttoTransaction.fromJson(json)
    check(transaction.isValid()) { "Invalid transaction" }
    return transaction
}
```

Decoding parses the structure. Validation checks the block, signature, and proof of work; acceptance by a node
also depends on ledger state. Constructing a public key or signature checks its length, not its authenticity.

Once a transaction is available, serialize it with:

```kotlin
val bytes = transaction.toByteArray()
val json = transaction.toJson()
val restored = AttoTransaction.fromByteArray(bytes)
```

See [transaction tests](src/commonTest/kotlin/cash/atto/commons/AttoTransactionTest.kt) for complete block,
signature, and worker examples. Work generation requires an additional worker module.

## Addresses and numeric values

Blocks and account models accept addresses as their identity. Their algorithm and public-key properties are
projections of the address; OPEN block height is always `1`.

Default JSON encodes amounts, heights, and timestamps as decimal strings to preserve their 64-bit values.
Timestamps are Unix epoch milliseconds. In JavaScript, use decimal strings or `bigint` for exact arithmetic:

```javascript
import { toAttoHeight } from "@attocash/commons-core";

const height = toAttoHeight("18446744073709551615");
const value = BigInt(height.toString());
console.log(value);
```

The Kotlin `ULong` height backing value is not exposed to JavaScript. Protocol versions and key indexes remain
JSON numbers. [Migration details](../MIGRATION.md#json-numbers) describe older numeric payloads and wire compatibility.

## Java callers

Use the JVM `*Blocking` facades for suspending cryptographic operations, such as
`AttoMnemonics.generateBlocking()`, `AttoSeeds.toSeedBlocking(mnemonic)`, and
`AttoPublicKeys.toPublicKeyBlocking(privateKey)`. Schedule them on an executor when asynchronous Java execution
is needed. See the [Java example](../examples/java-client/README.md).

## Further reading

- [Mnemonic tests](src/commonTest/kotlin/cash/atto/commons/AttoMnemonicTest.kt)
- [Seed normalization vectors](src/commonTest/kotlin/cash/atto/commons/AttoSeedNormalizationTest.kt)
- [Address and wire migration](../MIGRATION.md#addresses)
- [All modules](../README.md#choose-a-module)
- [License](../LICENSE)
