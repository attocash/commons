package cash.atto.commons

import java.math.BigInteger

fun ULong.toBigInteger(): BigInteger = BigInteger(this.toString())

fun BigInteger.toULong(): ULong = this.toString().toULong()
