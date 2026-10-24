package cash.atto.commons

internal actual fun normalizeMnemonicNfkd(value: String): String = value.asDynamic().normalize("NFKD").unsafeCast<String>()
