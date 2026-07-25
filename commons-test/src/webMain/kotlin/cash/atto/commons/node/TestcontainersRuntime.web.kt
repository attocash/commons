@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.node

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise
import kotlin.time.Duration.Companion.milliseconds

private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private var scheduledCleanup: Deferred<Unit> = CompletableDeferred(Unit)
private var scheduledCleanupFailure: Throwable? = null
private val activeEndpointRetryDelays = listOf(100, 200, 400, 800).map { it.milliseconds }

internal class TestcontainersLifecycle(
    private val resourceName: String,
) {
    private var active = false

    fun beginStart() {
        check(!active) { "$resourceName is already started or starting" }
        active = true
    }

    fun release() {
        active = false
    }
}

internal fun configureTestcontainersRuntime() {
    js(
        """
        if (
            typeof process !== "undefined" &&
            process.env != null &&
            process.env.TESTCONTAINERS_RYUK_PRIVILEGED == null
        ) {
            process.env.TESTCONTAINERS_RYUK_PRIVILEGED = "true";
        }
        """,
    )
}

internal suspend fun stopTestcontainersResources(
    first: JsAny?,
    second: JsAny? = null,
    third: JsAny? = null,
) = stopTestcontainersResources(resourceStops(first, second, third))

internal fun scheduleTestcontainersCleanup(
    first: JsAny?,
    second: JsAny? = null,
    third: JsAny? = null,
) = scheduleTestcontainersCleanup(resourceStops(first, second, third))

internal suspend fun stopTestcontainersResources(resourceStops: List<suspend () -> Unit>) {
    withContext(NonCancellable) {
        var failure: Throwable? = null
        for (stopResource in resourceStops) {
            try {
                stopTestcontainersResource(stopResource)
            } catch (exception: Throwable) {
                failure = failure.withSuppressed(exception)
            }
        }

        failure?.let { throw it }
    }
}

internal fun scheduleTestcontainersCleanup(resourceStops: List<suspend () -> Unit>) {
    val previousCleanup = scheduledCleanup
    scheduledCleanup =
        cleanupScope.async {
            previousCleanup.await()
            try {
                stopTestcontainersResources(resourceStops)
            } catch (exception: Throwable) {
                scheduledCleanupFailure = scheduledCleanupFailure.withSuppressed(exception)
            }
        }
}

internal suspend fun awaitScheduledTestcontainersCleanup() {
    while (true) {
        val cleanup = scheduledCleanup
        cleanup.await()
        if (cleanup === scheduledCleanup) {
            break
        }
    }

    val failure = scheduledCleanupFailure
    scheduledCleanupFailure = null
    failure?.let { throw it }
}

private fun resourceStops(
    first: JsAny?,
    second: JsAny?,
    third: JsAny?,
): List<suspend () -> Unit> =
    listOfNotNull(first, second, third).map { resource ->
        { stopTestcontainersResource(resource).awaitTestcontainers() }
    }

private suspend fun stopTestcontainersResource(stopResource: suspend () -> Unit) {
    var retryIndex = 0
    while (true) {
        try {
            stopResource()
            return
        } catch (exception: Throwable) {
            if (!exception.hasActiveEndpoints() || retryIndex == activeEndpointRetryDelays.size) {
                throw exception
            }
            delay(activeEndpointRetryDelays[retryIndex++])
        }
    }
}

private fun stopTestcontainersResource(resource: JsAny): Promise<JsAny?> = js("resource.stop()")

private fun Throwable?.withSuppressed(exception: Throwable): Throwable = this?.apply { addSuppressed(exception) } ?: exception

private fun Throwable.hasActiveEndpoints(): Boolean =
    generateSequence(this) { it.cause }
        .any { it.message.orEmpty().contains("has active endpoints", ignoreCase = true) }

internal suspend fun <T : JsAny?> Promise<T>.awaitTestcontainers(): T =
    suspendCancellableCoroutine { continuation ->
        then(
            {
                if (continuation.isActive) {
                    continuation.resume(it)
                }
                null
            },
            {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        IllegalStateException(testcontainersPromiseRejectionMessage(it)),
                    )
                }
                null
            },
        )
    }

private fun testcontainersPromiseRejectionMessage(reason: JsAny?): String =
    js(
        """
        (() => {
            const value = reason;
            if (value == null) return "Promise rejected.";
            if (value instanceof Error) return value.message || value.name || String(value);
            if (typeof value === "object" && "message" in value) return String(value.message);
            return String(value);
        })()
        """,
    )
