# Java client example

Run a complete local wallet demo from Java: start an Atto node, MySQL, and a Work Server in containers, then send funds between accounts and observe transactions and account entries.

## Prerequisites

- A JDK; Java 21 or newer is a suitable choice for this example.
- Docker or a compatible Podman API accessible to Testcontainers, with permission to create containers and networks.
- Network access to download Maven dependencies and missing container images.

The Maven wrapper is included. The [POM](pom.xml) currently pins Commons `7.0.2`; running this example uses those artifacts, not the current checkout automatically. The example leaves container image selection to the mock defaults; see [commons-test](../../commons-test/README.md) for this checkout's defaults and runtime setup.

## Run

From this directory:

```sh
./mvnw clean package
./mvnw exec:exec
```

The `exec:exec` configuration launches a separate JVM so Testcontainers shutdown hooks can run. The demo prints service URLs, addresses, transaction hashes, and balances, then closes its resources. Startup can take time while images download and MySQL initializes.

## Read the example

[Main.java](src/main/java/cash/atto/Main.java) generates a mnemonic and derives index zero's private key using the blocking JVM cryptographic helpers. It passes that key to the node mock builder, which creates a local genesis transaction. That specific account starts with `AttoAmount.MAX` on the local network; a randomly generated wallet on an existing network would have no funds.

The rest of the example uses `CompletableFuture` APIs and waits with `.get()`. It registers three wallet accounts, enables auto-receive, sends 1,000 and 2,000 ATTO from index zero, and displays the results. Transaction and account-entry monitors start the genesis account at height two to skip its genesis block.

This is a demonstration: the representative is an all-zero test address and balance displays use fixed delays rather than assertions that receiving has finished. Use a network-appropriate representative and bounded outcome checks in an application or test suite. `openAccount` registers local wallet membership; the first received payment opens a new recipient on-chain.

The normal success path cancels subscriptions and closes the wallet, monitors, and node client; the outer `finally` closes the mock servers. Both mock starts occur before that cleanup block. For reusable tests, put startup inside the same `try/finally` that owns teardown. See [commons-test lifecycle guidance](../../commons-test/README.md#start-and-stop-local-services).

## Further reading

- [Wallet APIs](../../commons-wallet/README.md) and [node monitors](../../commons-node/README.md).
- [JavaScript version](../js-client/README.md).
- [Cryptographic API migration](../../MIGRATION.md#suspending-cryptography), [repository overview](../../README.md), and [BSD 3-Clause license](../../LICENSE).
