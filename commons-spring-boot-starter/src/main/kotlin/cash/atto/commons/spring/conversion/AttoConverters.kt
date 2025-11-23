package cash.atto.commons.spring.conversion

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoVersion
import cash.atto.commons.AttoWork
import cash.atto.commons.toAtto
import cash.atto.commons.toBigInteger
import cash.atto.commons.toBuffer
import cash.atto.commons.toJavaInstant
import cash.atto.commons.toULong
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.springframework.core.convert.converter.Converter
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

object AttoConverters {
    /**
     * NOTE: We intentionally use **anonymous classes** instead of SAM lambdas for Converter<S, T>.
     *
     * Spring Data’s CustomConversions inspects each converter’s declared generic types (S and T).
     * With lambdas the generated class erases those type arguments, so CustomConversions cannot
     * resolve them and fails with:
     *   "Couldn't resolve type arguments for class ...$$Lambda/..."
     *
     * Using anonymous classes preserves the generic signatures at runtime, allowing
     * R2dbcCustomConversions (and friends) to register these converters correctly.
     */
    @JvmField
    val all: List<Converter<*, *>> =
        listOf(
            // AttoAmount <-> BigInteger / String
            object : Converter<AttoAmount, BigInteger> {
                override fun convert(source: AttoAmount) = source.raw.toBigInteger()
            },
            object : Converter<BigInteger, AttoAmount> {
                override fun convert(source: BigInteger) = AttoAmount(source.toULong())
            },
            object : Converter<AttoAmount, String> {
                override fun convert(source: AttoAmount) = source.raw.toString()
            },
            object : Converter<String, AttoAmount> {
                override fun convert(source: String) = AttoAmount(source.toULong())
            },
            // AttoBlock <-> ByteArray
            object : Converter<AttoBlock, ByteArray> {
                override fun convert(source: AttoBlock) = source.toBuffer().readByteArray()
            },
            object : Converter<ByteArray, AttoBlock> {
                override fun convert(source: ByteArray) = AttoBlock.fromBuffer(source.toBuffer())!!
            },
            // AttoHash <-> ByteArray / String
            object : Converter<String, AttoHash> {
                override fun convert(source: String) = AttoHash.parse(source)
            },
            object : Converter<AttoHash, ByteArray> {
                override fun convert(source: AttoHash) = source.value
            },
            object : Converter<ByteArray, AttoHash> {
                override fun convert(source: ByteArray) = AttoHash(source)
            },
            // AttoHeight <-> BigInteger / BigDecimal / String
            object : Converter<AttoHeight, BigInteger> {
                override fun convert(source: AttoHeight) = source.value.toBigInteger()
            },
            object : Converter<BigInteger, AttoHeight> {
                override fun convert(source: BigInteger) = AttoHeight(source.toULong())
            },
            object : Converter<BigDecimal, AttoHeight> {
                override fun convert(source: BigDecimal) = AttoHeight(source.toBigInteger().toULong())
            },
            object : Converter<AttoHeight, String> {
                override fun convert(source: AttoHeight) = source.value.toString()
            },
            object : Converter<String, AttoHeight> {
                override fun convert(source: String) = AttoHeight(source.toULong())
            },
            // AttoPublicKey <-> ByteArray / String
            object : Converter<String, AttoPublicKey> {
                override fun convert(source: String) = AttoPublicKey.parse(source)
            },
            object : Converter<AttoPublicKey, ByteArray> {
                override fun convert(source: AttoPublicKey) = source.value
            },
            object : Converter<ByteArray, AttoPublicKey> {
                override fun convert(source: ByteArray) = AttoPublicKey(source)
            },
            // AttoSignature <-> ByteArray / String
            object : Converter<String, AttoSignature> {
                override fun convert(source: String) = AttoSignature.parse(source)
            },
            object : Converter<AttoSignature, ByteArray> {
                override fun convert(source: AttoSignature) = source.value
            },
            object : Converter<ByteArray, AttoSignature> {
                override fun convert(source: ByteArray) = AttoSignature(source)
            },
            // AttoVersion <-> Short / Integer
            object : Converter<AttoVersion, Short> {
                override fun convert(source: AttoVersion) = source.value.toShort()
            },
            object : Converter<Short, AttoVersion> {
                override fun convert(source: Short) = AttoVersion(source.toUShort())
            },
            object : Converter<Integer, AttoVersion> {
                override fun convert(source: Integer) = AttoVersion(source.toShort().toUShort())
            },
            // AttoWork <-> ByteArray / String
            object : Converter<String, AttoWork> {
                override fun convert(source: String) = AttoWork.parse(source)
            },
            object : Converter<AttoWork, ByteArray> {
                override fun convert(source: AttoWork) = source.value
            },
            object : Converter<ByteArray, AttoWork> {
                override fun convert(source: ByteArray) = AttoWork(source)
            },
            // AttoAddress <-> String
            object : Converter<String, AttoAddress> {
                override fun convert(source: String): AttoAddress {
                    return AttoAddress.parse(source)
                }
            },
            object : Converter<AttoAddress, String> {
                override fun convert(source: AttoAddress) = source.toString()
            },
            // Buffer <-> ByteArray
            object : Converter<Buffer, ByteArray> {
                override fun convert(source: Buffer) = source.readByteArray()
            },
            object : Converter<ByteArray, Buffer> {
                override fun convert(source: ByteArray) = source.toBuffer()
            },
            // ULong <-> BigInteger
            object : Converter<ULong, BigInteger> {
                override fun convert(source: ULong) = source.toBigInteger()
            },
            object : Converter<BigInteger, ULong> {
                override fun convert(source: BigInteger) = source.toULong()
            },
            // UShort <-> Short
            object : Converter<UShort, Short> {
                override fun convert(source: UShort) = source.toShort()
            },
            object : Converter<Short, UShort> {
                override fun convert(source: Short) = source.toUShort()
            },
            // ZonedDateTime <-> Instant (UTC)
            object : Converter<Instant, ZonedDateTime> {
                override fun convert(source: Instant) = source.atZone(ZoneOffset.UTC)
            },
            object : Converter<ZonedDateTime, Instant> {
                override fun convert(source: ZonedDateTime) = source.toInstant()
            },
            // AttoInstant <-> Instant (UTC)
            object : Converter<AttoInstant, ZonedDateTime> {
                override fun convert(source: AttoInstant) = source.toJavaInstant().atZone(ZoneOffset.UTC)
            },
            object : Converter<ZonedDateTime, AttoInstant> {
                override fun convert(source: ZonedDateTime) = source.toInstant().toAtto()
            },
        )
}
