# Atto Java Client Example

This example demonstrates how to use the Atto Commons Java library to create wallets, manage accounts, and perform
transactions.

## Prerequisites

- Java 17 or higher
- Maven (included via wrapper)
- Docker or Podman installed and running

## Building

Build the example:

```bash
./mvnw clean package
```

## Running the Example

Execute the example:

```bash
./mvnw exec:exec
```

The example runs in its own JVM so Testcontainers can complete its shutdown hooks.

## What the Example Does

This example demonstrates the core functionality of the Atto Commons library:

1. **Generates a mnemonic and seed** - Creates a new wallet seed from a random mnemonic
2. **Starts mock servers** - Launches AttoNodeMockAsync and AttoWorkerMockAsync for testing
3. **Creates a wallet** - Initializes an AttoWalletAsync with auto-receive functionality
4. **Opens accounts** - Creates three accounts (indices 0, 1, and 2)
5. **Monitors transactions** - Sets up transaction and account entry monitors
6. **Performs transfers** - Sends ATTO from the genesis account to the other accounts
7. **Displays balances** - Shows account balances and heights after transactions
8. **Cleans up** - Properly closes clients, monitors, wallet jobs, and mock servers

## Key Features Demonstrated

- **Mnemonic generation** using `AttoMnemonics.generateBlocking()`
- **Seed derivation** using `AttoSeeds.toSeedBlocking()`
- **Private key derivation** from seed and index using `AttoPrivateKeys.toPrivateKeyBlocking()`
- **Mock server setup** for testing without a real node using builder pattern
- **Async client and worker** creation with `AttoNodeClientAsyncBuilder` and `AttoWorkerAsyncBuilder`
- **Account monitoring** for auto-receive functionality
- **Transaction and account entry monitoring** with custom height providers
- **Wallet builder pattern** with auto-receive configuration
- **Account management** (opening, checking balances)
- **Transaction sending** between accounts with `wallet.send()`
- **Proper resource cleanup** before stopping the mock servers

## Important Notes

- The example uses **mock servers** that run locally for testing
- The genesis account (index 0) starts with the maximum ATTO balance
- Mock servers are automatically cleaned up when the example completes
- Core cryptographic setup uses the blocking JVM API; wallet, client, worker, and mock operations use
  `CompletableFuture.get()` to wait for results
- Transaction and account monitors start from height 2 for the genesis account (skipping the genesis block at height 1)
