# commons-node-remote

Connect to an Atto node over HTTP. This module implements [AttoNodeClient](../commons-node/README.md) with account lookups, transaction publishing, clock synchronization, and NDJSON streams for accounts, receivables, transactions, and account entries.

## Kotlin

Add the dependency and choose a reachable node URL:

```kotlin
implementation("cash.atto:commons-node-remote:<version>")
```

Pass the node URL and an Atto address as command-line arguments:

```kotlin
import cash.atto.commons.AttoAddress
import cash.atto.commons.node.AttoNodeClient
import cash.atto.commons.node.remote

suspend fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: <node-url> <address>" }
    val client = AttoNodeClient.remote(args[0])
    val address = AttoAddress.parse(args[1])
    val account = client.account(address)
    println(account?.toJson() ?: "Account has not been opened")
    println("Node-adjusted time: ${client.now()}")
}
```

For authenticated requests, provide a suspending function that returns headers. It runs for each request, allowing token refresh. This partial snippet replaces client construction above:

```kotlin
val client = AttoNodeClient.remote(nodeUrl) {
    mapOf("Authorization" to "Bearer $token")
}
```

Streams are Kotlin `Flow` values: collecting starts the request, and cancelling collection cancels it. This partial snippet collects receivables for `address`:

```kotlin
import cash.atto.commons.AttoAmount
import kotlinx.coroutines.flow.collect

client.receivableStream(address, AttoAmount.MIN).collect { receivable ->
    println(receivable.toJson())
}
```

Use `accountEntryStream(address, fromHeight, toHeight)` or `transactionStream(address, fromHeight, toHeight)` for history by height. An omitted `toHeight` leaves the upper bound open. [Account monitors](../commons-node/README.md#track-accounts-in-kotlin) add membership management and height tracking.

## JavaScript

Install the split packages at matching versions:

```sh
npm install @attocash/commons-core @attocash/commons-node @attocash/commons-node-remote
```

This browser ES module uses the exported async builder. Replace the URL and address with your node and account; the node must permit requests from the browser's origin.

```javascript
import { AttoAddress } from '@attocash/commons-core';
import { AttoNodeClientAsyncBuilder } from '@attocash/commons-node-remote';

const client = new AttoNodeClientAsyncBuilder('https://your-node.example').build();
try {
  const address = AttoAddress.parse('atto://aarfzz26z5pfwrkdcwt4jdhhe2vvixscqwehgmfjqxku43rgtjso5p5cjw6fw');
  const account = await client.accountByAddress(address);
  console.log(account?.toJson() ?? 'Account has not been opened');
} finally {
  client.close();
}
```

The builder's `header(name, value)` sets a fixed request header. Callback methods such as `onAccountByAddress` and `onTransactionAll` return cancellable jobs. Their `onCancel` callback receives the failure, or `null` for normal completion and cancellation. Keep the client open while subscriptions are needed, then cancel their jobs and close it. See the [Node.js example](../examples/js-client/README.md) for the Node ESM setup and a complete subscription lifecycle.

Keep amounts and heights as Commons values, decimal strings, or `bigint`; use model `toJson()`/`fromJson()` methods to preserve protocol numbers. See [JSON number guidance](../MIGRATION.md#json-numbers) and [JavaScript package migration](../MIGRATION.md#javascript-packages).

## Runtime behavior

- `account(address)` returns `null` for HTTP 404; other failures propagate.
- `now()` returns an adjusted `AttoInstant`. `now(currentTime)` returns the server's `TimeDifferenceResponse`.
- `publish(transaction)` waits for an acknowledgement with the submitted transaction hash and rejects a different hash with `AttoNodePublishMismatchException`.
- Ordinary requests generally use ten-second timeouts; a single-account lookup uses one second. Streams have no overall request timeout, a one-hour socket idle timeout, and a ten-second connection timeout. Publishing uses a one-minute request/socket timeout.
- The remote client does not reconnect streams automatically. Use the [monitors](../commons-node/README.md) where their retry behavior fits, or manage retries in the caller.

See the [implementation](src/commonMain/kotlin/cash/atto/commons/node/AttoNodeClientRemote.kt) and [address-routing tests](src/jvmTest/kotlin/cash/atto/commons/node/AttoNodeClientRemoteAddressTest.kt) for the HTTP mapping. Client APIs take `AttoAddress`; see [address migration](../MIGRATION.md#addresses) for older call sites.

[Project overview](../README.md) · [License](../LICENSE)
