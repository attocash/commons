# Atto JavaScript Client Example

This example demonstrates how to use the Atto Commons JavaScript library to create wallets, manage accounts, and perform transactions using Node.js.

## Prerequisites

- Node.js (version 18 or higher recommended)
- The Atto Commons project built with JS target

## Setup

Build the JavaScript libraries from the project root:
   ```bash
   ./gradlew :commons-js:jsNodeProductionLibraryDistribution
   ./gradlew :commons-test:jsNodeProductionLibraryDistribution
   ```

## Running the Example

Execute the example:
```bash
node main.mjs
```

## What the Example Does

This example mirrors the functionality of the Java client example:

1. **Generates a mnemonic and seed** - Creates a new wallet seed from a random mnemonic
2. **Starts mock servers** - Launches AttoNodeMock and AttoWorkerMock for testing
3. **Creates a wallet** - Initializes an AttoWallet with auto-receive functionality
4. **Opens accounts** - Creates three accounts (indices 0, 1, and 2)
5. **Monitors transactions** - Sets up transaction and account entry monitors
6. **Performs transfers** - Sends ATTO from the genesis account to the other accounts
7. **Displays balances** - Shows account balances and heights after transactions

## Key Features Demonstrated

- **Mnemonic generation** using `AttoMnemonic.generate()`
- **Seed derivation** using `toSeedAsync()`
- **Private key derivation** from seed and index
- **Mock server setup** for testing without a real node
- **Wallet builder pattern** with auto-receive configuration
- **Account management** (opening, checking balances)
- **Transaction sending** between accounts
- **Real-time monitoring** of transactions and account entries

## Important Notes

- The example uses **unreleased library versions** from `build/dist/js/productionLibrary/`
- It imports from **commons-js** (core functionality) and **commons-test** (mock servers)
- The mock servers run locally and are cleaned up automatically
- All operations use the async/await pattern for JavaScript promises
