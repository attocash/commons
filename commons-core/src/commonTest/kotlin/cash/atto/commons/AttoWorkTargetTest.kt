package cash.atto.commons

import kotlin.test.Test
import kotlin.test.assertFailsWith

internal class AttoWorkTargetTest {
    @Test
    fun `should validate size`() {
        assertFailsWith<IllegalArgumentException> {
            AttoWorkTarget(ByteArray(AttoWorkTarget.SIZE - 1))
        }
    }
}
