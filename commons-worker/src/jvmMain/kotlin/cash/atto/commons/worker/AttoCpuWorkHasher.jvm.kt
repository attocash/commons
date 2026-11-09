package cash.atto.commons.worker

import cash.atto.commons.AttoWork
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.util.Pack

internal actual class AttoCpuWorkHasher actual constructor(
    private val target: ByteArray,
) {
    private val digest = Blake2bDigest(null, AttoWork.SIZE, null, null)
    private val output = ByteArray(AttoWork.SIZE)

    actual fun isValid(
        threshold: ULong,
        work: ByteArray,
    ): Boolean {
        digest.update(work, 0, work.size)
        digest.update(target, 0, target.size)
        // doFinal resets the digest for the next nonce.
        digest.doFinal(output, 0)
        return Pack.littleEndianToLong(output, 0).toULong() <= threshold
    }
}
