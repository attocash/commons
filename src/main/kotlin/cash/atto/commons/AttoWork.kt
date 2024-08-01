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

internal fun getThreshold(
    network: AttoNetwork,
    timestamp: Instant,
): ULong {
    if (timestamp < INITIAL_INSTANT) {
        throw IllegalArgumentException("Timestamp($timestamp) lower than initialInstant(${AttoNetwork.INITIAL_INSTANT})")
    }

    return thresholdCache[network]!![timestamp.toLocalDateTime(TimeZone.UTC).year]!!
}

internal fun isValid(
    threshold: ULong,
    target: ByteArray,
    work: ByteArray,
): Boolean {
    val difficult = hashRaw(8, work, target).toULong()
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

    fun isValid(block: AttoOpenBlock): Boolean {
        return isValid(block.network, block.timestamp, block.publicKey.value, value)
    }

    fun <T> isValid(block: T): Boolean where T : PreviousSupport, T : AttoBlock {
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
