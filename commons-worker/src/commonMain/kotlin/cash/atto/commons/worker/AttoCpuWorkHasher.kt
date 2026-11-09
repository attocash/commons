package cash.atto.commons.worker

// Each CPU lane owns its hasher so platform implementations can reuse mutable digest state.
internal expect class AttoCpuWorkHasher(
    target: ByteArray,
) {
    fun isValid(
        threshold: ULong,
        work: ByteArray,
    ): Boolean
}
