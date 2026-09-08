# Atto JavaScript Client Example

This example demonstrates how to use the Atto Commons JavaScript packages to create wallets, manage accounts, and
perform
transactions using Node.js.

## Prerequisites

- Node.js 24 or higher
- A working internet connection to fetch npm packages and images
- Docker or Podman installed and running

## Setup

Install the released npm packages in this directory:

```bash
npm install
```

For an unreleased checkout before the split packages are available on npm, build local tarballs with
`./gradlew packJsPackage -Pversion=0.0.0-local` from the repository root and install the tarballs from each module's
`build/packages` directory.

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
5. **Monitors transactions** - Subscribes to transaction and account entry streams
6. **Performs transfers** - Sends ATTO from the genesis account to the other accounts
7. **Displays balances** - Shows account balances and heights after transactions

## Key Features Demonstrated

- **Mnemonic generation** using `await AttoMnemonic.generate()`
- **Seed derivation** using `await mnemonic.toSeedAsync()`
- **Private key derivation** using `await seed.toPrivateKey()`
- **Mock server setup** for testing without a real node
- **Wallet builder pattern** with auto-receive configuration
- **Account management** (opening, checking balances)
- **Transaction sending** between accounts
- **Real-time streaming** of transactions and account entries
