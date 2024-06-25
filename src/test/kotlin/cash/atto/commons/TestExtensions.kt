package cash.atto.commons

fun String.compactJson(): String {
    return this.replace("\\s+".toRegex(), "")
}
