# JavaScript client example

Run a local wallet demo in Node.js using the individual Atto Commons npm packages. It starts a containerized node, MySQL database, and Work Server, sends funds between three accounts, and prints transaction and account-entry streams.

## Prerequisites

- Node.js 24 and npm.
- Docker or a compatible Podman API accessible to Testcontainers, with permission to create containers and networks.
- Network access to download npm packages and missing container images.

This is a Node.js ESM program. [package.json](package.json) currently pins Commons `7.0.2`; `npm install` uses those packages, not the current checkout automatically. Use [local tarballs](#try-the-current-checkout) to run against the current source. The example leaves container image selection to the mock defaults; this checkout's defaults and runtime requirements are documented in [commons-test](../../commons-test/README.md).

## Run

From this directory:

```sh
npm install
npm start
```

`npm start` runs `node main.mjs`. Expect service URLs, generated addresses, published transaction hashes, stream events, and balances. Startup can take time while images download and MySQL initializes. The script prints its generated mnemonic; use it only for disposable local testing.

## Try the current checkout

To use locally built Commons packages instead of the pinned release, run these commands from the repository root:

```sh
./gradlew packJsPackage -Pversion=0.0.0-local.1 -Psigning.skip=true
cd examples/js-client
npm install --no-save \
  ../../commons-core/build/packages/attocash-commons-core-0.0.0-local.1.tgz \
  ../../commons-transport/build/packages/attocash-commons-transport-0.0.0-local.1.tgz \
  ../../commons-node/build/packages/attocash-commons-node-0.0.0-local.1.tgz \
  ../../commons-node-remote/build/packages/attocash-commons-node-remote-0.0.0-local.1.tgz \
  ../../commons-worker/build/packages/attocash-commons-worker-0.0.0-local.1.tgz \
  ../../commons-worker-remote/build/packages/attocash-commons-worker-remote-0.0.0-local.1.tgz \
  ../../commons-wallet/build/packages/attocash-commons-wallet-0.0.0-local.1.tgz \
  ../../commons-test/build/packages/attocash-commons-test-0.0.0-local.1.tgz
npm start
```

Install all eight tarballs together so their internal Commons dependencies use the same local version.

## Read the example

[main.mjs](main.mjs) exposes `require` with `createRequire` for the underlying client runtime. It awaits `AttoMnemonic.generate()`, `mnemonic.toSeedAsync()`, and `seed.toPrivateKey(index)`, builds and starts both mock services, then configures `AttoWalletAsyncBuilder` with `.signerProviderSeed(seed)` and auto-receive.

Index zero holds `AttoAmount.MAX` because its private key is used to generate the local node's genesis transaction. Newly generated wallets on an existing network have no funds. The example opens indices zero through two, then sends 1,000 and 2,000 ATTO from zero using `sendByIndex`. Opening a wallet account registers it locally; receiving the first payment opens a new recipient on-chain.

The script uses Promise methods including `openAccount`, `getAddress`, and `getAccountByIndex`. This demo uses an all-zero representative address and fixed delays before printing balances. Choose a representative appropriate for your network and wait for an observed result with a timeout in production code or integration tests.

## Cleanup

The script cancels and joins its stream jobs, closes the wallet, account monitor, and node client, then awaits worker and node `stop()`. Both mock starts occur before the inner cleanup block; if startup fails between them, use your container runtime to inspect any remaining test resources. For reusable tests, put startup inside the same `try/finally` that owns teardown, as shown in [commons-test](../../commons-test/README.md#start-and-stop-local-services).

Long-lived subscriptions keep work running until canceled. See [subscription completion](../../MIGRATION.md#subscription-completion) for completion callback behavior.

## Further reading

- [Wallet API](../../commons-wallet/README.md) and [remote node client](../../commons-node-remote/README.md).
- [Java version](../java-client/README.md).
- [JavaScript package migration](../../MIGRATION.md#javascript-packages), [repository overview](../../README.md), and [BSD 3-Clause license](../../LICENSE).
