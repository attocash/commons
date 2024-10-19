# Atto Commons

## Overview

The `commons` project is a Kotlin library that provides utilities and common functionality for working cryptocurrency accounts, transactions, mnemonics, and more. It includes support for cryptographic operations, address generation, seed management, and proof-of-work computation, among other features.

**NOTE: This library is in active development, and frequent major releases may introduce breaking changes. We appreciate your understanding and tolerance as the API evolves during this phase.**

## Features

- Generate and validate Atto cryptocurrency accounts and addresses.
- Manage private keys, public keys, and mnemonics.
- Serialize and deserialize Atto transactions and other data structures.
- Perform proof-of-work calculations using CPU or OpenCL.

## Getting Started

To include `commons` in your Kotlin project, add the following dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("cash.atto:commons:${attoVersion}")
}
```

### Example Usage

#### Generate a New Account, Seed, and Keys

You can generate a mnemonic, derive a seed, and create private and public keys:

```kotlin
import cash.atto.commons.AttoMnemonic
import cash.atto.commons.toHex

fun main() {
    val mnemonic = AttoMnemonic.generate()
    println("Created mnemonic: \${mnemonic.words}")

    val seed = mnemonic.toSeed()
    println("Created seed: \${seed.value.toHex()}")

    val privateKey = seed.toPrivateKey(0U)
    println("Created privateKey: \${privateKey.value.toHex()}")

    val publicKey = privateKey.toPublicKey()
    println("Created publicKey: \${publicKey.value.toHex()}")
}
```

#### Sign a Transaction

To sign a transaction, use the following example:

```kotlin
val privateKey = AttoPrivateKey.generate()
val publicKey = privateKey.toPublicKey()

val receiveBlock = AttoReceiveBlock(
    version = 0U.toAttoVersion(),
    network = AttoNetwork.LOCAL,
    algorithm = AttoAlgorithm.V1,
    publicKey = publicKey,
    height = 2U.toAttoHeight(),
    balance = AttoAmount.MAX,
    timestamp = Clock.System.now(),
    previous = AttoHash(Random.nextBytes(ByteArray(32))),
    sendHashAlgorithm = AttoAlgorithm.V1,
    sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32)))
)

val signedTransaction = AttoTransaction(
    block = receiveBlock,
    signature = privateKey.sign(receiveBlock.hash),
    work = AttoWorker.cpu().work(receiveBlock)
)

println("Signed Transaction: \${signedTransaction}")
```

#### Proof-of-Work Calculation

The library also includes utilities for performing proof-of-work calculations:

```kotlin
import cash.atto.commons.AttoWork

val worker = AttoWorker.opencl()


val work = worker.work(receiveBlock)
println("Proof of Work: \${work}")
```

### Benchmarks

This project also includes a set of benchmarks to test the performance of key operations like address generation, hashing, and proof-of-work calculation. You can find these benchmarks in the `src/benchmarks` directory.

To run the benchmarks:

```sh
./gradlew benchmark
```

## Contributing

Contributions are welcome! Please feel free to open issues or submit pull requests.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
