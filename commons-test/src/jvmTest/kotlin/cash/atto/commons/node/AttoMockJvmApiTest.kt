package cash.atto.commons.node

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttoMockJvmApiTest {
    @Test
    fun `direct suspend lifecycle is hidden and async lifecycle uses futures`() {
        // Given
        val directClasses = listOf(AttoNodeMock::class.java, AttoWorkerMock::class.java)
        val asyncClasses = listOf(AttoNodeMockAsync::class.java, AttoWorkerMockAsync::class.java)

        // When
        val directLifecycleMethods =
            directClasses.flatMap { type ->
                listOf("start", "stop").map { name ->
                    type.declaredMethods.single { method ->
                        method.name == name && method.parameterTypes.lastOrNull()?.name == "kotlin.coroutines.Continuation"
                    }
                }
            }
        val asyncLifecycleMethods =
            asyncClasses.flatMap { type ->
                listOf("start", "stop").map { name ->
                    type.getMethod(name)
                }
            }

        // Then
        assertTrue(directLifecycleMethods.all { it.isSynthetic })
        assertTrue(asyncLifecycleMethods.all { it.parameterCount == 0 })
        assertTrue(asyncLifecycleMethods.all { it.returnType == CompletableFuture::class.java })
        assertFalse(asyncLifecycleMethods.any { it.isSynthetic })
        assertEquals(4, directLifecycleMethods.size)
        assertEquals(4, asyncLifecycleMethods.size)
    }
}
