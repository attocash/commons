@file:JsExport
@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class AccountUpdate(
    val account: AttoAccount,
    val block: AttoBlock,
)

private fun String?.toInstant(): Instant {
    if (this == null) return Clock.System.now()
    return Instant.parse(this)
}

private fun Pair<AttoBlock, AttoAccount>.toAccountUpdate(): AccountUpdate {
    return AccountUpdate(second, first)
}

fun attoAccountOpen(
    network: AttoNetwork,
    representativeAddress: AttoAddress,
    receivable: AttoReceivable,
    timestamp: String? = null,
): AccountUpdate {
    val update =
        AttoAccount.open(
            representativeAddress.algorithm,
            representativeAddress.publicKey,
            receivable,
            network,
            timestamp.toInstant(),
        )
    return update.toAccountUpdate()
}

fun attoAccountSend(
    account: AttoAccount,
    receiverAddress: AttoAddress,
    amount: AttoAmount,
    timestamp: String? = null,
): AccountUpdate {
    val update = account.send(receiverAddress.algorithm, receiverAddress.publicKey, amount, timestamp.toInstant())
    return update.toAccountUpdate()
}

fun attoAccountReceive(
    account: AttoAccount,
    receivable: AttoReceivable,
    timestamp: String? = null,
): AccountUpdate {
    val update = account.receive(receivable, timestamp.toInstant())
    return update.toAccountUpdate()
}

fun attoAccountChange(
    account: AttoAccount,
    representativeAddress: AttoAddress,
    timestamp: String? = null,
): AccountUpdate {
    val update = account.change(representativeAddress.algorithm, representativeAddress.publicKey, timestamp.toInstant())
    return update.toAccountUpdate()
}
