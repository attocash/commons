package cash.atto.commons.node.stream.server

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/** The signature fixes one member's filters and ranges for as long as it is retained. */
data class AttoStreamMember(
    val signature: String,
    val stream: suspend () -> Flow<JsonElement>,
)
