package cash.atto.commons.node

class AttoNodeStreamException(
    val status: Int,
    message: String,
) : IllegalStateException(message)
