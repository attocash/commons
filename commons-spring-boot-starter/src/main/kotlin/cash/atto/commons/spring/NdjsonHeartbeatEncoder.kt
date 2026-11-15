package cash.atto.commons.spring

import kotlinx.serialization.json.Json
import org.reactivestreams.Publisher
import org.springframework.core.ResolvableType
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.codec.HttpMessageEncoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.util.MimeType
import reactor.core.publisher.Flux
import java.time.Duration

/** Encodes v2 NDJSON responses with blank heartbeat lines between complete records. */
class NdjsonHeartbeatEncoder(
    json: Json,
    private val heartbeatInterval: Duration = Duration.ofSeconds(1),
) : KotlinSerializationJsonEncoder(json),
    HttpMessageEncoder<Any> {
    init {
        require(!heartbeatInterval.isNegative && !heartbeatInterval.isZero) { "Heartbeat interval must be positive" }
    }

    override fun getStreamingMediaTypes(): List<MediaType> = listOf(MediaType.APPLICATION_NDJSON)

    override fun getEncodeHints(
        actualType: ResolvableType,
        elementType: ResolvableType,
        mediaType: MediaType?,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Map<String, Any> =
        mapOf(
            HEARTBEAT_HINT to
                isV2StreamRequest(request),
        )

    override fun encode(
        inputStream: Publisher<*>,
        bufferFactory: DataBufferFactory,
        elementType: ResolvableType,
        mimeType: MimeType?,
        hints: Map<String, Any>?,
    ): Flux<DataBuffer> {
        if (mimeType == null ||
            !MediaType.APPLICATION_NDJSON.isCompatibleWith(mimeType) ||
            hints?.get(HEARTBEAT_HINT) != true
        ) {
            return super.encode(inputStream, bufferFactory, elementType, mimeType, hints)
        }

        // Share domain values, not pooled buffers: cancellation can discard queued values safely.
        return Flux
            .from(inputStream)
            .publish { values ->
                val heartbeats =
                    Flux
                        .interval(heartbeatInterval)
                        .onBackpressureDrop()
                        .map { HEARTBEAT }
                        .takeUntilOther(values.ignoreElements().onErrorComplete())
                Flux.merge(1, values, heartbeats)
            }.map { value ->
                if (value === HEARTBEAT) {
                    bufferFactory.wrap(NEWLINE)
                } else {
                    // The inherited method joins the serialized record and newline into one buffer.
                    encodeStreamingValue(value, bufferFactory, elementType, mimeType, hints, EMPTY_BYTES, NEWLINE)
                }
            }.doOnDiscard(DataBuffer::class.java, DataBufferUtils::release)
    }

    private companion object {
        const val HEARTBEAT_HINT = "cash.atto.commons.spring.NdjsonHeartbeatEncoder.enabled"
        val HEARTBEAT = Any()
        val NEWLINE = byteArrayOf('\n'.code.toByte())
    }
}

internal fun isV2StreamRequest(request: ServerHttpRequest): Boolean {
    val path = request.path.pathWithinApplication().value()
    return (request.method == HttpMethod.POST && path == "/v2/transactions") ||
        (path.startsWith("/v2/") && path.endsWith("/stream"))
}
