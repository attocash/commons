@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.node

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

internal class TestcontainersLifecycle(
    private val resourceName: String,
) {
    private var current: TestcontainersSession? = null

    fun beginStart(): TestcontainersSession {
        check(current == null) { "$resourceName is already started or starting" }
        return TestcontainersSession(resourceName).also { current = it }
    }

    fun startedResource(message: String): JsAny {
        val resource = current?.startedResource
        require(resource != null) { message }
        return resource
    }

    suspend fun stop(session: TestcontainersSession? = current) {
        if (session == null) return
        try {
            session.stop()
        } finally {
            if (current === session) current = null
        }
    }

    fun close() {
        val session = current ?: return
        current = null
        session.close()
    }
}

internal class TestcontainersSession(
    private val resourceName: String,
) {
    private var active = true
    private var cleanup: Deferred<Unit>? = null
    private val acquisitions = mutableListOf<Deferred<JsAny>>()
    var startedResource: JsAny? = null
        private set

    suspend fun acquire(start: () -> Promise<JsAny>): JsAny {
        checkActive()
        // External startup cannot be cancelled. Retain its result until cleanup can stop it.
        val acquisition = retainTestcontainersResource(start())
        acquisitions += acquisition
        val resource = acquisition.await()
        checkActive()
        return resource
    }

    fun started(resource: JsAny) {
        checkActive()
        startedResource = resource
    }

    suspend fun stop() {
        withContext(NonCancellable) {
            cleanup().await()
        }
    }

    fun close() {
        scheduleTestcontainersCleanup(cleanup())
    }

    private fun cleanup(): Deferred<Unit> = cleanup ?: retainTestcontainersCleanup(takeResourceStops()).also { cleanup = it }

    private fun checkActive() {
        check(active) { "$resourceName startup is no longer active" }
    }

    private fun takeResourceStops(): List<suspend () -> Unit> {
        active = false
        startedResource = null
        val resources = acquisitions.asReversed().toList()
        acquisitions.clear()
        return resources.map { acquisition ->
            {
                // A rejected startup never produced a resource; its caller owns that failure.
                val resource =
                    try {
                        acquisition.await()
                    } catch (_: Throwable) {
                        null
                    }
                if (resource != null) {
                    stopTestcontainersResource(resource).awaitTestcontainers()
                }
            }
        }
    }
}
