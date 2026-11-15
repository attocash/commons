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

Keep amounts and heights as Commons values, decimal strings, or `bigint`; use model `toJson()`/`fromJson()` methods to preserve protocol numbers. See [JSON number guidance](../commons-core/README.md#addresses-and-numeric-values) and [JavaScript package migration](../commons-js/README.md#replace-aggregate-imports).

## Runtime behavior

- NDJSON requests use v2 routes, including GET, POST, publication, hash, and height-range streams.
  Publishing uses `POST /v2/transactions`; live transaction updates use `GET /v2/transactions/stream`.
  There is no fallback to legacy routes. Deploy a server providing every required v2 route before upgrading
  this client; see [compatibility and rollout](../V2-STREAMS.md).
- Single-account snapshots and streams use `/v2/accounts/{encodedAddress}` and its stream suffixes.
  The full `atto://...` address is encoded once as one path segment (`atto%3A%2F%2F...`). Account lookup
  keeps its one-second timeout. Batch `account(addresses)` uses `POST /v2/accounts` with an `addresses`
  array and requires canonical account response addresses. Other scalar paths remain unchanged.
- JSON models require canonical owner and role addresses. Additional legacy keys are ignored by the
  transport's configured unknown-key policy; they cannot supply a missing address. Numeric scalar input
  remains supported, including scalar transaction responses at `GET /transactions/{hash}`.
- Blank and whitespace-only response lines are heartbeats and produce no domain values. They do not confirm
  a transaction or extend a publication deadline. Malformed nonblank or oversized lines still fail collection.
- `account(address)` returns `null` for HTTP 404; other failures propagate.
- `now()` returns an adjusted `AttoInstant`. `now(currentTime)` returns the server's `TimeDifferenceResponse`.
- `publish(transaction)` waits for an acknowledgement with the submitted transaction hash and rejects a different hash with `AttoNodePublishMismatchException`.
- Ordinary requests generally use ten-second timeouts; a single-account lookup uses one second. Streams have no overall request timeout, a one-hour socket idle timeout, and a ten-second connection timeout. Publishing uses a one-minute request/socket timeout.
- The remote client does not reconnect streams automatically. Use the [monitors](../commons-node/README.md) where their retry behavior fits, or manage retries in the caller.

See the [implementation](src/commonMain/kotlin/cash/atto/commons/node/AttoNodeClientRemote.kt) and [address-routing tests](src/jvmTest/kotlin/cash/atto/commons/node/AttoNodeClientRemoteAddressTest.kt) for the HTTP mapping. Client APIs take `AttoAddress`; see [address migration](../V2-STREAMS.md#address-based-account-routes) for older call sites.

[Project overview](../README.md) · [License](../LICENSE)

## Optional WebSocket streaming

HTTP NDJSON remains the default. Select WebSockets for streaming operations with:

```kotlin
val client = AttoNodeClient.remote(baseUrl, AttoNodeStreamTransport.WEBSOCKET)
```

Java and JavaScript builders expose `.streamTransport(AttoNodeStreamTransport.WEBSOCKET)`.
Finite account snapshots (including batch lookup), transaction lookup, time, and voter weights continue
using HTTP. `publish(transaction)` always uses HTTP `POST /v2/transactions` and waits for the first
matching NDJSON confirmation. `accountEntry(hash)` retains its first-of-stream behavior and uses the
selected stream transport. WebSockets support the 13 read-stream routes; publication is HTTP-only.

Each collected flow opens one public `/v2/stream` connection. The base URL's path prefix is preserved;
HTTP becomes `ws`, HTTPS becomes `wss`. The subscribe command contains the original operation method, v2
path, query, and optional JSON body. Encoded addresses remain encoded once within that path. Responses
contain canonical records, heartbeats, explicit completion, or a status-bearing error. Heartbeats produce
no domain values. A socket close without a completion frame fails the stream, including a normal close
code. Cancellation and first-of-stream completion close the socket. The client does not reconnect or fall
back to another transport.

Stream connection and receive-idle limits remain ten seconds and one hour. HTTP publication retains its
one-minute request and socket deadlines, including heartbeats, and a ten-second connection timeout. The 2,000-character record limit applies to the data record, excluding its envelope. Malformed,
binary, oversized, and receive-queue-overflow messages fail visibly. The receive queue is bounded to 64
frames; a protocol or engine overflow fails visibly. A 32-record server credit window and acknowledgements
after downstream delivery allow healthy slow consumers to apply backpressure without overflowing.

JVM and Node.js WebSockets send custom handshake headers. Browser WebSockets cannot send arbitrary
headers: selecting WebSockets with nonempty custom headers fails explicitly. Use HTTP streaming when
browser clients require those headers. Header credentials are never placed in a WebSocket data frame.
See the [wire protocol](../V2-STREAMS.md#public-websocket-stream-protocol).

Monitor membership changes use update commands on the same WebSocket. `monitor(...)` and
`stopMonitoring(...)` add and remove addresses without reconnecting retained members. Account,
receivable, entry, and transaction streams expose dynamic `Flow` overloads for Kotlin callers. A finite
updates flow seals the subscription after its last update; an empty updates flow completes without
opening a connection. An empty membership from a live updates flow keeps the socket open. Height
monitors freeze retained member ranges within an attempt and rebuild from acknowledged heights on retry.
HTTP monitor behavior remains unchanged.
