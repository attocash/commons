# commons-node

Node-facing operations and higher-level monitors. This module defines the interfaces and helpers used by remote clients and wallet flows.

Highlights:
- `AttoNodeClient` + `AttoNodeOperations`
- Account membership monitor: `AttoAccountMonitor`
- Height-aware monitors: `toTransactionMonitor()`, `toAccountEntryMonitor()`
- Receivable stream utilities

## Basic client operations

The concrete remote client lives in `commons-node-remote`. Example below assumes you built a client there and pass it in.

```kotlin
val client: AttoNodeClient = AttoNodeClient.remote("http://localhost:8080")

// Single account by public key
val account = client.account(address.publicKey)

// Multiple accounts by address
val accounts = client.account(listOf(address1, address2))
```

## Membership monitor

Track a dynamic set of addresses and derive streams from it.

```kotlin
val monitor = client.createAccountMonitor()

// Add addresses to track
monitor.monitor(listOf(address1, address2))

// Ask the node for current account snapshots for tracked addresses
val current = monitor.getAccounts()
```

## Receivable stream

```kotlin
// Stream receivables for all tracked addresses
val min = AttoAmount.MIN
val receivables = monitor.receivableStream(min)
```

## Height-aware transaction monitor

```kotlin
// Build a transaction monitor from the membership monitor.
// Provide per-address initial heights; default to 1 if unknown.
val initialHeights = mapOf(/* AttoAddress -> AttoHeight */)
val txMonitor = monitor.toTransactionMonitor { address ->
  initialHeights[address] ?: 1U.toAttoHeight()
}

// Consume messages and acknowledge to advance stored heights
val msg = txMonitor.stream().first()
val tx = msg.value
msg.acknowledge() // moves height
```

## Height-aware account-entry monitor

```kotlin
val entryMonitor = monitor.toAccountEntryMonitor { 1U.toAttoHeight() }
val msg = entryMonitor.stream().first()
val entry = msg.value
msg.acknowledge() // moves height
```

See:
- `commons-node/src/commonMain/kotlin/cash/atto/commons/node/monitor/*`
- `commons-wallet` README for end-to-end usage with a wallet.
