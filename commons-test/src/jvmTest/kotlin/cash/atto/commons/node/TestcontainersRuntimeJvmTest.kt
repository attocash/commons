package cash.atto.commons.node

import kotlin.test.Test
import kotlin.test.assertEquals

class TestcontainersRuntimeJvmTest {
    @Test
    fun `should close every resource and preserve failures`() {
        // Given
        val closes = mutableListOf<String>()
        val node =
            AutoCloseable {
                closes += "node"
                error("node cleanup failed")
            }
        val mysql =
            AutoCloseable {
                closes += "mysql"
                error("mysql cleanup failed")
            }
        val network = AutoCloseable { closes += "network" }

        // When
        val failure =
            try {
                closeTestcontainersResources(node, mysql, network)
                null
            } catch (exception: Throwable) {
                exception
            }

        // Then
        assertEquals(listOf("node", "mysql", "network"), closes)
        assertEquals("node cleanup failed", failure?.message)
        assertEquals(listOf("mysql cleanup failed"), failure?.suppressedExceptions?.map { it.message })
    }
}
