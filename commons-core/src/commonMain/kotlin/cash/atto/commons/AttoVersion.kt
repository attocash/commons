package cash.atto.commons

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline
@Serializable
value class AttoVersion(
    val value: UShort,
) : Comparable<AttoVersion> {
    override operator fun compareTo(other: AttoVersion): Int = value.compareTo(other.value)

    operator fun plus(uShort: UShort): AttoVersion = AttoVersion((value + uShort).toUShort())

    operator fun plus(uInt: UInt): AttoVersion = AttoVersion((value + uInt).toUShort())

    operator fun minus(uShort: UShort): AttoVersion = AttoVersion((value - uShort).toUShort())

    operator fun minus(uInt: UInt): AttoVersion = AttoVersion((value - uInt).toUShort())

    override fun toString(): String {
        return value.toString()
    }
}

fun AttoVersion.max(anotherVersion: AttoVersion): AttoVersion {
    return if (value > anotherVersion.value) {
        return this
    } else {
        anotherVersion
    }
}

fun UShort.toAttoVersion(): AttoVersion = AttoVersion(this)

fun UInt.toAttoVersion(): AttoVersion = this.toUShort().toAttoVersion()
