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
import kotlin.math.pow

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

fun AttoWork.Companion.getThreshold(
    network: AttoNetwork,
    timestamp: Instant,
): ULong {
    if (timestamp < INITIAL_INSTANT) {
        throw IllegalArgumentException("Timestamp($timestamp) lower than initialInstant(${AttoNetwork.INITIAL_INSTANT})")
    }

    return thresholdCache[network]!![timestamp.toLocalDateTime(TimeZone.UTC).year]!!
}

fun AttoWork.Companion.isValid(
    threshold: ULong,
    target: ByteArray,
    work: ByteArray,
): Boolean {
    val difficult = AttoHasher.hash(8, work, target).toULong()
    return difficult <= threshold
}

fun AttoWork.Companion.isValid(
    network: AttoNetwork,
    timestamp: Instant,
    target: ByteArray,
    work: ByteArray,
): Boolean {
    if (timestamp < INITIAL_INSTANT) {
        return false
    }
    return isValid(AttoWork.getThreshold(network, timestamp), target, work)
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

        fun parse(value: String): AttoWork {
            return AttoWork(value.fromHexToByteArray())
        }
    }

    init {
        value.checkLength(SIZE)
    }

    fun isValid(block: AttoBlock): Boolean {
        if (block is AttoOpenBlock) {
            return isValid(block.network, block.timestamp, block.publicKey.value, value)
        }
        block as PreviousSupport
        return isValid(block.network, block.timestamp, block.previous.value, value)
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
