package cash.atto.commons.worker

import cash.atto.commons.AttoWork

internal actual class AttoCpuWorkHasher actual constructor(
    private val target: ByteArray,
) {
    actual fun isValid(
        threshold: ULong,
        work: ByteArray,
    ): Boolean = AttoWork.isValid(threshold, target, work)
}
