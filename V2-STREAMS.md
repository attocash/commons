# V2 HTTP streams and account routes

Commons 10 changes every remote Node NDJSON request and batch account lookup to v2 routes. This is a breaking server
compatibility change. V2 uses address-based Commons models and retains `application/x-ndjson`.
Local builds default to `10.0.0-SNAPSHOT`; pass `-Pversion=...` when preparing a release. Keep all Commons modules at the
same version. Release automation must select a major release for this change.

## Wire contract

V2 servers attempt to send and individually flush a blank newline every second while a stream is
open, including before its first record. Heartbeats appear between complete encoded records. A slow
connection may delay a write; at most one heartbeat waits for downstream demand. Financial records
retain backpressure and are never dropped to make room for a heartbeat. Source completion, failure,
or client cancellation stops the timer and cancels the corresponding subscription.

Healthy streams have unlimited lifetime unless the operation itself is finite. A heartbeat adds no
domain event and does not reset an application deadline or confirm publication. An error before any
response bytes are committed can retain its HTTP error status; an error after a heartbeat must end
the response because its successful status is already committed. Clients must still await the actual
record and handle a truncated stream as failure.

Unprefixed legacy streams remain callable without heartbeat bytes. Account snapshots use the new
address-based scalar and batch routes below; other scalar JSON routes keep their existing paths and encoding.
Worker, signer, and unrelated upstream service paths do not change.
The shared parser tolerates blank and whitespace-only lines on every NDJSON response, while retaining
the existing line-size limit and rejection of malformed nonblank records.

## Address-based account routes

Use HTTP `POST /v2/transactions` to publish and wait for confirmation, returning the confirmed transaction as NDJSON.
Publication is HTTP-only, including when the client selects WebSockets for read streams.
Live transaction updates remain at `GET /v2/transactions/stream`. The former
`POST /v2/transactions/stream` mapping is not exposed; legacy unprefixed publication routes remain callable.

Single-account requests use the full account address instead of its public key. Encode the entire
`atto://...` value as one URL path segment, including the colon and both slashes. For example,
`atto://<address>` becomes `atto%3A%2F%2F<address>`; encoding it twice is invalid.

| Operation | V2 route |
| --- | --- |
| Account snapshot | `GET /v2/accounts/{encodedAddress}` |
| Batch account snapshots | `POST /v2/accounts` with an `addresses` array |
| Account stream | `GET /v2/accounts/{encodedAddress}/stream` |
| Receivables | `GET /v2/accounts/{encodedAddress}/receivables/stream?minAmount=...` |
| Entry history | `GET /v2/accounts/{encodedAddress}/entries/stream?fromHeight=...&toHeight=...` |
| Transaction history | `GET /v2/accounts/{encodedAddress}/transactions/stream?fromHeight=...&toHeight=...` |

Commons encodes these paths when making requests. Query defaults and timeouts are unchanged, including
the one-second account lookup timeout. Legacy public-key routes remain callable but are hidden from
Swagger/OpenAPI; the new address-based routes are visible. Vote-weight requests already accept
addresses and retain their existing route.

Address-based v2 account responses use `address`, `representativeAddress`, `receiverAddress`, and `subjectAddress`
where applicable, omitting their corresponding public-key and address-algorithm fields. Nested transaction
blocks use the same representation. Hash-algorithm fields such as `sendHashAlgorithm` and `blockAlgorithm`
remain because they describe hashes, not addresses. Signatures, work, hashes, and protocol bytes are unchanged.

These are the normal Commons models and JSON format. The old convenience properties are removed from the
models; callers use `model.address.publicKey`, `model.address.algorithm`, or the corresponding role address.
JSON input requires the canonical addresses; legacy algorithm/public-key pairs cannot replace them.
Extra fields follow the configured JSON unknown-key policy. Node and Gatekeeper own their legacy HTTP
adapters, preserving those routes' previous response fields and numeric representation. V2 responses return Commons models directly
and use decimal strings for 64-bit amounts, heights, and timestamps.

Generic ProtoBuf serialization retains its previous field numbering, framing, and identity fields. Private
wire DTOs derive those fields from addresses; the domain models and JSON output do not regain them.

## Server integration

`commons-node-stream-server` owns the framework-independent WebSocket session engine, including
membership, acknowledgement credits, and ordered terminal output. Its plan/member types are in
`cash.atto.commons.node.stream.server`. The Spring starter depends on that module and retains the
WebFlux handler and Spring-specific failure translation. This extraction does not change the wire
protocol. Server callers must update plan/member imports and use matching module versions.

The [Spring starter](commons-spring-boot-starter/README.md) owns the shared heartbeat writer. Each
application must expose explicit v2 aliases for its existing streaming operations and install that
writer using its existing JSON configuration. An upstream heartbeat does not replace a downstream
server's independent timer. Legacy stream operations should be hidden from generated OpenAPI while
v2 stream operations remain visible. Examples should use v2 paths.

Node must expose v2 for all GET, POST, publication, hash, and height-range stream operations.
Gatekeeper must also expose v2 for its public address, entity, metric, and voter streams, retaining
their upstream protocols and its custom serializers. The writer alone does not add controller routes
or modify OpenAPI documents. Preserve stream validation, snapshot/live handoff, persistence,
cancellation ownership, and operation deadlines when adding aliases.

## Deployment and rollback

The minimum compatible server is the first Node or Gatekeeper release implementing the complete v2
contract above. Published minimum server versions have not yet been assigned. Local verification uses
coordinated builds of Commons, Node, Gatekeeper, and signer. A v1-only server is incompatible with
Commons 10 Node streaming requests and batch account lookups; clients do not
silently retry legacy paths.

1. Upgrade signer so it accepts canonical address-based block and vote requests. Any legacy JSON request
   adaptation belongs at the application HTTP boundary.
2. Release and deploy v2-capable Node backends, then verify their routes and real-socket flushing.
3. Upgrade and deploy Gatekeeper with the shared writer and Commons v2 Node subscriptions.
4. Upgrade wallet-server and other Commons clients using matching module versions. Keep wallet-server's
   finite public history API unchanged.
5. During a v2 canary, compare active HTTP requests and memory trends over matched observation periods.

Roll clients back before servers, unless the server rollback retains v2 support. Heartbeats expose
transport failures; they do not establish application responsiveness or prove that production memory
growth has been fixed. Downstream builds and integration tests must use a pinned, task-owned v2 Node
image rather than an existing v1-only image. Wallet-server adoption, production deployment, and the canary
remain deferred; local Node and Gatekeeper integration must use the coordinated builds.

The `commons-test` Node mock still defaults to `ghcr.io/attocash/node:live`. That moving tag is not
evidence of v2 compatibility. Override `AttoNodeMockConfiguration.image` (or a mock builder's `image`)
with a verified v2 tag or digest before running container-backed Commons wallet tests or downstream
integration suites. The convenience `AttoNodeMock.create(privateKey)` uses the default image; use the
configuration overload when pinning. Parser, client-routing, and Spring socket tests can run without
a Node image. Container-backed wallet integration remains unverified until that image is available.

## Public WebSocket stream protocol

The public `/v2/stream` endpoint runs on the normal HTTP listener, independently of the node's peer
WebSocket listener. HTTP NDJSON and finite JSON routes retain their behavior. One socket owns one
logical streaming operation. The client sends a JSON text command:

```json
{"type":"subscribe","request":{"method":"POST","path":"/v2/accounts/stream","body":{"addresses":["atto://..."]}},"mutable":true}
```

The request identifies one of the 13 v2 read-stream routes. Publication (`POST /v2/transactions`) and
finite routes such as `POST /v2/accounts` are excluded. Publication always uses HTTP NDJSON and retains
its one-minute request/socket deadlines, confirmation-hash validation, cancellation, and error handling. Mutable subscriptions support the four POST operations
for accounts, receivables, account entries, and account transactions. Send `update` with the same method
and path and a complete replacement body to add or remove members in place. Retained members keep
their original ranges and subscriptions; newly added members begin their normal snapshot/history stream.
An empty membership keeps a mutable socket open. Send `seal` when there will be no more updates.

```json
{"type":"update","request":{"method":"POST","path":"/v2/accounts/stream","body":{"addresses":["atto://...","atto://..."]}}}
{"type":"ack","sequence":"1"}
{"type":"seal"}
```

Responses are JSON text frames:

```json
{"type":"subscribed"}
{"type":"updated"}
{"type":"data","data":{"address":"atto://..."},"sequence":"1"}
{"type":"heartbeat"}
{"type":"complete"}
{"type":"error","status":400,"message":"Invalid stream request","terminal":true}
```

Data uses the canonical v2 model JSON. Sequences are decimal strings starting at one. The server allows
32 outstanding data records. The client cumulatively acknowledges a sequence only after its consumer
accepts that record. A separate reader drains control frames and heartbeats while bounded data queues
and credits apply backpressure. Heartbeats carry no domain value and do not extend operation deadlines.
Only `complete` is normal completion; premature disconnect fails the stream. Server completion waits for
all outstanding records to be acknowledged. Invalid updates leave the previous membership intact and
report an error; the SDK surfaces that error by failing the flow. Cancellation closes the connection.

`AttoStreamCommand`, `AttoStreamRequest`, `AttoStreamResponse`, and `AttoStreamProtocol` in `commons-node`
define the contract. The envelope codec is independent of application JSON naming strategies. Transport
mechanics remain in `commons-transport`, independent of node types. There are no operation IDs,
multiplexing, automatic reconnection, transport fallback, or finite HTTP operations over this socket.
