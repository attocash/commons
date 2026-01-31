package cash.atto.commons.wallet

import cash.atto.commons.utils.JsExportForJs
import kotlinx.coroutines.CoroutineDispatcher

@JsExportForJs
expect class AttoWalletAsync internal constructor(
    wallet: AttoWallet,
    dispatcher: CoroutineDispatcher,
) {
    internal val wallet: AttoWallet
}
