# commons-gatekeeper

Gatekeeper helpers for Atto backend authentication. This module wires wallet signing into the token flow used by Atto
backend services and provides authenticated node/worker client factories.

Highlights:

- `AttoAuthenticator` for authorization header generation
- JWT decoding and expiry checks
- `AttoNodeClient.attoBackend(...)` and `AttoWorker.attoBackend(...)` convenience factories

## Installation

Gradle:

```kotlin
implementation("cash.atto:commons-gatekeeper:<version>")
```

NPM:

```sh
npm install @attocash/commons-core @attocash/commons-node @attocash/commons-node-remote
npm install @attocash/commons-worker @attocash/commons-worker-remote @attocash/commons-gatekeeper
```

## Usage

```kotlin
val authenticator = AttoAuthenticator.attoBackend(
  network = AttoNetwork.LIVE,
  signer = signer,
)

val client = AttoNodeClient.attoBackend(
  network = AttoNetwork.LIVE,
  authenticator = authenticator,
)

val worker = AttoWorker.attoBackend(
  network = AttoNetwork.LIVE,
  authenticator = authenticator,
)
```

Use `AttoAuthenticator.custom(url, signer)` for a non-standard gatekeeper endpoint. Use
`authenticator.toHeaderProvider()` when you need to pass authenticated headers into a lower-level client builder.
