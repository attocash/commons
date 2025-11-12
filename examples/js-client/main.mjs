import {createRequire} from 'node:module';

import {
  AttoAccountEntryMonitorAsyncBuilder,
  AttoAccountMonitorAsyncBuilder,
  AttoAddress,
  AttoAlgorithm,
  AttoAmount,
  AttoHeight,
  AttoMnemonic,
  AttoNodeClientAsyncBuilder,
  AttoPublicKey,
  AttoTransactionMonitorAsyncBuilder,
  AttoUnit,
  AttoWalletAsyncBuilder,
  AttoWorkerAsyncBuilder,
  toAttoHeight,
  toAttoIndex,
  toPrivateKey,
  toPublicKey,
  toSeedAsync,
} from '@attocash/commons-js';

import {
  AttoNodeMockAsyncBuilder,
  AttoWorkerMockAsyncBuilder,
} from '@attocash/commons-test';

// Expose require for Ktor runtime
globalThis.require = createRequire(import.meta.url);

async function main() {
  try {
    // Generate mnemonic and seed
    const mnemonic = AttoMnemonic.generate();
    const seed = await toSeedAsync(mnemonic);

    // Generate private key for genesis account (index 0) for mock node
    const genesisIndex = toAttoIndex(0);
    const genesisPrivateKey = toPrivateKey(seed, genesisIndex);

    // Create and start AttoNodeMockAsync using builder
    const nodeMock = await new AttoNodeMockAsyncBuilder(genesisPrivateKey).build().asPromise();
    await nodeMock.start().asPromise();
    console.log(`Node mock started at: ${nodeMock.baseUrl}`);

    // Create and start AttoWorkerMockAsync using builder
    const workerMock = await new AttoWorkerMockAsyncBuilder().build().asPromise();
    await workerMock.start().asPromise();
    console.log(`Worker mock started at: ${workerMock.baseUrl}`);

    try {
      // Create async client and worker using the mock server URLs
      const nodeClient = new AttoNodeClientAsyncBuilder(nodeMock.baseUrl).build();
      const worker = new AttoWorkerAsyncBuilder(workerMock.baseUrl).build();

      // Create account monitor for auto-receive functionality
      const accountMonitor = new AttoAccountMonitorAsyncBuilder(nodeClient).build();

      // Create transaction monitor to track transactions
      const transactionMonitor = new AttoTransactionMonitorAsyncBuilder(nodeClient, accountMonitor)
        .heightProvider((address) => {
          // Start from height 2 for genesis (skip genesis block at height 1)
          const genesisPublicKey = toPublicKey(genesisPrivateKey);
          const genesisAddress = new AttoAddress(AttoAlgorithm.V1, genesisPublicKey);
          if (address.equals(genesisAddress)) {
            return Promise.resolve(toAttoHeight(2));
          }
          return Promise.resolve(AttoHeight.MIN);
        })
        .build();

      // Create account entry monitor to track account entries
      const accountEntryMonitor = new AttoAccountEntryMonitorAsyncBuilder(nodeClient, accountMonitor)
        .heightProvider((address) => {
          // Start from height 2 for genesis (skip genesis block at height 1)
          const genesisPublicKey = toPublicKey(genesisPrivateKey);
          const genesisAddress = new AttoAddress(AttoAlgorithm.V1, genesisPublicKey);
          if (address.equals(genesisAddress)) {
            return Promise.resolve(toAttoHeight(2));
          }
          return Promise.resolve(AttoHeight.MIN);
        })
        .build();

      // Create AttoWalletAsync using builder with auto-receive enabled
      const wallet = new AttoWalletAsyncBuilder(nodeClient, worker)
        .signerProviderSeed(seed)
        .enableAutoReceiver(
          accountMonitor,
          AttoAmount.from(AttoUnit.ATTO, "1"),  // Minimum amount to auto-receive
          10,  // Retry interval in seconds
          () => {
            // Default representative address provider
            // Using a dummy address as default representative
            const dummyBytes = new Uint8Array(32);
            return new AttoAddress(AttoAlgorithm.V1, new AttoPublicKey(dummyBytes));
          }
        )
        .build();

      // Open genesis account (index 0) - this has the initial MAX balance
      const index0 = toAttoIndex(0);
      await wallet.openAccount(index0).asPromise();

      console.log("\n=== Genesis Account (Index 0) ===");
      const address0 = await wallet.getAddress(index0).asPromise();
      console.log(`Address: ${address0}`);

      const account0Details = await wallet.getAccountByIndex(index0).asPromise();
      if (account0Details) {
        console.log(`Initial Balance: ${account0Details.balance}`);
        console.log(`Height: ${account0Details.height}`);
      }

      // Open additional accounts
      const index1 = toAttoIndex(1);
      const index2 = toAttoIndex(2);

      await wallet.openAccount(index1).asPromise();
      await wallet.openAccount(index2).asPromise();

      console.log("\n=== Opened Additional Accounts ===");
      const address1 = await wallet.getAddress(index1).asPromise();
      const address2 = await wallet.getAddress(index2).asPromise();
      console.log(`Account 1 address: ${address1}`);
      console.log(`Account 2 address: ${address2}`);

      // Register transaction monitor to track all transactions
      console.log("\n=== Registering Transaction Monitor ===");
      const transactionJob = transactionMonitor.onTransaction(
        (transaction) => console.log(`Transaction monitor received: ${transaction.hash}`),
        (error) => {
          if (error) {
            console.error(`Transaction monitor error: ${error.message}`);
          }
        }
      );

      // Register account entry monitor to track all account entries
      console.log("=== Registering Account Entry Monitor ===");
      const accountEntryJob = accountEntryMonitor.onAccountEntry(
        (entry) => console.log(`Account entry monitor received: ${entry.hash}`),
        (error) => {
          if (error) {
            console.error(`Account entry monitor error: ${error.message}`);
          }
        }
      );

      // Send from genesis account (index 0) to account 1
      const sendAmount1 = AttoAmount.from(AttoUnit.ATTO, "1000");

      console.log("\n=== Sending First Transaction ===");
      console.log(`Sending ${sendAmount1} from index 0 to index 1...`);

      const sendTransaction1 = await wallet.sendByIndex(index0, address1, sendAmount1, null).asPromise();
      console.log(`Transaction hash: ${sendTransaction1.hash}`);
      console.log("Transaction sent successfully!");

      // Wait a moment for monitors to process and transaction to be received
      await new Promise(resolve => setTimeout(resolve, 2000));

      // Check balances after first transaction
      const account0After1 = await wallet.getAccountByIndex(index0).asPromise();
      const account1After1 = await wallet.getAccountByIndex(index1).asPromise();

      console.log("\n=== Balances After First Transaction ===");
      if (account0After1) {
        console.log(`Account 0 balance: ${account0After1.balance}`);
      }
      if (account1After1) {
        console.log(`Account 1 balance: ${account1After1.balance}`);
      }

      // Send from genesis account (index 0) to account 2
      const sendAmount2 = AttoAmount.from(AttoUnit.ATTO, "2000");

      console.log("\n=== Sending Second Transaction ===");
      console.log(`Sending ${sendAmount2} from index 0 to index 2...`);

      const sendTransaction2 = await wallet.sendByIndex(index0, address2, sendAmount2, null).asPromise();
      console.log(`Transaction hash: ${sendTransaction2.hash}`);
      console.log("Transaction sent successfully!");

      // Wait a moment for transaction to be processed
      await new Promise(resolve => setTimeout(resolve, 1000));

      // Check final balances
      const account0Final = await wallet.getAccountByIndex(index0).asPromise();
      const account1Final = await wallet.getAccountByIndex(index1).asPromise();
      const account2Final = await wallet.getAccountByIndex(index2).asPromise();

      console.log("\n=== Final Balances ===");
      if (account0Final) {
        console.log(`Account 0 balance: ${account0Final.balance}`);
        console.log(`Account 0 height: ${account0Final.height}`);
      }
      if (account1Final) {
        console.log(`Account 1 balance: ${account1Final.balance}`);
        console.log(`Account 1 height: ${account1Final.height}`);
      }
      if (account2Final) {
        console.log(`Account 2 balance: ${account2Final.balance}`);
        console.log(`Account 2 height: ${account2Final.height}`);
      }

      console.log("\n=== Demo completed successfully! ===");

    } finally {
      // Clean up resources
      nodeMock.close();
      workerMock.close();
      console.log("Mock servers stopped");
    }
  } catch (error) {
    console.error("Error:", error);
    process.exit(1);
  }
}

main();
