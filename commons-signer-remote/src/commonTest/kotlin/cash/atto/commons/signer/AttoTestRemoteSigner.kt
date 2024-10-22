package cash.atto.commons.signer

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChallenge
import cash.atto.commons.AttoPrivateKey
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoVote
import cash.atto.commons.toSigner
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable


class AttoTestRemoteSigner(port: Int) {
    val signer = AttoPrivateKey.generate().toSigner()

    val server = embeddedServer(CIO, port = port) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            get("/") {
                val response = PublicKeyResponse(signer.publicKey)
                call.respond(response)
            }

            post("/blocks") {
                val request = call.request.call.receive<SignatureRequest<AttoBlock>>()
                val signature = signer.sign(request.target)
                call.respond(SignatureResponse(signature))
            }


            post("/votes") {
                val request = call.request.call.receive<SignatureRequest<AttoVote>>()
                val signature = signer.sign(request.target)
                call.respond(SignatureResponse(signature))
            }

            post("/challenges") {
                val request = call.request.call.receive<SignatureRequest<AttoChallenge>>()
                val signature = signer.sign(request.target)
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

    @Serializable
    data class PublicKeyResponse(
        val publicKey: AttoPublicKey,
    )

    @Serializable
    data class SignatureRequest<T>(
        val target: T,
    )

    @Serializable
    data class SignatureResponse(
        val signature: AttoSignature,
    )
}
