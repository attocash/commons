@file:JsExport
@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons

data class AccountUpdate(
    val account: AttoAccount,
    val block: AttoBlock,
)

private fun String?.toInstant(): AttoInstant {
    if (this == null) return AttoInstant.now()
    return AttoInstant.fromIso(this)
}

private fun Pair<AttoBlock, AttoAccount>.toAccountUpdate(): AccountUpdate = AccountUpdate(second, first)

fun attoAccountOpen(
    representativeAddress: AttoAddress,
    receivable: AttoReceivable,
    timestamp: String? = null,
): AccountUpdate {
    val update =
        AttoAccount.open(
            representativeAddress.algorithm,
            representativeAddress.publicKey,
            receivable,
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
