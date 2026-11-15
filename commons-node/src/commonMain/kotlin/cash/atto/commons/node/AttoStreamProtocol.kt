package cash.atto.commons.node

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@Serializable
enum class AttoStreamMethod { GET, POST }

@Serializable
data class AttoStreamRequest(
    val method: AttoStreamMethod,
    val path: String,
    val body: JsonElement? = null,
)

@Serializable
enum class AttoStreamCommandType {
    @SerialName("subscribe")
    SUBSCRIBE,

    @SerialName("update")
    UPDATE,

    @SerialName("ack")
    ACK,

    @SerialName("seal")
    SEAL,
}

@Serializable
data class AttoStreamCommand(
    val type: AttoStreamCommandType,
    val request: AttoStreamRequest? = null,
    val mutable: Boolean = false,
    val sequence: String? = null,
)

@Serializable
enum class AttoStreamResponseType {
    @SerialName("subscribed")
    SUBSCRIBED,

    @SerialName("updated")
    UPDATED,

    @SerialName("data")
    DATA,

    @SerialName("heartbeat")
    HEARTBEAT,

    @SerialName("complete")
    COMPLETE,

    @SerialName("error")
    ERROR,
}

@Serializable
data class AttoStreamResponse(
    val type: AttoStreamResponseType,
    val data: JsonElement? = null,
    val status: Int? = null,
    val message: String? = null,
    val sequence: String? = null,
    val terminal: Boolean = true,
)

object AttoStreamProtocol {
    const val PATH: String = "/v2/stream"
    const val CREDIT_WINDOW: Int = 32
    const val MAX_RECORD_CHARS: Int = 2_000
    private val json = Json

    fun encodeCommand(command: AttoStreamCommand): String = json.encodeToString(command)

    fun decodeCommand(value: String): AttoStreamCommand = json.decodeFromString(value)

    fun encodeRequest(request: AttoStreamRequest): String = json.encodeToString(request)

    fun decodeRequest(value: String): AttoStreamRequest = json.decodeFromString(value)

    fun encodeResponse(response: AttoStreamResponse): String = json.encodeToString(response)

    fun decodeResponse(value: String): AttoStreamResponse = json.decodeFromString(value)
}
