# commons-transport

Shared HTTP transport for Commons remote clients. Use this module when implementing an HTTP adapter; use [the node client](../commons-node-remote/README.md), [remote worker](../commons-worker-remote/README.md), or [remote signer](../commons-signer-remote/README.md) for their domain APIs.

## Add the dependency

```kotlin
implementation("cash.atto:commons-transport:<version>")
```

The remote modules already include it. Its Kotlin API uses Ktor and Kotlin serialization; custom request and response types need serializers. The npm artifact supplies the remote clients' transport dependency and does not expose a JavaScript API for constructing `AttoHttpTransport`.

## Build an adapter

Create `AttoHttpTransport(baseUrl, headerProvider)` with a base URL and an optional suspending header provider. The provider runs for each request, so it can obtain fresh authentication headers.

| Method | Response |
| --- | --- |
| `get<Response>(path)` | One decoded JSON value |
| `post<Request, Response>(path, body)` | One decoded JSON value |
| `getNdjson<Response>(path)` | A `Flow<Response>` decoded from newline-delimited JSON |
| `postNdjson<Request, Response>(path, body)` | A `Flow<Response>` from a POST response |

Each method accepts `AttoHttpTimeouts` with optional `request`, `socket`, and `connect` durations. Stream requests begin when the flow is collected. Collection cancellation cancels the request; the transport does not reconnect or retry.

Requests use JSON bodies and set `Accept` to JSON or `application/x-ndjson`. Decoding ignores unknown JSON fields. NDJSON records have a 2,000-character line limit; malformed or oversized records fail the flow. HTTP failures propagate, and `Throwable.httpStatusCodeOrNull()` extracts a Ktor client/server error status, including from a cause.

See the [implementation](src/commonMain/kotlin/cash/atto/commons/transport/AttoHttpTransport.kt) and [transport tests](src/commonTest/kotlin/cash/atto/commons/transport/AttoHttpTransportTest.kt) for request configuration and adapter examples.

[Project overview](../README.md) · [License](../LICENSE)
