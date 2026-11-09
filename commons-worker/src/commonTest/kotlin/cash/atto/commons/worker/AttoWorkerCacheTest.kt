package cash.atto.commons.worker

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoVersion
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AttoWorkerCacheTest {
    @Test
    fun `close cancels pending work and prevents new calculations`() =
        runTest {
            // Given
            val target = AttoWorkTarget(ByteArray(32))
            var calls = 0
            var cancelled = false
            var closed = false
            val delegate =
                object : AttoWorker {
                    override suspend fun work(
                        threshold: ULong,
                        target: AttoWorkTarget,
                    ): AttoWork {
                        calls++
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled = true
                        }
                    }

                    override fun close() {
                        closed = true
                    }
                }
            val worker = delegate.cached(StandardTestDispatcher(testScheduler))
            val request = async { worker.work(ULong.MAX_VALUE, target) }
            runCurrent()
            assertEquals(1, calls)

            // When
            worker.close()
            runCurrent()

            // Then
            assertTrue(request.isCancelled)
            assertTrue(cancelled)
            assertTrue(closed)
            assertFailsWith<kotlinx.coroutines.CancellationException> {
                worker.work(ULong.MAX_VALUE, target)
            }
            assertEquals(1, calls)
        }

    @Test
    fun `coalesces pending target work and consumes the cached result`() =
        runTest {
            // Given
            val target = AttoWorkTarget(ByteArray(32))
            val result = AttoWork(ByteArray(8))
            val ready = CompletableDeferred<Unit>()
            var calls = 0
            val delegate =
                object : AttoWorker {
                    override suspend fun work(
                        threshold: ULong,
                        target: AttoWorkTarget,
                    ): AttoWork {
                        calls++
                        ready.await()
                        return result
                    }

                    override fun close() {}
                }
            val worker = delegate.cached(StandardTestDispatcher(testScheduler))
            try {
                // When
                val first = async { worker.work(ULong.MAX_VALUE, target) }
                val second = async { worker.work(ULong.MAX_VALUE, target) }
                runCurrent()

                // Then
                assertEquals(1, calls)
                ready.complete(Unit)
                assertEquals(result, first.await())
                assertEquals(result, second.await())
                assertEquals(result, worker.work(ULong.MAX_VALUE, target))
                assertEquals(2, calls)
            } finally {
                worker.close()
            }
        }

    @Test
    fun `completed waiters cannot evict a replacement calculation for the same target`() =
        runTest {
            // Given
            val target = AttoWorkTarget(ByteArray(32))
            val firstResult = AttoWork(ByteArray(8))
            val replacementResult = AttoWork(ByteArray(8) { 1 })
            val releaseFirst = CompletableDeferred<Unit>()
            val releaseReplacement = CompletableDeferred<Unit>()
            var calls = 0
            val delegate =
                object : AttoWorker {
                    override suspend fun work(
                        threshold: ULong,
                        target: AttoWorkTarget,
                    ): AttoWork =
                        if (++calls == 1) {
                            releaseFirst.await()
                            firstResult
                        } else {
                            releaseReplacement.await()
                            replacementResult
                        }

                    override fun close() {}
                }
            val worker = delegate.cached(StandardTestDispatcher(testScheduler))
            val requests = mutableListOf<Deferred<AttoWork>>()
            var replacement: Deferred<AttoWork>? = null
            try {
                val first =
                    async(UnconfinedTestDispatcher(testScheduler)) {
                        val result = worker.work(ULong.MAX_VALUE, target)
                        // Start the replacement before the other completed waiter resumes.
                        replacement =
                            backgroundScope
                                .async(start = CoroutineStart.UNDISPATCHED) {
                                    worker.work(ULong.MAX_VALUE, target)
                                }.also { requests.add(it) }
                        result
                    }.also { requests.add(it) }
                val oldWaiter = async { worker.work(ULong.MAX_VALUE, target) }.also { requests.add(it) }
                runCurrent()
                assertEquals(1, calls)

                // When
                releaseFirst.complete(Unit)
                runCurrent()
                assertEquals(2, calls)
                val newWaiter = async { worker.work(ULong.MAX_VALUE, target) }.also { requests.add(it) }
                runCurrent()

                // Then
                assertEquals(2, calls)
                assertSame(firstResult, first.await())
                assertSame(firstResult, oldWaiter.await())
                releaseReplacement.complete(Unit)
                assertSame(replacementResult, replacement!!.await())
                assertSame(replacementResult, newWaiter.await())
            } finally {
                worker.close()
                requests.forEach { it.cancelAndJoin() }
            }
        }

    @Test
    fun `revalidates network work without falling back to threshold requests`() =
        runTest {
            // Given
            val network = AttoNetwork.LOCAL
            val timestamp = AttoNetwork.INITIAL_INSTANT
            val target = AttoWorkTarget(ByteArray(32))
            val result = validWork(target)
            val invalid = invalidWork { AttoWork.isValid(network, timestamp, target, it.value) }
            var calls = 0
            val delegate =
                object : AttoWorker {
                    override suspend fun work(
                        threshold: ULong,
                        target: AttoWorkTarget,
                    ): AttoWork = error("Threshold work is unsupported")

                    override suspend fun work(
                        network: AttoNetwork,
                        timestamp: AttoInstant,
                        target: AttoWorkTarget,
                    ): AttoWork {
                        assertEquals(AttoNetwork.LOCAL, network)
                        assertEquals(AttoNetwork.INITIAL_INSTANT, timestamp)
                        assertEquals(AttoWorkTarget(ByteArray(32)), target)
                        calls++
                        return if (calls == 1) invalid else result
                    }

                    override fun close() {}
                }
            val worker = delegate.cached(StandardTestDispatcher(testScheduler))
            try {
                // When
                val actual = worker.work(network, timestamp, target)

                // Then
                assertEquals(result, actual)
                assertEquals(2, calls)
            } finally {
                worker.close()
            }
        }

    @Test
    fun `prefetches a block successor before retrying stale block work`() =
        runTest {
            // Given
            val address = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32)))
            val block =
                AttoOpenBlock(
                    network = AttoNetwork.LOCAL,
                    version = AttoVersion(0U),
                    address = address,
                    balance = AttoAmount(1UL),
                    timestamp = AttoNetwork.INITIAL_INSTANT,
                    sendHashAlgorithm = AttoAlgorithm.V1,
                    sendHash = AttoHash(ByteArray(32)),
                    representativeAddress = address,
                )
            val result = validWork(block.getTarget())
            val invalid = invalidWork { it.isValid(block) }
            val calls = mutableListOf<String>()
            var blockCalls = 0
            val delegate =
                object : AttoWorker {
                    override suspend fun work(
                        threshold: ULong,
                        target: AttoWorkTarget,
                    ): AttoWork = error("Threshold work is unsupported")

                    override suspend fun work(block: AttoBlock): AttoWork {
                        calls += "block"
                        return if (++blockCalls == 1) invalid else result
                    }

                    override suspend fun work(
                        network: AttoNetwork,
                        timestamp: AttoInstant,
                        target: AttoWorkTarget,
                    ): AttoWork {
                        assertEquals(AttoWorkTarget(block.hash.value), target)
                        calls += "prefetch"
                        return result
                    }

                    override fun close() {}
                }
            val worker = delegate.cached(StandardTestDispatcher(testScheduler))
            try {
                // When
                val actual = worker.work(block)
                runCurrent()

                // Then
                assertTrue(actual.isValid(block))
                assertEquals(listOf("block", "prefetch", "block"), calls)
            } finally {
                worker.close()
            }
        }

    private suspend fun validWork(target: AttoWorkTarget): AttoWork {
        val cpu = AttoWorker.cpu()
        return try {
            cpu.work(AttoNetwork.LOCAL, AttoNetwork.INITIAL_INSTANT, target)
        } finally {
            cpu.close()
        }
    }

    private fun invalidWork(isValid: (AttoWork) -> Boolean): AttoWork =
        generateSequence(0) { it + 1 }
            .map { value -> AttoWork(ByteArray(8) { index -> (value shr (index * 8)).toByte() }) }
            .first { !isValid(it) }
}
