@file:JsModule("@stablelib/hmac")
@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.utils

import org.khronos.webgl.Uint8Array

external class HMAC(
    hash: JsAny,
    key: Uint8Array,
) {
    fun update(data: Uint8Array)

    fun digest(): Uint8Array
}
