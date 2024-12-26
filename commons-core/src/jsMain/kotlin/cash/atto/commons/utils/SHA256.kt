@file:JsModule("@stablelib/sha256")
@file:JsNonModule

package cash.atto.commons.utils

import org.khronos.webgl.Uint8Array

external class SHA256 {
    fun update(
        data: Uint8Array,
        dataLength: Int = definedExternally,
    ): SHA256

    fun digest(): Uint8Array
}
