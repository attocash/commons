package cash.atto.commons

data class AttoChallenge(val value: ByteArray) {
    init {
        require(value.size >= 64) { "Challenge should have at least 64 bytes" }

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AttoChallenge

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}
