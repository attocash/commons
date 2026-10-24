# Atto Commons

Kotlin and JavaScript libraries for building Atto applications: derive keys, create and sign transactions,
connect to nodes, manage wallets, and generate proof of work.

The modules target Kotlin/JVM, Kotlin/JS, and Kotlin/Wasm where supported. Java callers use JVM blocking
facades; JavaScript and TypeScript callers use ESM packages with Promise-based APIs.

## Choose a module

| Task | Module |
| --- | --- |
| Work with mnemonics, keys, addresses, amounts, and transactions | [commons-core](commons-core/README.md) |
| Query accounts and consume node streams | [commons-node](commons-node/README.md) |
| Connect to a node over HTTP | [commons-node-remote](commons-node-remote/README.md) |
| Share HTTP transport between remote adapters | [commons-transport](commons-transport/README.md) |
| Sign through an external signer service on the JVM | [commons-signer-remote](commons-signer-remote/README.md) |
| Manage accounts, send funds, and receive payments | [commons-wallet](commons-wallet/README.md) |
| Generate proof of work on a CPU | [commons-worker](commons-worker/README.md) |
| Generate proof of work with OpenCL on the JVM | [commons-worker-opencl](commons-worker-opencl/README.md) |
| Generate proof of work with WebGPU or WebGL in a browser | [commons-worker-web](commons-worker-web/README.md) |
| Request proof of work from a Work Server | [commons-worker-remote](commons-worker-remote/README.md) |
| Configure Commons in a Spring Boot application | [commons-spring-boot-starter](commons-spring-boot-starter/README.md) |
| Run tests against mock nodes and workers | [commons-test](commons-test/README.md) |

For JavaScript, use individual `@attocash/commons-*` packages. The deprecated
[commons-js](commons-js/README.md) project is retained for older aggregate-package integrations.
The [npm linkage project](commons-npm-bundle/README.md) is internal build tooling.

## Install

Install the modules you need, using the same release version across Commons dependencies.
For a Kotlin application, start with core:

```kotlin
dependencies {
    implementation("cash.atto:commons-core:<version>")
}
```

For JavaScript or TypeScript:

```sh
npm install @attocash/commons-core
```

Use ESM imports (`.mjs`, or `"type": "module"` in `package.json`). Node.js 24 is used in CI;
the current hash dependency requires Node.js 20.19 or newer. Browser cryptography requires WebCrypto.
Each module README lists any additional dependencies and platform requirements.

## Create an address

This Kotlin program runs without a node or worker:

```kotlin
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoMnemonic

suspend fun main() {
    val mnemonic = AttoMnemonic.generate()
    val seed = mnemonic.toSeed()
    val publicKey = seed.toPrivateKey(0U).toPublicKey()
    println(publicKey.toAddress(AttoAlgorithm.V1))
}
```

Continue with the [core guide](commons-core/README.md) for JavaScript, message signing, and serialization.
Use the [wallet guide](commons-wallet/README.md) to connect account operations to a node and worker.
Creating an address does not create a funded account on the network.

## Examples

- [Java client](examples/java-client/README.md): JVM blocking APIs and mock services.
- [JavaScript client](examples/js-client/README.md): Promise APIs, a wallet, and node subscriptions.

Both examples use local mock services. Their READMEs describe the pinned dependencies and container prerequisites.

## Build and test

Use the checked-in Gradle wrapper. Most modules use a JDK 17 toolchain; the Spring Boot starter uses JDK 21.
Browser tests need Chrome/Chromium, OpenCL tests need a driver/runtime, and mock-node tests need a
Docker-compatible container runtime.

```sh
./gradlew ktlintCheck allTests -Psigning.skip=true
./gradlew :commons-spring-boot-starter:test -Psigning.skip=true
```

The JVM-only Spring test task is separate from multiplatform `allTests`. To check core alone:

```sh
./gradlew :commons-core:ktlintCheck :commons-core:allTests -Psigning.skip=true
```

To try an unreleased checkout from npm, pack matching local versions:

```sh
./gradlew packJsPackage -Pversion=0.0.0-local.1 -Psigning.skip=true
```

Tarballs are written to each module's `build/packages/` directory. Install the local tarballs for the package
and its Commons dependencies together; avoid mixing local packages with a different published version.
The [JavaScript example](examples/js-client/README.md) describes local package installation.

## Upgrading and help

Read [the migration guide](MIGRATION.md) when updating existing applications. It covers address-based APIs,
raw mnemonic entropy, Unicode passphrases, JSON numeric fields, and asynchronous APIs.

Use [GitHub issues](https://github.com/attocash/commons/issues) for bugs and questions, and
[releases](https://github.com/attocash/commons/releases) for published changes.
Contributions should include focused checks for changed behavior and pass the applicable Gradle checks.

[BSD 3-Clause license](LICENSE).
