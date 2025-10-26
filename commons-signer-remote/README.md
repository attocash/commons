# commons-signer-remote

Remote HTTP implementation of `AttoSigner`. Useful when private keys live in an external signer service (HSM, KMS, wallet server) and the app needs to request signatures.

Highlights:
- `AttoSigner.remote(url, retryEvery, headerProvider)` convenience
- Lazily resolves `publicKey` from the remote service
- Signs `AttoChallenge` + `AttoInstant` (challenge timestamps)

## Quick start

```kotlin
// Create a remote signer (retries on failure)
val signer = AttoSigner.remote("http://localhost:8081")

// Remote public key
val address = signer.address

// Sign block
val signature = signer.sign(block)

// Sign a challenge
val challenge = AttoChallenge.random()
val timestamp = AttoInstant.now()
val signature = signer.sign(challenge, timestamp)
```
