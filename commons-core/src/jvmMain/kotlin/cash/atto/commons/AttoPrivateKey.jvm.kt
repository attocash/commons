@file:JvmName("AttoPrivateKeys")

package cash.atto.commons

import kotlinx.coroutines.runBlocking
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.jvm.JvmName

internal object AttoPrivateKeyHolder

internal actual suspend fun hmacSha512(
    secretKey: ByteArray,
    data: ByteArray,
): ByteArray =
    Mac
        .getInstance("HmacSHA512")
        .apply { init(SecretKeySpec(secretKey, "HmacSHA512")) }
        .doFinal(data)

fun AttoSeed.toPrivateKeyBlocking(index: AttoKeyIndex): AttoPrivateKey = runBlocking { toPrivateKey(index) }

fun AttoSeed.toPrivateKeyBlocking(index: UInt): AttoPrivateKey = runBlocking { toPrivateKey(index) }

fun AttoSeed.toPrivateKeyBlocking(index: Int): AttoPrivateKey = runBlocking { toPrivateKey(index) }
