@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons

import cash.atto.commons.AttoNetwork.Companion.DOUBLING_PERIOD
import cash.atto.commons.AttoNetwork.Companion.INITIAL_DATE
import cash.atto.commons.AttoNetwork.Companion.INITIAL_INSTANT
import cash.atto.commons.AttoNetwork.Companion.INITIAL_LIVE_THRESHOLD
import cash.atto.commons.utils.JsExportForJs
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmStatic
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
private fun initializeThresholdCache(): Map<AttoNetwork, Map<Int, ULong>> {
    val cache = mutableMapOf<AttoNetwork, Map<Int, ULong>>()
    for (network in AttoNetwork.validEntries) {
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

@JsExportForJs
@Serializable(with = AttoWorkAsStringSerializer::class)
data class AttoWork(
    val value: ByteArray,
) {
    companion object {
        const val SIZE = 8

        fun parse(value: String): AttoWork = AttoWork(value.fromHexToByteArray(SIZE))

        @OptIn(ExperimentalTime::class)
        @JsExport.Ignore
        @JvmStatic
        fun getThreshold(
            network: AttoNetwork,
            timestamp: AttoInstant,
        ): ULong {
            require(network != AttoNetwork.UNKNOWN) { "Atto network can't be UNKNOWN." }
            if (timestamp < INITIAL_INSTANT) {
                throw IllegalArgumentException("Timestamp($timestamp) lower than initialInstant(${AttoNetwork.INITIAL_INSTANT})")
            }

            return thresholdCache[network]!![timestamp.value.toLocalDateTime(TimeZone.UTC).year]!!
        }

        @JsExport.Ignore
        @JvmStatic
        fun isValid(
            threshold: ULong,
            target: ByteArray,
            work: ByteArray,
        ): Boolean {
            val difficult = AttoHasher.hash(8, work, target).toULong()
            return difficult <= threshold
        }

        @JsExport.Ignore
        @JvmStatic
        fun isValid(
            threshold: ULong,
            target: AttoWorkTarget,
            work: ByteArray,
        ): Boolean = isValid(threshold, target.value, work)

        @JsExport.Ignore
        @JvmStatic
        fun isValid(
            network: AttoNetwork,
            timestamp: AttoInstant,
            target: AttoWorkTarget,
            work: ByteArray,
        ): Boolean {
            if (network == AttoNetwork.UNKNOWN) {
                return false
            }
            if (timestamp < INITIAL_INSTANT) {
                return false
            }
            return isValid(getThreshold(network, timestamp), target, work)
        }
    }

    init {
        value.checkLength(SIZE)
    }

    fun isValid(block: AttoBlock): Boolean = isValid(block.network, block.timestamp, block.getTarget(), value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttoWork) return false

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = value.toHex()
}

@Deprecated(
    "Moved to AttoWork.getThreshold(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoWork.getThreshold(network, timestamp)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoWork.Companion.getThreshold(
    network: AttoNetwork,
    timestamp: AttoInstant,
): ULong = AttoWork.getThreshold(network, timestamp)

@OptIn(ExperimentalJsExport::class)
@JsExport.Ignore
@Deprecated(
    "Moved to AttoWork.isValid(); compatibility extension will be removed in 8.0.0",
    ReplaceWith("AttoWork.isValid(threshold, target, work)"),
    level = DeprecationLevel.WARNING,
)
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun AttoWork.Companion.isValid(
    threshold: ULong,
    target: ByteArray,
    work: ByteArray,
): Boolean = AttoWork.isValid(threshold, target, work)

object AttoWorkAsStringSerializer : KSerializer<AttoWork> {
    override val descriptor = PrimitiveSerialDescriptor("AttoWorkAsString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: AttoWork,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AttoWork = AttoWork.parse(decoder.decodeString())
}

object AttoWorkAsByteArraySerializer : KSerializer<AttoWork> {
    override val descriptor = PrimitiveSerialDescriptor("AttoWorkAsByteArray", PrimitiveKind.BYTE)

    override fun serialize(
        encoder: Encoder,
        value: AttoWork,
    ) {
        encoder.encodeSerializableValue(ByteArraySerializer(), value.value)
    }

    override fun deserialize(decoder: Decoder): AttoWork = AttoWork(decoder.decodeSerializableValue(ByteArraySerializer()))
}
