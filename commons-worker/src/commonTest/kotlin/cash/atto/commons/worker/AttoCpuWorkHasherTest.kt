package cash.atto.commons.worker

import cash.atto.commons.AttoHasher
import cash.atto.commons.AttoWork
import cash.atto.commons.AttoWorkTarget
import cash.atto.commons.toByteArray
import cash.atto.commons.toULong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AttoCpuWorkHasherTest {
    @Test
    fun `should match reference thresholds when reusing digest for many nonces and targets`() {
        // Given
        val targets =
            listOf(
                ByteArray(AttoWorkTarget.SIZE),
                ByteArray(AttoWorkTarget.SIZE) { 0xFF.toByte() },
                ByteArray(AttoWorkTarget.SIZE) { it.toByte() },
                ByteArray(AttoWorkTarget.SIZE) { (it * 37 + 129).toByte() },
            )
        var sawHighBit = false
        var sawLowBit = false

        for (target in targets) {
            val hasher = AttoCpuWorkHasher(target)
            for (nonce in 0UL until 128UL) {
                val work = (nonce * 0x0102_0304_0506_0708UL).toByteArray()
                val difficulty = AttoHasher.hash(AttoWork.SIZE, work, target).toULong()
                sawHighBit = sawHighBit || difficulty > Long.MAX_VALUE.toULong()
                sawLowBit = sawLowBit || difficulty <= Long.MAX_VALUE.toULong()
                val thresholds =
                    listOfNotNull(
                        0UL,
                        Long.MAX_VALUE.toULong(),
                        Long.MAX_VALUE.toULong() + 1UL,
                        ULong.MAX_VALUE,
                        difficulty,
                        if (difficulty > 0UL) difficulty - 1UL else null,
                        if (difficulty < ULong.MAX_VALUE) difficulty + 1UL else null,
                    )

                for (threshold in thresholds) {
                    // When
                    val valid = hasher.isValid(threshold, work)

                    // Then
                    assertEquals(difficulty <= threshold, valid, "nonce=$nonce threshold=$threshold difficulty=$difficulty")
                }
            }
        }
        assertTrue(sawHighBit, "The inputs must exercise unsigned difficulties with their high bit set")
        assertTrue(sawLowBit, "The inputs must also exercise difficulties below the unsigned high bit")
    }

    @Test
    fun `should keep interleaved lane hashers independent`() {
        // Given
        val targets = List(3) { lane -> ByteArray(AttoWorkTarget.SIZE) { (it + lane * 91).toByte() } }
        val hashers = targets.map(::AttoCpuWorkHasher)

        for (nonce in 0UL until 64UL) {
            for (lane in hashers.indices) {
                val work = (nonce + (lane.toULong() shl 63)).toByteArray()
                val difficulty = AttoHasher.hash(AttoWork.SIZE, work, targets[lane]).toULong()

                // When
                val valid = hashers[lane].isValid(difficulty, work)
                val belowThreshold = difficulty > 0UL && hashers[lane].isValid(difficulty - 1UL, work)

                // Then
                assertTrue(valid, "lane=$lane nonce=$nonce must accept the exact threshold")
                assertEquals(false, belowThreshold, "lane=$lane nonce=$nonce must reject a lower threshold")
            }
        }
    }
}
