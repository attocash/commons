# commons-wallet

A thin wallet utility layer built on top of the node client and a PoW worker. Provides helpers to:
- Derive addresses from a seed (per index)
- Open accounts, send funds, and query balances
- Start an auto-receiver that listens for receivables and publishes receive/open blocks

Examples below are distilled from `commons-wallet` and integration tests.

## Setup

```kotlin
// Primitives
val mnemonic = AttoMnemonic.generate()
val seed = mnemonic.toSeed()

// Remote services (replace with your endpoints)
val client = AttoNodeClient.remote("http://localhost:8080")
val worker = AttoWorker.remote("http://localhost:8085")

// Wallet instance
val wallet = AttoWallet.create(client, worker, seed)
```

## Open accounts

```kotlin
// Often index 0 is the first account
wallet.openAccount(0U.toAttoIndex())

// Open multiple accounts at once
wallet.openAccount(1U.toAttoIndex(), 3U.toAttoIndex())

// Get the public address for an index
val address2 = wallet.getAddress(2U.toAttoIndex())
```

## Query balances and state

```kotlin
val account0 = wallet.getAccount(0U.toAttoIndex())
val balance0 = account0?.balance // AttoAmount or null if not opened yet
```

## Send funds

```kotlin
val amount = AttoAmount.from(AttoUnit.ATTO, "1")
val destination = wallet.getAddress(2U.toAttoIndex())
val tx = wallet.send(0U.toAttoIndex(), destination, amount)
```

## Monitors and auto-receive

You can subscribe to node monitors and acknowledge processed heights. Start an auto-receiver to handle incoming receivables.

```kotlin
// Create account membership monitor from the client
val accountMonitor = client.createAccountMonitor()

// Start auto-receiver: provide a representative address supplier
val receiverJob = wallet.startAutoReceiver(accountMonitor) {
  AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32)))
}

// Transaction monitor: track transactions from the current height forward
val initialHeights = mapOf(/* address -> nextHeight */)
val txnMonitor = accountMonitor.toTransactionMonitor { address ->
  initialHeights[address] ?: 1U.toAttoHeight()
}

// Collect and acknowledge a message
val msg = txnMonitor.stream().first()
msg.acknowledge() // move the height forward

// When done
receiverJob.cancel()
```

See `commons-wallet/src/commonTest/kotlin/cash/atto/commons/wallet/AttoWalletTest.kt` for a full scenario.
