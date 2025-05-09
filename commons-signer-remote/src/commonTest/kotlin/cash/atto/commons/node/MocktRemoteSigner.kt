package cash.atto.commons.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.node.HttpSignerRemote.BlockSignatureRequest
import cash.atto.commons.node.HttpSignerRemote.ChallengeSignatureRequest
import cash.atto.commons.node.HttpSignerRemote.PublicKeyResponse
import cash.atto.commons.node.HttpSignerRemote.SignatureResponse
import cash.atto.commons.node.HttpSignerRemote.VoteSignatureRequest
import cash.atto.commons.toSigner
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

class MocktRemoteSigner(
    port: Int,
) {
    val signer = AttoPrivateKey.generate().toSigner()

    val server =
        embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                json()
            }

            install(StatusPages) {
                exception<IllegalArgumentException> { call, cause ->
                    call.respond(HttpStatusCode.BadRequest, cause.localizedMessage)
                }
            }

            routing {
                get("/public-keys") {
                    val response = PublicKeyResponse(signer.publicKey)
                    call.respond(response)
                }

                post("/blocks") {
                    val request = call.request.call.receive<BlockSignatureRequest>()
                    val signature = signer.sign(request.target)
                    call.respond(SignatureResponse(signature))
                }

                post("/votes") {
                    val request = call.request.call.receive<VoteSignatureRequest>()
                    val signature = signer.sign(request.target)
                    call.respond(SignatureResponse(signature))
                }

                post("/challenges") {
                    val request = call.request.call.receive<ChallengeSignatureRequest>()
                    val signature = signer.sign(request.target, request.timestamp)
                    call.respond(SignatureResponse(signature))
                }
            }
        }

    fun start() {
        server.start()
    }

    fun stop() {
        server.stop()
    }
}
