package cash.atto.commons

import cash.atto.commons.worker.AttoCpuWorkHasher
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

@State(Scope.Thread)
open class AttoCpuWorkHasherBenchmark {
    private val target = ByteArray(AttoWorkTarget.SIZE) { (it * 37 + 129).toByte() }
    private val hasher = AttoCpuWorkHasher(target)
    private val work = ByteArray(AttoWork.SIZE)

    @Benchmark
    fun genericValidation(): Boolean {
        incrementWork()
        return AttoWork.isValid(Long.MAX_VALUE.toULong(), target, work)
    }

    @Benchmark
    fun reusedValidation(): Boolean {
        incrementWork()
        return hasher.isValid(Long.MAX_VALUE.toULong(), work)
    }

    private fun incrementWork() {
        for (i in work.indices) {
            work[i] = (work[i] + 1).toByte()
            if (work[i] != 0.toByte()) return
        }
    }
}
