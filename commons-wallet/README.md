# commons-wallet

Manage seed-derived accounts, sign and publish transfers, and receive incoming funds through an Atto node and a proof-of-work worker. Use this module when you want account state and transaction construction managed for you; use [commons-node](../commons-node/README.md) directly for queries and streams.

## Install

```kotlin
implementation("cash.atto:commons-wallet:<version>")
```

For JavaScript and TypeScript:

```sh
npm install @attocash/commons-core @attocash/commons-node @attocash/commons-node-remote @attocash/commons-worker @attocash/commons-worker-remote @attocash/commons-wallet
```

Use the same release for all Commons packages. The [JavaScript guide](../commons-js/README.md) covers ESM and runtime setup; the [Java example](../examples/java-client/README.md) uses the `CompletableFuture` API.

## Open a wallet account

Start a node API and a Work Server before running this example. Replace the localhost URLs with your service endpoints. A generated seed has no funds: the example registers an address locally and queries its state; it does not create an on-chain account or a balance.

```kotlin
import cash.atto.commons.AttoMnemonic
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.remote
import cash.atto.commons.toAttoIndex
import cash.atto.commons.wallet.AttoWallet
import cash.atto.commons.worker.AttoWorker
import cash.atto.commons.worker.remote
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val seed = AttoMnemonic.generate().toSeed()
    val client = AttoNodeClient.remote("http://localhost:8080")
    AttoWorker.remote("http://localhost:8085").use { worker ->
        val wallet = AttoWallet.create(client, worker, seed)
        val index = 0U.toAttoIndex()
        wallet.openAccount(index)
        println(wallet.getAddress(index))
        println(wallet.getAccount(index)?.balance) // null until opened on-chain
    }
}
```

Keep the mnemonic securely if the account will hold funds. `openAccount` must precede `getAddress`, `getAccount`, sending, or receiving for that index. Opening the same index twice fails; `closeAccount` removes it from the wallet's local membership. A wallet created from one signer supports only index zero.

## Send and receive

Within a suspending context, send from an already funded, open wallet account:

```kotlin
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoUnit

// wallet, senderIndex, and destinationAddress are supplied by your application.
val transaction = wallet.send(
    senderIndex,
    destinationAddress,
    AttoAmount.from(AttoUnit.ATTO, "1"),
)
```

Sending publishes a send block. The destination must publish a receive block to add the funds to its balance. Use `wallet.receive(receivable, representativeAddress)` explicitly, or enable the auto-receiver. Receiving the first payment opens the on-chain account and requires a representative address.

The auto-receiver uses the wallet's address membership and a node account monitor. In an application coroutine scope:

```kotlin
import cash.atto.commons.node.monitor.createAccountMonitor
import cash.atto.commons.wallet.startAutoReceiver
import kotlinx.coroutines.cancelAndJoin

val monitor = client.createAccountMonitor()
val receiverJob = wallet.startAutoReceiver(scope, monitor) { representativeAddress }
try {
    // Keep the application scope alive while handling incoming payments.
} finally {
    receiverJob.cancelAndJoin()
}
```

Use a representative appropriate for your network. Cancel and join wallet jobs before closing workers or other closeable dependencies. The auto-receiver manages incoming receivables; the wallet's cached account state is not a general replacement for live node queries when other processes also publish for the same keys.

JavaScript uses `AttoWalletAsyncBuilder`, `.signerProviderSeed(seed)`, and awaited methods such as `openAccount`, `getAddress`, `getAccountByIndex`, and `sendByIndex`. See the [runnable JavaScript example](../examples/js-client/README.md) for setup, auto-receive, and teardown.

## Further reading

- [Wallet integration test](src/commonTest/kotlin/cash/atto/commons/wallet/AttoWalletTest.kt): a funded local genesis account, transfers, acknowledgements, and auto-receive.
- [commons-test](../commons-test/README.md): container-backed local services for integration tests.
- [Core types and cryptography](../commons-core/README.md) and [suspending API migration](../MIGRATION.md#suspending-cryptography).
- [Repository overview](../README.md) and [BSD 3-Clause license](../LICENSE).
