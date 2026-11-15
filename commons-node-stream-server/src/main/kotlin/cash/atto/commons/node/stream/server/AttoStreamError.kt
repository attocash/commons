package cash.atto.commons.node.stream.server

/** A boundary-specific failure translated into the stream protocol's status and message. */
data class AttoStreamError(
    val status: Int,
    val message: String,
)

internal class AttoStreamRequestException(
    val reason: String,
) : IllegalArgumentException(reason)

internal fun badStreamRequest(message: String): AttoStreamRequestException = AttoStreamRequestException(message)
