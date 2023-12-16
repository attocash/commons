package cash.atto.commons

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random

val openBlock = AttoOpenBlock(
    version = 0U,
    publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    balance = AttoAmount.MIN,
    timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
    sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32))),
    representative = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
)

val sendBlock = AttoSendBlock(
    version = 0U,
    publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    height = 2U,
    balance = AttoAmount(1U),
    timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
    previous = AttoHash(Random.Default.nextBytes(ByteArray(32))),
    receiverPublicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    amount = AttoAmount(1U),
)

val receiveBlock = AttoReceiveBlock(
    version = 0U,
    publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    height = 2U,
    balance = AttoAmount.MAX,
    timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
    previous = AttoHash(Random.nextBytes(ByteArray(32))),
    sendHash = AttoHash(Random.Default.nextBytes(ByteArray(32)))
)

val changeBlock = AttoChangeBlock(
    version = 0U,
    publicKey = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
    height = 2U,
    balance = AttoAmount.MAX,
    timestamp = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds()),
    previous = AttoHash(Random.nextBytes(ByteArray(32))),
    representative = AttoPublicKey(Random.Default.nextBytes(ByteArray(32))),
)