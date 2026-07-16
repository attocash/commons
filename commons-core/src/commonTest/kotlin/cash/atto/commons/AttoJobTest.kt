package cash.atto.commons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttoJobTest {
    @Test
    fun `should delegate lifecycle operations`() {
        var active = true
        var cancellations = 0
        val job =
            AttoJob.create(
                activeProvider = { active },
                cancellation = {
                    active = false
                    cancellations++
                },
            )

        assertTrue(job.isActive())

        job.cancel()

        assertFalse(job.isActive())
        assertEquals(1, cancellations)
    }
}
