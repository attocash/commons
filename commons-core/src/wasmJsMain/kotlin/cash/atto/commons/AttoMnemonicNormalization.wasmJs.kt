@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package cash.atto.commons

internal actual fun normalizeMnemonicNfkd(value: String): String = normalizeNfkd(value.toJsString()).toString()

private fun normalizeNfkd(value: JsString): JsString = js("value.normalize('NFKD')")
