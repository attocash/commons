@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cash.atto.commons

import cash.atto.commons.utils.getSubtleCryptoInstance
import org.khronos.webgl.Uint8Array

internal actual suspend fun checksum(entropy: ByteArray): Byte {
    val digest =
        getSubtleCryptoInstance()
            .digest(sha256Algorithm(), entropy.copyOfRange(0, 32).toUint8Array())
            .await()

    return Uint8Array(digest).toByteArray()[0]
}

private fun sha256Algorithm(): JsAny = js("""({ "name": "SHA-256" })""")
