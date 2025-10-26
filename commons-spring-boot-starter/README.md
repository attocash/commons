# commons-spring-boot-starter

Spring Boot auto-configuration and converters to integrate Atto types with your application.

Currently included:
- R2DBC custom conversions for Atto types (MySQL dialect)

## Getting started

Add the starter dependency (Kotlin DSL example):

```kotlin
dependencies {
  implementation("cash.atto:commons-spring-boot-starter:<version>")
}
```

The starter contributes `R2dbcCustomConversions` when both Spring Data R2DBC and a `ConnectionFactory` are on the classpath. It registers `AttoConverters` using the MySQL dialect.

## Usage

With Spring Data R2DBC and a configured MySQL `ConnectionFactory`, the starter will auto-register conversions so you can map Atto value objects in your domain entities and repositories without manual converters.

```kotlin
@Table("accounts")
data class AccountEntity(
  @Id val id: Long? = null,
  val address: AttoAddress,
  val publicKey: AttoPublicKey,
)

interface AccountRepository : ReactiveCrudRepository<AccountEntity, Long>
```

No additional configuration is needed; `AttoR2dbcCustomConversionsAutoConfiguration` will provide the necessary conversions.

For non-MySQL dialects, contribute your own `R2dbcCustomConversions` bean or extend the auto-configuration.
