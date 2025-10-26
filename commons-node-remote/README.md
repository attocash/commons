# commons-node-remote

Remote HTTP/WebSocket implementation of `AttoNodeClient`. Uses Ktor under the hood and streams NDJSON where supported.

Highlights:
- `AttoNodeClient.remote(baseUrl, headerProvider)` convenience
- `AttoNodeClientAsyncBuilder.remote(url)` for incremental setup
- Streams for accounts, receivables, transactions, and account entries
- `publish(AttoTransaction)` to send blocks to the node
- `now()` to synchronize time with the node

## Quick start

```kotlin
// Optional authenticated header provider (JWT, API keys, etc.)
suspend fun headers(): Map<String, String> = mapOf("Authorization" to "Bearer <jwt>")

val client = AttoNodeClient.remote("http://localhost:8080", ::headers)

// Current time difference (node clock vs yours)
val timeDiff = client.now()

// Fetch accounts by address
val accounts = client.account(listOf(address1, address2))

// Stream receivables for many addresses (min amount filter)
val receivables = client.receivableStream(listOf(address1, address2), AttoAmount.MIN)

// Stream account entries by height range
val entries = client.accountEntryStream(address1.publicKey, 1U.toAttoHeight(), null)

// Publish a transaction
client.publish(transaction)
```

## Async builder

```kotlin
val async = AttoNodeClientAsyncBuilder.remote("http://localhost:8080")
  .header("Authorization", "Bearer <jwt>")
  .build()

// Use async operations if needed, or wrap into the sync client
```

Implementation notes and APIs: see `AttoNodeClientRemote.kt`.
