package cash.atto.commons.spring

import kotlinx.serialization.json.Json
import org.reactivestreams.Publisher
import org.springframework.core.ResolvableType
import org.springframework.http.MediaType
import org.springframework.http.codec.EncoderHttpMessageWriter
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

/** Register as a custom server codec with the application's JSON format to enable v2 stream heartbeats. */
class NdjsonHeartbeatHttpMessageWriter(
    json: Json,
    heartbeatInterval: Duration = Duration.ofSeconds(1),
) : EncoderHttpMessageWriter<Any>(NdjsonHeartbeatEncoder(json, heartbeatInterval)) {
    override fun canWrite(
        elementType: ResolvableType,
        mediaType: MediaType?,
    ): Boolean = mediaType != null && MediaType.APPLICATION_NDJSON.isCompatibleWith(mediaType) && super.canWrite(elementType, mediaType)

    override fun write(
        inputStream: Publisher<out Any>,
        actualType: ResolvableType,
        elementType: ResolvableType,
        mediaType: MediaType?,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        hints: Map<String, Any>,
    ): Mono<Void> {
        // The default writer buffers Mono bodies until completion. A v2 stream must flush its initial heartbeat too.
        val body =
            if (mediaType != null &&
                MediaType.APPLICATION_NDJSON.isCompatibleWith(mediaType) &&
                isV2StreamRequest(request)
            ) {
                Flux.from(inputStream)
            } else {
                inputStream
            }
        return super.write(body, actualType, elementType, mediaType, request, response, hints)
    }
}
