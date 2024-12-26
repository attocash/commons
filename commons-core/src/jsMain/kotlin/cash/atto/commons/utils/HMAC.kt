@file:JsModule("@stablelib/hmac")
@file:JsNonModule

package cash.atto.commons.utils

import org.khronos.webgl.Uint8Array

external class HMAC(
    hash: dynamic,
    key: Uint8Array,
) {
    fun update(data: Uint8Array)

    fun digest(): Uint8Array
}
