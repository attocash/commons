@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.node

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestcontainersLifecycleTest {
    @Test
    fun `cancelled startup retains and stops its late resource`() =
        runTest {
            // Given
            val lifecycle = TestcontainersLifecycle("Worker")
            val pending = pendingStart()
            val resource = resource()
            val startup = async { start(lifecycle, pending) }
            yield()

            // When
            startup.cancel()
            resolve(pending, resource)
            startup.join()

            // Then
            assertEquals(1, stops(resource))
            assertFailsWith<IllegalArgumentException> { lifecycle.startedResource("not started") }
            lifecycle.beginStart()
            lifecycle.stop()
        }

    @Test
    fun `close owns a pending resource until startup resolves`() =
        runTest {
            // Given
            val lifecycle = TestcontainersLifecycle("Worker")
            val pending = pendingStart()
            val resource = resource()
            val startup = async { runCatching { start(lifecycle, pending) } }
            yield()

            // When
            lifecycle.close()
            val cleanup = async { awaitScheduledTestcontainersCleanup() }
            yield()

            // Then
            assertFalse(cleanup.isCompleted)
            resolve(pending, resource)
            assertTrue(startup.await().isFailure)
            cleanup.await()
            assertEquals(1, stops(resource))
            assertFailsWith<IllegalArgumentException> { lifecycle.startedResource("not started") }
        }

    @Test
    fun `stop waits for pending acquisition and stops it once`() =
        runTest {
            // Given
            val lifecycle = TestcontainersLifecycle("Worker")
            val pending = pendingStart()
            val resource = resource()
            val startup = async { runCatching { start(lifecycle, pending) } }
            yield()

            // When
            val stopping = async { lifecycle.stop() }
            yield()

            // Then
            assertFalse(stopping.isCompleted)
            assertFailsWith<IllegalStateException> { lifecycle.beginStart() }
            resolve(pending, resource)
            stopping.await()
            assertTrue(startup.await().isFailure)
            assertEquals(1, stops(resource))
            lifecycle.beginStart()
            lifecycle.stop()
        }

    @Test
    fun `old startup completion cannot release or replace a new session`() =
        runTest {
            // Given
            val lifecycle = TestcontainersLifecycle("Worker")
            val oldPending = pendingStart()
            val oldResource = resource()
            val oldStartup = async { runCatching { start(lifecycle, oldPending) } }
            yield()
            lifecycle.close()
            val newPending = pendingStart()
            val newResource = resource()
            val newStartup = async { start(lifecycle, newPending) }
            yield()

            // When
            resolve(oldPending, oldResource)
            assertTrue(oldStartup.await().isFailure)
            resolve(newPending, newResource)
            newStartup.await()

            // Then
            assertEquals(1, stops(oldResource))
            assertEquals(0, stops(newResource))
            assertTrue(lifecycle.startedResource("not started") === newResource)
            lifecycle.stop()
            assertEquals(1, stops(newResource))
        }

    @Test
    fun `failed startup cleans acquired resources in reverse order and preserves failures`() =
        runTest {
            // Given
            val lifecycle = TestcontainersLifecycle("Node")
            val session = lifecycle.beginStart()
            val events = events()
            val network = resource(events, "network", true)
            val mysql = resource(events, "mysql", true)
            session.acquire { resolved(network) }
            session.acquire { resolved(mysql) }

            // When
            val startupFailure =
                try {
                    session.acquire { rejected() }
                    error("Expected startup to fail")
                } catch (exception: Throwable) {
                    try {
                        lifecycle.stop(session)
                    } catch (cleanupFailure: Throwable) {
                        exception.addSuppressed(cleanupFailure)
                    }
                    exception
                }

            // Then
            assertEquals("startup failed", startupFailure.message)
            assertEquals("mysql,network", eventNames(events))
            val cleanupFailure = startupFailure.suppressedExceptions.single()
            assertEquals("mysql cleanup failed", cleanupFailure.message)
            assertEquals("network cleanup failed", cleanupFailure.suppressedExceptions.single().message)
        }

    private suspend fun start(
        lifecycle: TestcontainersLifecycle,
        pending: JsAny,
    ) {
        val session = lifecycle.beginStart()
        try {
            awaitScheduledTestcontainersCleanup()
            val resource = session.acquire { promise(pending) }
            session.started(resource)
        } catch (exception: Throwable) {
            try {
                lifecycle.stop(session)
            } catch (cleanupFailure: Throwable) {
                exception.addSuppressed(cleanupFailure)
            }
            throw exception
        }
    }
}

private fun pendingStart(): JsAny =
    js("(() => { let resolve; const promise = new Promise(done => { resolve = done; }); return { promise, resolve }; })()")

private fun promise(pending: JsAny): Promise<JsAny> = js("pending.promise")

private fun resolve(
    pending: JsAny,
    resource: JsAny,
) {
    js("pending.resolve(resource)")
}

private fun resource(): JsAny = js("({ stops: 0, stop() { this.stops++; return Promise.resolve(null); } })")

private fun resource(
    events: JsAny,
    name: String,
    fail: Boolean,
): JsAny =
    js("({ stop() { events.push(name); return fail ? Promise.reject(new Error(name + ' cleanup failed')) : Promise.resolve(null); } })")

private fun stops(resource: JsAny): Int = js("resource.stops")

private fun events(): JsAny = js("[]")

private fun eventNames(events: JsAny): String = js("events.join(',')")

private fun resolved(resource: JsAny): Promise<JsAny> = js("Promise.resolve(resource)")

private fun rejected(): Promise<JsAny> = js("Promise.reject(new Error('startup failed'))")
