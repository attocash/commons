package cash.atto.commons.node

import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoSignature
import cash.atto.commons.node.HttpSignerRemote.BlockSignatureRequest
import cash.atto.commons.node.HttpSignerRemote.ChallengeSignatureRequest
import cash.atto.commons.node.HttpSignerRemote.PublicKeyResponse
import cash.atto.commons.node.HttpSignerRemote.SignatureResponse
import cash.atto.commons.node.HttpSignerRemote.VoteSignatureRequest
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.time.Duration

class MocktRemoteSigner(
    port: Int,
    private val statusByPath: Map<String, HttpStatusCode> = emptyMap(),
    private val invalidSignatures: Boolean = false,
    private val voteDelay: Duration = Duration.ZERO,
) {
    val signer = runBlocking { AttoPrivateKey.generate().toSigner() }
    val voteRequestStarted = CompletableDeferred<Unit>()
    var voteRequestCount = 0
        private set

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
                    voteRequestCount += 1
                    voteRequestStarted.complete(Unit)
                    statusByPath["/votes"]?.let {
                        call.respond(it)
                        return@post
                    }
                    delay(voteDelay)
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
