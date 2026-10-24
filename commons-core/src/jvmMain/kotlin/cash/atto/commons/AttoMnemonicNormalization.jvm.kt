package cash.atto.commons

import java.text.Normalizer

internal actual fun normalizeMnemonicNfkd(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
