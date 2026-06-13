package cash.atto.commons.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoSignature
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
import kotlin.random.Random

class MocktRemoteSigner(
    port: Int,
    private val statusByPath: Map<String, HttpStatusCode> = emptyMap(),
    private val invalidSignatures: Boolean = false,
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
                    statusByPath["/public-keys"]?.let {
                        call.respond(it)
                        return@get
                    }
                    val response = PublicKeyResponse(signer.publicKey)
                    call.respond(response)
                }

                post("/blocks") {
                    statusByPath["/blocks"]?.let {
                        call.respond(it)
                        return@post
                    }
                    val request = call.request.call.receive<BlockSignatureRequest>()
                    val signature =
                        if (invalidSignatures) {
                            AttoSignature(Random.nextBytes(64))
                        } else {
                            signer.sign(request.target)
                        }
                    call.respond(SignatureResponse(signature))
                }

                post("/votes") {
                    statusByPath["/votes"]?.let {
                        call.respond(it)
                        return@post
                    }
                    val request = call.request.call.receive<VoteSignatureRequest>()
                    val signature =
                        if (invalidSignatures) {
                            AttoSignature(Random.nextBytes(64))
                        } else {
                            signer.sign(request.target)
                        }
                    call.respond(SignatureResponse(signature))
                }

                post("/challenges") {
                    statusByPath["/challenges"]?.let {
                        call.respond(it)
                        return@post
                    }
                    val request = call.request.call.receive<ChallengeSignatureRequest>()
                    val signature =
                        if (invalidSignatures) {
                            AttoSignature(Random.nextBytes(64))
                        } else {
                            signer.sign(request.target, request.timestamp)
                        }
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
