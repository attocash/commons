@file:JvmName("AttoMnemonics")

package cash.atto.commons

import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import kotlin.jvm.JvmName

internal actual suspend fun checksum(entropy: ByteArray): Byte {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(entropy, 0, 32)

    val checksum = digest.digest()
    return checksum[0]
}

@JvmName("fromWordsBlocking")
fun mnemonicFromWordsBlocking(words: List<String>): AttoMnemonic = runBlocking { AttoMnemonic.fromWords(words) }

@JvmName("fromPhraseBlocking")
fun mnemonicFromPhraseBlocking(phrase: String): AttoMnemonic = runBlocking { AttoMnemonic.fromPhrase(phrase) }

@JvmName("fromEntropyBlocking")
fun mnemonicFromEntropyBlocking(entropy: ByteArray): AttoMnemonic = runBlocking { AttoMnemonic.fromEntropy(entropy) }

@JvmName("generateBlocking")
fun generateMnemonicBlocking(): AttoMnemonic = runBlocking { AttoMnemonic.generate() }
