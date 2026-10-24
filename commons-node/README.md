# commons-node

Node client interfaces, coroutine streams, and account monitors. Use this module to write code against `AttoNodeClient` or track a changing set of addresses. Add [Commons Node Remote](../commons-node-remote/README.md) to connect to an HTTP node, or [Commons Test](../commons-test/README.md) for a mock node.

## Add the dependency

```kotlin
implementation("cash.atto:commons-node:<version>")
```

JavaScript consumers use the exported async clients and monitor builders:

```sh
npm install @attocash/commons-node
```

See the [remote client guide](../commons-node-remote/README.md#javascript) for a JavaScript connection example.

## Track accounts in Kotlin

This example also needs `cash.atto:commons-node-remote:<version>`, a reachable node, and an address to monitor. Pass the node URL and address as command-line arguments. `collect` keeps running until the stream completes, fails, or the coroutine is cancelled.

```kotlin
import cash.atto.commons.AttoAddress
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.monitor.createAccountMonitor
import cash.atto.commons.node.remote
import kotlinx.coroutines.flow.collect

suspend fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: <node-url> <address>" }
    val client = AttoNodeClient.remote(args[0])
    val monitor = client.createAccountMonitor()
    monitor.monitor(AttoAddress.parse(args[1]))

    for (account in monitor.getAccounts()) {
        println(account.toJson())
    }
    monitor.accountStream().collect { account ->
        println(account.toJson())
    }
}
```

`monitor(address)` and `monitor(addresses)` add membership; `stopMonitoring(address)` removes it. Membership changes restart streams for the new set, and emitted values are filtered to that set. `getAccount(address)` requires the address to be monitored; `getAccounts()` fetches snapshots for all members. `receivableStream(minAmount)` emits receivables for monitored receiver addresses.

## Resume from acknowledged heights

Transaction and account-entry monitors wrap each value in a message with `acknowledge()`. Acknowledgement records the message's height in memory for subsequent stream requests. Process the value before acknowledging it, and persist your own progress if it must survive process restarts. The recorded height is reused as the next search's `fromHeight`; consumers should tolerate replay.

This partial snippet continues with `monitor` from the example above:

```kotlin
import cash.atto.commons.AttoHeight
import cash.atto.commons.node.monitor.toTransactionMonitor

val transactions = monitor.toTransactionMonitor { AttoHeight.MIN }
transactions.stream().collect { message ->
    println(message.value.toJson())
    message.acknowledge()
}
```

Use `toAccountEntryMonitor` for the same pattern with account entries. A suspending height provider can load an initial height for each address.

Account streams and height monitors retry stream failures after ten seconds; the receivable monitor forwards failures to its collector. Normal stream completion does not trigger reconnection.

## Async subscriptions

`AttoNodeClientAsync` and async monitor builders expose callback subscriptions that return an `AttoJob`. Cancel the job to stop one subscription; close the client or monitor to cancel its owned subscriptions. On JavaScript, one-shot operations return promises; JVM wrappers use `CompletableFuture`.

The completion callback is named `onCancel` and accepts `Throwable?`: failures are passed through unchanged, while normal completion and cancellation report `null`. See [subscription migration notes](../MIGRATION.md#subscription-completion) when updating typed callbacks.

For the full operation set, see [AttoNodeOperations](src/commonMain/kotlin/cash/atto/commons/node/AttoNodeOperations.kt). See [monitor filtering tests](src/commonTest/kotlin/cash/atto/commons/node/monitor/AttoMonitorFilteringTest.kt) and the [wallet guide](../commons-wallet/README.md) for related flows.

[Project overview](../README.md) · [License](../LICENSE)
