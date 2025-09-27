@file:JsModule("@stablelib/sha512")
@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.utils

@JsName("SHA512")
external val SHA512Algorithm: JsAny
