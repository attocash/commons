# commons-spring-boot-starter

Integrate Atto value types with a Spring Boot application: convert request parameters and database values, use Kotlin serialization in WebFlux, and describe Atto types in SpringDoc OpenAPI schemas.

This module is JVM-only and targets Java 21. The current build uses Spring Boot 4 and SpringDoc 3. It does not create node clients, workers, or wallets.

## Install

```kotlin
dependencies {
    implementation("cash.atto:commons-spring-boot-starter:<version>")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
}
```

Use Spring Boot dependency management for the Spring dependencies. WebFlux, Spring Data R2DBC, and SpringDoc are compile-only dependencies of this module: add the integrations your application uses. The registered codec customizer requires WebFlux's codec classes.

## Bind an Atto request parameter

When Spring exposes a `ConfigurableConversionService` bean, the starter registers [AttoConverters](src/main/kotlin/cash/atto/commons/spring/conversion/AttoConverters.kt) with it. For a WebFlux application using that conversion service:

```kotlin
import cash.atto.commons.AttoAddress
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class AddressController {
    @GetMapping("/address")
    fun address(@RequestParam address: AttoAddress): String = address.toString()
}
```

Address input is parsed through `AttoAddress.parse`. See [address formats](../commons-core/README.md) and [address migration](../V2-STREAMS.md#address-based-account-routes) before accepting or storing addresses from older clients.

## What is configured

| Integration | Behavior and conditions |
| --- | --- |
| WebFlux JSON | Registers a `CodecCustomizer` with Kotlin serialization encoders and decoders. Unknown keys are ignored, null properties are omitted, and default values are encoded. |
| Spring conversions | Registers `AttoConverters.all` if a `ConfigurableConversionService` bean is available. |
| R2DBC | Supplies `R2dbcCustomConversions` when R2DBC classes and a `ConnectionFactory` bean are present, unless your application already supplies `R2dbcCustomConversions`. Uses the MySQL dialect. |
| SpringDoc | Adds parameter and schema customizers when `OpenApiCustomizer` is on the classpath, including Atto primitives and block variants. |

For MySQL R2DBC, add `spring-boot-starter-data-r2dbc`, a MySQL R2DBC driver, and your connection configuration. The starter then contributes Atto conversions; it does not create your database schema. For another dialect or customized conversions, supply your own `R2dbcCustomConversions` bean and include the Atto converters you need. The default dialect is not selected from the connection factory.

The codec, general converter registrar, and SpringDoc customizer beans have no missing-bean condition. Account for their registration when customizing those parts of your application. JSON serialization uses the core types' wire contracts, including [decimal strings for 64-bit quantities](../commons-core/README.md#addresses-and-numeric-values).

## Serve WebSocket streams

`AttoStreamWebSocketHandler` adapts Spring WebFlux messages and handshake information to the
[framework-independent stream server](../commons-node-stream-server/README.md), which the starter exposes
as an API dependency. Import `AttoStreamPlan` and `AttoStreamMember` from
`cash.atto.commons.node.stream.server` when supplying the handler's planner. Applications own route
registration, request validation, and the query flows selected by each plan.

The server module owns membership, producer cancellation, output sequence numbers, acknowledgement
credits, and terminal ordering. The Spring adapter owns pooled payload copying, encoding, connection
bridges, and translation of Spring response-status exceptions into protocol errors. Adding the starter
does not register a WebSocket endpoint automatically.

## Enable v2 stream heartbeats

Register `NdjsonHeartbeatHttpMessageWriter` in your application's existing WebFlux codec configuration,
passing the same `Json` instance used by its JSON encoder. This preserves contextual serializers,
default-value encoding, and null handling. For example, inside a `WebFluxConfigurer` with an existing
`json` property:

```kotlin
override fun configureHttpMessageCodecs(configurer: ServerCodecConfigurer) {
    configurer.defaultCodecs().kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(json))
    configurer.customCodecs().register(NdjsonHeartbeatHttpMessageWriter(json))
}
```

Import the writer from `cash.atto.commons.spring`, `ServerCodecConfigurer` from
`org.springframework.http.codec`, and `KotlinSerializationJsonEncoder` from
`org.springframework.http.codec.json`. Applications with custom JSON configuration must reuse it here.
The starter does not register the heartbeat writer automatically.

The writer selects `application/x-ndjson` and adds heartbeats only when the application-relative path
starts with `/v2/` and ends with `/stream`, or for `POST /v2/transactions`. It flushes a blank newline
every second, including while waiting for the first record. It supports multi-record publishers and delayed single records;
empty or finite sources complete normally. Each record stays intact, and cancellation or termination
stops the timer. Other NDJSON paths use the supplied JSON configuration without heartbeat bytes;
ordinary JSON remains handled by the existing codecs.

Applications must add their own v2 controller aliases and hide legacy stream paths from OpenAPI.
See [the stream contract and rollout](../V2-STREAMS.md) for server compatibility, operation deadlines,
and deployment order. A heartbeat exposes a transport failure; it does not confirm an application
operation or prove that memory growth has been resolved.

## Further reading

- [Auto-configuration registrations](src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports).
- [R2DBC conditions](src/main/kotlin/cash/atto/commons/spring/conversion/AttoR2dbcCustomConversionsAutoConfiguration.kt) and [WebFlux codecs](src/main/kotlin/cash/atto/commons/spring/AttoAutoConfiguration.kt).
- [OpenAPI tests](src/test/kotlin/cash/atto/commons/spring/AttoSpringDocOpenApiAutoConfigurationTest.kt).
- [Repository overview](../README.md) and [BSD 3-Clause license](../LICENSE).
