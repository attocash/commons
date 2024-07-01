package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.DOUBLING_PERIOD
import cash.atto.commons.AttoNetwork.Companion.INITIAL_DATE
import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import cash.atto.commons.AttoNetwork.Companion.INITIAL_LIVE_THRESHOLD
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.stream.Stream
import kotlin.math.pow
import kotlin.random.Random

private fun initializeThresholdCache(): Map<AttoNetwork, Map<Int, ULong>> {
    val cache = mutableMapOf<AttoNetwork, Map<Int, ULong>>()
    for (network in AttoNetwork.entries) {
        val yearMap = mutableMapOf<Int, ULong>()
        for (year in INITIAL_DATE.year..(
            Clock
                .System
                .now()
                .toLocalDateTime(TimeZone.UTC)
                .year + 10
        )) {
            val decreaseFactor = (2.0).pow((year - INITIAL_DATE.year) / DOUBLING_PERIOD).toULong()
            val initialThreshold = INITIAL_LIVE_THRESHOLD * network.thresholdIncreaseFactor

            yearMap[year] = initialThreshold / decreaseFactor
        }
        cache[network] = yearMap.toMap()
    }
    return cache.toMap()
}

private val thresholdCache = initializeThresholdCache()

internal fun getThreshold(
    network: AttoNetwork,
    timestamp: Instant,
): ULong {
    if (timestamp < INITIAL_INSTANT) {
        throw IllegalArgumentException("Timestamp($timestamp) lower than initialInstant(${AttoNetwork.INITIAL_INSTANT})")
    }

    return thresholdCache[network]!![timestamp.toLocalDateTime(TimeZone.UTC).year]!!
}

private fun isValid(
    threshold: ULong,
    hash: ByteArray,
    work: ByteArray,
): Boolean {
    val difficult = hashRaw(8, work, hash).toULong()
    return difficult <= threshold
}

internal fun isValid(
    network: AttoNetwork,
    timestamp: Instant,
    hash: ByteArray,
    work: ByteArray,
): Boolean {
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
    val hash: ByteArray,
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

@Serializable(with = AttoWorkSerializer::class)
data class AttoWork(
    val value: ByteArray,
) {
    companion object {
        const val SIZE = 8

        fun threshold(
            network: AttoNetwork,
            timestamp: Instant,
        ): ULong {
            return getThreshold(network, timestamp)
        }

        fun isValid(
            block: AttoOpenBlock,
            work: AttoWork,
        ): Boolean {
            return isValid(block.network, block.timestamp, block.publicKey.value, work.value)
        }

        fun <T> isValid(
            block: T,
            work: AttoWork,
        ): Boolean where T : PreviousSupport, T : AttoBlock {
            return isValid(block.network, block.timestamp, block.previous.value, work.value)
        }

        fun work(
            threshold: ULong,
            hash: ByteArray,
        ): AttoWork {
            val controller = WorkerController()
            return Stream
                .generate { Worker(controller, threshold, hash) }
                .takeWhile { controller.isEmpty() }
                .parallel()
                .peek { it.work() }
                .map { controller.get() }
                .filter { it != null }
                .findAny()
                .get()
        }

        fun work(block: AttoOpenBlock): AttoWork {
            return work(block.network, block.timestamp, block.publicKey.value)
        }

        fun <T> work(block: T): AttoWork where T : PreviousSupport, T : AttoBlock {
            return work(block.network, block.timestamp, block.previous.value)
        }

        fun parse(value: String): AttoWork {
            return AttoWork(value.fromHexToByteArray())
        }

        fun work(
            network: AttoNetwork,
            timestamp: Instant,
            hash: ByteArray,
        ): AttoWork {
            val threshold = getThreshold(network, timestamp)
            return work(threshold, hash)
        }
    }

    init {
        value.checkLength(SIZE)
    }

    fun isValid(block: AttoOpenBlock): Boolean {
        return isValid(block, this)
    }

    fun <T> isValid(block: T): Boolean where T : PreviousSupport, T : AttoBlock {
        return isValid(block, this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttoWork) return false

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = value.toHex()
}

object AttoWorkSerializer : KSerializer<AttoWork> {
    override val descriptor = PrimitiveSerialDescriptor("AttoWork", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoWork,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoWork = AttoWork.parse(decoder.decodeString())
}
