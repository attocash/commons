# commons-signer-remote

Request signatures from an HTTP signer service while keeping private keys outside the calling application. This JVM module implements `AttoSigner` for blocks, votes, and timestamped challenges. The service must implement the Commons signer HTTP contract; a general HSM or KMS API needs an adapter.

## Connect to a signer

Add the dependency and provide a signer service URL. Use HTTPS for remote connections and supply the authentication headers required by your service.

```kotlin
implementation("cash.atto:commons-signer-remote:<version>")
```

This module is not published as an npm package. Pass the signer URL as a command-line argument; this example assumes a service that accepts unauthenticated requests:

```kotlin
import cash.atto.commons.AttoChallenge
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoSigner
import cash.atto.commons.node.remote

suspend fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: <signer-url>" }
    val signer = AttoSigner.remote(
        url = args[0],
        maxAttempts = 3U,
        headerProvider = { emptyMap() },
    )
    val challenge = AttoChallenge.generate()
    val timestamp = AttoInstant.now()
    val signature = signer.sign(challenge, timestamp)
    println("Signer: ${signer.address}")
    println("Signature: $signature")
}
```

The suspending `headerProvider` runs for each request, so it can obtain fresh credentials. `publicKey` and `address` resolve lazily: their first access fetches the public key with `runBlocking` and blocks the calling thread.

## Signing and failures

`sign(block)` and `sign(vote)` require the target's public key to match the signer. Every returned block, vote, or challenge signature is verified locally before being returned. An invalid result throws `AttoRemoteSignerInvalidSignatureException`. Direct `sign(hash)` and the inherited `signMessage(message)` are unsupported and throw `NotImplementedError`.

Retries wait `retryEvery` between attempts (one second by default). `maxAttempts = null`, the default, leaves retryable failures unbounded; set a limit when the caller needs a bounded attempt count. Cancellation propagates without retrying. HTTP 408 and 429 are retryable; other HTTP client/server error statuses are terminal and throw `AttoRemoteSignerTerminalException`. Non-HTTP exceptions from a request are retried, and reaching the attempt limit throws `AttoRemoteSignerRetriesExhaustedException`.

## Service contract

The base URL must expose these JSON endpoints:

| Request | Body | Response |
| --- | --- | --- |
| `GET /public-keys` | — | `{ "publicKey": "…" }` |
| `POST /blocks` | `{ "target": <block> }` | `{ "signature": "…" }` |
| `POST /votes` | `{ "target": <vote> }` | `{ "signature": "…" }` |
| `POST /challenges` | `{ "target": <challenge>, "timestamp": <instant> }` | `{ "signature": "…" }` |

Payloads use Commons model serializers. See the [implementation](src/commonMain/kotlin/cash/atto/commons/node/HttpSignerRemote.kt), [mock signer service](src/commonTest/kotlin/cash/atto/commons/node/MocktRemoteSigner.kt), and [signing/failure tests](src/commonTest/kotlin/cash/atto/commons/node/HttpSignerRemote.kt) for exact serialization and behavior.

[Project overview](../README.md) · [License](../LICENSE)
