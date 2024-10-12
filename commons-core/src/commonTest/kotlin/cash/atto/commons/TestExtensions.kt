package cash.atto.commons

fun String.compactJson(): String = this.replace("\\s+".toRegex(), "")
