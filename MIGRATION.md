# Migrating Atto Commons applications

This guide collects API and representation changes that affect existing consumers of the current checkout.
Use [release notes](https://github.com/attocash/commons/releases) to identify the changes in a particular published
version. The [module READMEs](README.md#choose-a-module) describe how to use the current APIs.

## Addresses

Blocks, votes, accounts, account entries, and receivables use `AttoAddress` as their canonical identity.
Algorithm and public-key properties are derived projections. Update constructors, `copy` calls, and destructuring
to use `address`, `receiverAddress`, `representativeAddress`, or `subjectAddress` as appropriate.

For example, replace `block.copy(publicKey = key)` with:

```kotlin
block.copy(address = AttoAddress(block.algorithm, key))
```

Account operations accept addresses:

```kotlin
AttoAccount.open(representativeAddress, receivable)
account.send(receiverAddress, amount)
account.change(representativeAddress)
```

OPEN block height is a constant `1`, so it is no longer a constructor or `copy` parameter. JavaScript constructors
also take addresses in place of algorithm/public-key pairs.

These are source API changes. Existing JSON fields/defaults, binary layouts, and hashes are preserved for the same
values. Blocks continue to emit their address and algorithm/public-key projections. Accounts, receivables, and
votes retain their original wire identity fields without an added address field; account entries emit address
projections when `encodeDefaults` is enabled.

When an address is present in JSON, decoding ignores its projected copies, including conflicting or malformed
copies. Payloads without an address can still supply the algorithm/public-key pair required by older codecs.
OPEN height is emitted as `"1"`, and incoming height copies are ignored.

## Mnemonic entropy

For the supported 24-word English mnemonics, `AttoMnemonic.fromEntropy()` now accepts exactly **32 bytes** of raw
entropy. `toEntropy()` returns those 32 bytes. The additional checksum byte stays internal.

Earlier APIs accepted/exported a 33-byte buffer that included the checksum. Replace those buffers with their
32-byte entropy portion before calling the new API. A 33-byte input is rejected. Existing mnemonic phrases and
seeds are unchanged by this entropy representation change.

## Unicode passphrases

Seed derivation applies BIP-39 NFKD normalization to the mnemonic and the full `"mnemonic" + passphrase` salt
before PBKDF2-HMAC-SHA512. The Kotlin `toSeed()`, JavaScript `toSeedAsync()`, and JVM `toSeedBlocking()` entry points
all use this derivation.

Older derivation skipped normalization. Passphrases whose Unicode representation changes under NFKD can now
derive different seeds, keys, and addresses. Empty and ASCII passphrases are unchanged. There is no legacy raw
seed-derivation mode in the current API.

## JSON numbers

Default JSON encodes amounts, heights, and timestamps as decimal strings so JavaScript consumers can preserve
all 64 bits. Timestamps still represent Unix epoch milliseconds. An explicitly selected
`AttoInstantAsStringSerializer` uses ISO dates. Small numeric values, including protocol versions and key indexes,
remain JSON numbers.

Decoding accepts both decimal strings and older numeric fields, including mixed payloads. Binary serialization
and hashes are unaffected by the JSON representation change. Applications parsing JSON directly should accept
strings and use `bigint` for exact arithmetic rather than converting large values to JavaScript `number`.

The Kotlin `AttoHeight.value: ULong` backing property is not exported to JavaScript. Replace `height.value` reads
with `BigInt(height.toString())`.

## Suspending cryptography

Operations that can use WebCrypto are suspending Kotlin APIs. This includes mnemonic creation and seed derivation,
private-key derivation, Ed25519 key loading/signing, and signature-dependent verification. Call them from a
coroutine or a `suspend` entry point. Parsing, serialization, BLAKE2b hashing, address operations, and local block
and proof-of-work checks remain synchronous.

Java uses the corresponding `*Blocking` facades; asynchronous Java applications can schedule those calls on their
own executor. JavaScript awaits Promise-returning members, such as:

```javascript
const seed = await mnemonic.toSeedAsync();
const privateKey = await seed.toPrivateKey(index);
const publicKey = await privateKey.toPublicKey();
```

Use member methods in new code. Older top-level forwarding functions remain available but are deprecated.

## JavaScript packages

Replace aggregate `@attocash/commons-js` imports with imports from the package that owns each API:

```javascript
import { AttoMnemonic } from "@attocash/commons-core";
import { AttoNodeClientAsyncBuilder } from "@attocash/commons-node-remote";
import { AttoWalletAsyncBuilder } from "@attocash/commons-wallet";
```

Install matching Commons versions. For a local checkout, install matching local tarballs for internal dependencies
together; see the [JavaScript example](examples/js-client/README.md).

The current aggregate runtime does not re-export the individual packages' APIs. Some Kotlin worker factories
also have no public JavaScript constructor; consult the [worker guides](README.md#choose-a-module) for supported
entry points. npm declarations alone are not proof that a runtime export exists.

## Subscription completion

Async node and monitor subscriptions report completion through `onCancel(Throwable?)`. A stream failure is passed
through unchanged; normal completion and cancellation report `null`. Change callbacks that explicitly require
`Exception?` to accept `Throwable?`.
