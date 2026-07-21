@file:JvmName("AttoPublicKeys")

package cash.atto.commons

import kotlinx.coroutines.runBlocking

fun AttoPrivateKey.toPublicKeyBlocking(): AttoPublicKey = runBlocking { toPublicKey() }
