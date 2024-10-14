package cash.atto.commons.wallet

import cash.atto.commons.AttoNetwork
import cash.atto.commons.AttoSigner
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.SIMPLE


data class AttoEndpoint(
    val prefix: String,
    val headerProvider: () -> Map<String, String> = { emptyMap() },
    val loglevel: LogLevel = LogLevel.NONE,
    val logger: Logger = Logger.SIMPLE,
)

private class AttoAuthenticator(network: AttoNetwork, signer: AttoSigner) {

}

fun AttoEndpoint.gatekeeper(network: AttoNetwork, signer: AttoSigner) {
    val endpoint = "https://gatekeeper.${network.name.lowercase()}.application.atto.cash"
}
