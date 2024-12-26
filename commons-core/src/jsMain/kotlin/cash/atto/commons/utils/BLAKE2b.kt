@file:JsModule("@stablelib/blake2b")
@file:JsNonModule

package cash.atto.commons.utils

import org.khronos.webgl.Uint8Array


external class BLAKE2b(
    digestLength: Int = definedExternally,
    config: dynamic = definedExternally
) {

    fun update(data: Uint8Array): BLAKE2b
    fun digest(): Uint8Array
}
