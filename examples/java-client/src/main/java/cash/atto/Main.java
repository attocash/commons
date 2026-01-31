package cash.atto;

import cash.atto.commons.*;
import cash.atto.commons.node.*;
import cash.atto.commons.node.monitor.*;
import cash.atto.commons.wallet.AttoWalletAsync;
import cash.atto.commons.wallet.AttoWalletAsyncBuilder;
import cash.atto.commons.worker.AttoWorkerAsync;
import cash.atto.commons.worker.AttoWorkerAsyncBuilder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static cash.atto.commons.AttoKeyIndexes.toAttoIndex;

public class Main {
    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {
        AttoMnemonic mnemonic = AttoMnemonic.generate();
        AttoSeed seed = AttoSeeds.toSeedBlocking(mnemonic);

        // Generate private key for genesis account (index 0)
        AttoKeyIndex genesisIndex = AttoKeyIndexes.toAttoIndex(0);
        AttoPrivateKey genesisPrivateKey = AttoPrivateKeys.toPrivateKey(seed, genesisIndex);

        // Create and start AttoNodeMockAsync using its builder
        AttoNodeMockAsync nodeMock = new AttoNodeMockAsyncBuilder(genesisPrivateKey).build();
        nodeMock.start().get();
        System.out.println("Node mock started at: " + nodeMock.getBaseUrl());

        // Create and start AttoWorkerMockAsync using its builder
        AttoWorkerMockAsync workerMock = new AttoWorkerMockAsyncBuilder().build();
        workerMock.start().get();
        System.out.println("Worker mock started at: " + workerMock.getBaseUrl());

        try {
            // Create async client and worker using the mock server URLs
            AttoNodeClientAsync nodeClient = new AttoNodeClientAsyncBuilder(nodeMock.getBaseUrl()).build();
            AttoWorkerAsync worker = new AttoWorkerAsyncBuilder(workerMock.getBaseUrl()).build();

            // Create account monitor for auto-receive functionality
            AttoAccountMonitorAsync accountMonitor = new AttoAccountMonitorAsyncBuilder(nodeClient).build();

            // Create transaction monitor to track transactions
            AttoTransactionMonitorAsync transactionMonitor = new AttoTransactionMonitorAsyncBuilder(nodeClient, accountMonitor)
                .heightProvider(address -> {
                    // Start from height 2 for genesis (skip genesis block at height 1)
                    AttoAddress genesisAddress = AttoAddresses.toAddress(AttoPublicKeys.toPublicKey(genesisPrivateKey), AttoAlgorithm.V1);
                    if (address.equals(genesisAddress)) {
                        return CompletableFuture.completedFuture(AttoHeights.toAttoHeight(2));
                    }
                    return CompletableFuture.completedFuture(AttoHeight.MIN);
                })
                .build();

            // Create account entry monitor to track account entries
            AttoAccountEntryMonitorAsync accountEntryMonitor = new AttoAccountEntryMonitorAsyncBuilder(nodeClient, accountMonitor)
                .heightProvider(address -> {
                    // Start from height 2 for genesis (skip genesis block at height 1)
                    AttoAddress genesisAddress = AttoAddresses.toAddress(AttoPublicKeys.toPublicKey(genesisPrivateKey), AttoAlgorithm.V1);
                    if (address.equals(genesisAddress)) {
                        return CompletableFuture.completedFuture(AttoHeights.toAttoHeight(2));
                    }
                    return CompletableFuture.completedFuture(AttoHeight.MIN);
                })
                .build();

            // Create AttoWalletAsync using AttoWalletAsyncBuilder with auto-receive enabled
            AttoWalletAsync wallet = new AttoWalletAsyncBuilder(nodeClient, worker)
                .signerProvider(seed)
                .enableAutoReceiver(
                    accountMonitor,
                    AttoAmount.from(AttoUnit.ATTO, "1"),  // Minimum amount to auto-receive
                    java.time.Duration.ofSeconds(10),      // Retry interval
                    () -> {
                        // Default representative address provider
                        // Using a dummy address as default representative
                        byte[] dummyBytes = new byte[32];
                        return new AttoAddress(AttoAlgorithm.V1, new AttoPublicKey(dummyBytes));
                    }
                )
                .build();

            // Open genesis account (index 0) - this has the initial MAX balance
            AttoKeyIndex index0 = toAttoIndex(0);
            wallet.openAccount(index0).get();

            System.out.println("\n=== Genesis Account (Index 0) ===");
            AttoAddress address0 = wallet.getAddress(index0).get();
            System.out.println("Address: " + address0);

            AttoAccount account0Details = wallet.getAccount(index0).get();
            if (account0Details != null) {
                System.out.println("Initial Balance: " + account0Details.getBalance());
                System.out.println("Height: " + account0Details.getHeight());
            }

            // Open additional accounts
            AttoKeyIndex index1 = toAttoIndex(1);
            AttoKeyIndex index2 = toAttoIndex(2);

            wallet.openAccount(index1).get();
            wallet.openAccount(index2).get();

            System.out.println("\n=== Opened Additional Accounts ===");
            AttoAddress address1 = wallet.getAddress(index1).get();
            AttoAddress address2 = wallet.getAddress(index2).get();
            System.out.println("Account 1 address: " + address1);
            System.out.println("Account 2 address: " + address2);

            // Register transaction monitor to track all transactions
            System.out.println("\n=== Registering Transaction Monitor ===");
            AttoJob transactionJob = transactionMonitor.onTransaction(
                transaction -> {
                    System.out.println("Transaction monitor received: " + transaction.getHash());
                    return CompletableFuture.completedFuture(null);
                },
                error -> {
                    if (error != null) {
                        System.err.println("Transaction monitor error: " + error.getMessage());
                    }
                    return CompletableFuture.completedFuture(null);
                }
            );

            // Register account entry monitor to track all account entries
            System.out.println("=== Registering Account Entry Monitor ===");
            AttoJob accountEntryJob = accountEntryMonitor.onAccountEntry(
                entry -> {
                    System.out.println("Account entry monitor received: " + entry.getHash());
                    return CompletableFuture.completedFuture(null);
                },
                error -> {
                    if (error != null) {
                        System.err.println("Account entry monitor error: " + error.getMessage());
                    }
                    return CompletableFuture.completedFuture(null);
                }
            );

            // Send from genesis account (index 0) to account 1
            AttoAmount sendAmount1 = AttoAmount.from(AttoUnit.ATTO, "1000");

            System.out.println("\n=== Sending First Transaction ===");
            System.out.println("Sending " + sendAmount1 + " from index 0 to index 1...");

            AttoTransaction sendTransaction1 = wallet.send(index0, address1, sendAmount1, null).get();
            System.out.println("Transaction hash: " + sendTransaction1.getHash());
            System.out.println("Transaction sent successfully!");

            // Wait a moment for monitors to process and transaction to be received
            Thread.sleep(2000);

            // Check balances after first transaction
            AttoAccount account0After1 = wallet.getAccount(index0).get();
            AttoAccount account1After1 = wallet.getAccount(index1).get();

            System.out.println("\n=== Balances After First Transaction ===");
            if (account0After1 != null) {
                System.out.println("Account 0 balance: " + account0After1.getBalance());
            }
            if (account1After1 != null) {
                System.out.println("Account 1 balance: " + account1After1.getBalance());
            }

            // Send from genesis account (index 0) to account 2
            AttoAmount sendAmount2 = AttoAmount.from(AttoUnit.ATTO, "2000");

            System.out.println("\n=== Sending Second Transaction ===");
            System.out.println("Sending " + sendAmount2 + " from index 0 to index 2...");

            AttoTransaction sendTransaction2 = wallet.send(index0, address2, sendAmount2, null).get();
            System.out.println("Transaction hash: " + sendTransaction2.getHash());
            System.out.println("Transaction sent successfully!");

            // Wait a moment for transaction to be processed
            Thread.sleep(1000);

            // Check final balances
            AttoAccount account0Final = wallet.getAccount(index0).get();
            AttoAccount account1Final = wallet.getAccount(index1).get();
            AttoAccount account2Final = wallet.getAccount(index2).get();

            System.out.println("\n=== Final Balances ===");
            if (account0Final != null) {
                System.out.println("Account 0 balance: " + account0Final.getBalance());
                System.out.println("Account 0 height: " + account0Final.getHeight());
            }
            if (account1Final != null) {
                System.out.println("Account 1 balance: " + account1Final.getBalance());
                System.out.println("Account 1 height: " + account1Final.getHeight());
            }
            if (account2Final != null) {
                System.out.println("Account 2 balance: " + account2Final.getBalance());
                System.out.println("Account 2 height: " + account2Final.getHeight());
            }

            System.out.println("\n=== Demo completed successfully! ===");

        } finally {
            // Clean up resources
            nodeMock.close();
            workerMock.close();
            System.out.println("Mock servers stopped");
        }
    }
}
