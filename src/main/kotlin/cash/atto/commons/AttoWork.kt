package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.DOUBLING_PERIOD
import cash.atto.commons.AttoNetwork.Companion.INITIAL_DATE
import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import cash.atto.commons.AttoNetwork.Companion.INITIAL_LIVE_THRESHOLD
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.stream.Stream
import kotlin.math.pow
import kotlin.random.Random

private fun initializeThresholdCache(): Map<AttoNetwork, Map<Int, ULong>> {
    val cache = mutableMapOf<AttoNetwork, Map<Int, ULong>>()
    for (network in AttoNetwork.entries) {
        val yearMap = mutableMapOf<Int, ULong>()
        for (year in INITIAL_DATE.year..(LocalDateTime.now().year + 10)) {
            val decreaseFactor = (2.0).pow((year - INITIAL_DATE.year) / DOUBLING_PERIOD).toULong()
            val initialThreshold = INITIAL_LIVE_THRESHOLD * network.thresholdIncreaseFactor

            yearMap[year] = initialThreshold / decreaseFactor
        }
        cache[network] = yearMap.toMap()
    }
    return cache.toMap()
}

private val thresholdCache = initializeThresholdCache()
internal fun getThreshold(network: AttoNetwork, timestamp: Instant): ULong {
    if (timestamp < INITIAL_INSTANT) {
        throw IllegalArgumentException("Timestamp($timestamp) lower than initialInstant(${AttoNetwork.INITIAL_INSTANT})")
    }

    return thresholdCache[network]!![timestamp.atZone(ZoneOffset.UTC).year]!!
}

private fun isValid(threshold: ULong, hash: ByteArray, work: ByteArray): Boolean {
    val difficult = hashRaw(8, work, hash).toULong()
    return difficult <= threshold
}

private fun isValid(network: AttoNetwork, timestamp: Instant, hash: ByteArray, work: ByteArray): Boolean {
    if (timestamp < INITIAL_INSTANT) {
        return false
    }
    return isValid(getThreshold(network, timestamp), hash, work)
}

private class WorkerController {
    private var work: AttoWork? = null

    fun isEmpty(): Boolean {
        return work == null
    }

    fun add(work: ByteArray) {
        this.work = AttoWork(work)
    }

    fun get(): AttoWork? {
        return work
    }
}

private class Worker(
    val controller: WorkerController,
    val threshold: ULong,
    val hash: ByteArray
) {
    private val work = ByteArray(8)

    fun work() {
        while (true) {
            Random.nextBytes(work)
            for (i in work.indices) {
                val byte = work[i]
                for (b in -128..126) {
                    work[i] = b.toByte()
                    if (isValid(threshold, hash, work)) {
                        controller.add(work)
                    }
                    if (!controller.isEmpty()) {
                        return
                    }
                }
                work[i] = byte
            }
        }
    }
}

data class AttoWork(val value: ByteArray) {
    companion object {
        const val SIZE = 8

        fun threshold(network: AttoNetwork, timestamp: Instant): ULong {
            return getThreshold(network, timestamp)
        }

        fun isValid(network: AttoNetwork, timestamp: Instant, hash: AttoHash, work: AttoWork): Boolean {
            return isValid(network, timestamp, hash.value, work.value)
        }

        fun isValid(network: AttoNetwork, timestamp: Instant, publicKey: AttoPublicKey, work: AttoWork): Boolean {
            return isValid(network, timestamp, publicKey.value, work.value)
        }

        fun work(network: AttoNetwork, timestamp: Instant, hash: AttoHash): AttoWork {
            return work(network, timestamp, hash.value)
        }

        fun work(network: AttoNetwork, timestamp: Instant, publicKey: AttoPublicKey): AttoWork {
            return work(network, timestamp, publicKey.value)
        }

        fun work(threshold: ULong, hash: ByteArray): AttoWork {
            val controller = WorkerController()
            return Stream.generate { Worker(controller, threshold, hash) }
                .takeWhile { controller.isEmpty() }
                .parallel()
                .peek { it.work() }
                .map { controller.get() }
                .filter { it != null }
                .findAny()
                .get()
        }

        private fun work(network: AttoNetwork, timestamp: Instant, hash: ByteArray): AttoWork {
            val threshold = getThreshold(network, timestamp)
            return work(threshold, hash)
        }

        fun parse(value: String): AttoWork {
            return AttoWork(value.fromHexToByteArray())
        }
    }

    init {
        value.checkLength(SIZE)
    }

    fun isValid(network: AttoNetwork, timestamp: Instant, publicKey: AttoPublicKey): Boolean {
        return isValid(network, timestamp, publicKey, this)
    }

    fun isValid(network: AttoNetwork, timestamp: Instant, hash: AttoHash): Boolean {
        return isValid(network, timestamp, hash, this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttoWork

        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    override fun toString(): String {
        return value.toHex().lowercase()
    }
}