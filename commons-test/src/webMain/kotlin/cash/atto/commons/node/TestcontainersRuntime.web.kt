@file:OptIn(ExperimentalWasmJsInterop::class)

package cash.atto.commons.node

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.Promise

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
) {
    var failure: Throwable? = null
    for (resource in listOf(first, second, third)) {
        if (resource == null) {
            continue
        }

        try {
            stopTestcontainersResource(resource).awaitTestcontainers()
        } catch (exception: Throwable) {
            if (failure == null) {
                failure = exception
            } else {
                failure.addSuppressed(exception)
            }
        }
    }

    failure?.let { throw it }
}

internal fun scheduleTestcontainersCleanup(
    first: JsAny?,
    second: JsAny? = null,
    third: JsAny? = null,
) {
    js(
        """
        (function() {
            var resources = [first, second, third];
            var cleanupQueue = globalThis.__attoCommonsTestcontainerCleanupQueue || Promise.resolve();
            globalThis.__attoCommonsTestcontainerCleanupQueue = cleanupQueue
                .then(function() {
                    var firstError = null;
                    var cleanup = Promise.resolve();
                    resources.forEach(function(resource) {
                        cleanup = cleanup.then(function() {
                            if (resource == null) return;
                            return resource.stop().catch(function(error) {
                                if (firstError == null) firstError = error;
                            });
                        });
                    });
                    return cleanup.then(function() {
                        if (firstError != null) throw firstError;
                    });
                })
                .catch(function(error) {
                    console.warn(error);
                });
        })()
        """,
    )
}

private fun stopTestcontainersResource(resource: JsAny): Promise<JsAny?> = js("resource.stop()")

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
