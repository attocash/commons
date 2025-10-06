package cash.atto.commons.spring.schema

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChangeBlock
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoSendBlock
import cash.atto.commons.AttoTransaction
import cash.atto.commons.node.AccountHeightSearch
import cash.atto.commons.node.AccountSearch
import cash.atto.commons.node.HeightSearch
import cash.atto.commons.node.TimeDifferenceResponse
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Discriminator
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.ParameterCustomizer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.core.ResolvableType
import java.util.Collection
import kotlin.reflect.KClass

@AutoConfiguration
@ConditionalOnClass(OpenApiCustomizer::class)
class AttoSpringDocOpenApiAutoConfiguration {
    @Bean
    fun attoParameterCustomizer(): ParameterCustomizer =
        ParameterCustomizer { parameter, resolved ->
            val param = resolved ?: return@ParameterCustomizer parameter

            val rawType = param.parameterType
            val type =
                if (Collection::class.java.isAssignableFrom(rawType)) {
                    val itemType =
                        ResolvableType
                            .forMethodParameter(param)
                            .asCollection()
                            .generics
                            .firstOrNull()
                            ?.resolve()
                    ArraySchema().apply { items = (AttoSchemas.primitiveMap[itemType?.kotlin] ?: StringSchema()) }
                } else {
                    rawType.kotlin
                }
            AttoSchemas.primitiveMap[type]?.let { parameter.schema = it }

            parameter
        }

    @Bean
    fun attoOpenApiCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            val schemas = openApi.components?.schemas ?: return@OpenApiCustomizer

            fun findSchema(vararg candidates: KClass<*>): Schema<*>? = candidates.firstNotNullOfOrNull { schemas[it.simpleName!!] }

            fun ensureSchema(
                clazz: KClass<*>,
                fallback: () -> Schema<*>,
            ): Schema<*> = schemas[clazz.simpleName!!] ?: fallback().also { schemas[clazz.simpleName!!] = it }

            fun putOrReplace(
                clazz: KClass<*>,
                schema: Schema<*>,
            ) {
                schemas[clazz.simpleName!!] = schema
            }

            fun setPropDesc(
                schema: Schema<*>,
                prop: String,
                desc: String,
                example: Any? = null,
            ) {
                schema.properties?.get(prop)?.let { s ->
                    s.description = desc
                    if (example != null) s.example = example
                    return
                }
                schema.allOf?.forEach { part ->
                    val refName = part.`$ref`?.substringAfterLast('/')?.let { schemas[it] }
                    val target = (refName ?: part).properties?.get(prop)
                    if (target != null) {
                        target.description = desc
                        if (example != null) target.example = example
                        return
                    }
                }
            }

            fun removeProps(
                schema: Schema<*>,
                vararg props: String,
            ) {
                val names = props.toSet()

                fun deref(s: Schema<*>): Schema<*> {
                    val ref = s.`$ref`?.substringAfterLast('/')
                    return if (ref != null) (schemas[ref] ?: s) else s
                }

                fun prune(target: Schema<*>) {
                    target.properties?.keys?.removeAll(names)
                    target.required = target.required?.filterNot { it in names }?.toMutableList()

                    if (target is ComposedSchema) {
                        target.allOf?.forEach { part -> prune(deref(part)) }
                        target.oneOf?.forEach { part -> prune(deref(part)) }
                        target.anyOf?.forEach { part -> prune(deref(part)) }
                    }
                }

                prune(schema)
            }

            AttoSchemas.primitiveMap.forEach { (clazz, schema) ->
                putOrReplace(clazz, schema)
            }

            val attoBlock =
                ensureSchema(AttoBlock::class) { ComposedSchema().description("Base type for all block variants") }

            attoBlock.discriminator =
                Discriminator()
                    .propertyName("type")
                    .mapping(
                        mapOf(
                            "SEND" to "#/components/schemas/${AttoSendBlock::class.simpleName}",
                            "RECEIVE" to "#/components/schemas/${AttoReceivable::class.simpleName}",
                            "OPEN" to "#/components/schemas/${AttoOpenBlock::class.simpleName}",
                            "CHANGE" to "#/components/schemas/${AttoChangeBlock::class.simpleName}",
                        ),
                    )

            findSchema(AttoSendBlock::class)?.let { s ->
                setPropDesc(s, "height", "Height of the block (unsigned 64-bit)", "2")
                setPropDesc(s, "previous", "Hash of the previous block (hex)")
                setPropDesc(s, "receiverAlgorithm", "Algorithm of the receiver", "V1")
                setPropDesc(s, "receiverPublicKey", "Public key of the receiver (hex)")
                setPropDesc(s, "amount", "Amount being sent (raw uint64)", "1")
                removeProps(s, "isValid", "hash")
            }

            findSchema(AttoReceiveBlock::class)?.let { s ->
                setPropDesc(s, "height", "Height of the block (unsigned 64-bit)", "2")
                setPropDesc(s, "previous", "Hash of the previous block (hex)")
                setPropDesc(s, "sendHashAlgorithm", "Algorithm of the send block", "V1")
                setPropDesc(s, "sendHash", "Hash of the send block (hex)")
                removeProps(s, "isValid", "hash")
            }

            findSchema(AttoOpenBlock::class)?.let { s ->
                setPropDesc(s, "sendHashAlgorithm", "Algorithm of the send block", "V1")
                setPropDesc(s, "sendHash", "Hash of the send block (hex)")
                setPropDesc(s, "representativeAlgorithm", "Algorithm of the representative", "V1")
                setPropDesc(s, "representativePublicKey", "Public key of the representative (hex)")
                removeProps(s, "isValid", "hash")
            }

            findSchema(AttoChangeBlock::class)?.let { s ->
                setPropDesc(s, "height", "Height of the block (unsigned 64-bit)", "2")
                setPropDesc(s, "previous", "Hash of the previous block (hex)")
                setPropDesc(s, "representativeAlgorithm", "Algorithm of the representative", "V1")
                setPropDesc(s, "representativePublicKey", "Public key of the representative (hex)")
                removeProps(s, "isValid", "hash")
            }

            findSchema(AttoBlock::class)?.let { s ->
                removeProps(s, "isValid", "hash")
            }

            findSchema(AttoTransaction::class)?.let { s ->
                removeProps(s, "height", "isValid", "hash", "totalSize")
            }

            findSchema(AttoReceivable::class)?.let { s ->
                setPropDesc(
                    s,
                    "hash",
                    "Sender transaction hash (hex)",
                    "0AF0F63BFE4DBC588F95FC3B154DE848AA9A5DD5604BAC99AE9E21C5EA8B4F64",
                )
                setPropDesc(s, "version", "Version", "0")
                setPropDesc(s, "algorithm", "Algorithm", "V1")
                setPropDesc(
                    s,
                    "publicKey",
                    "Public key of the sender (hex)",
                    "53F1A85D25EDA5021C01A77A2B1BA99CEF9DD5FD912D7465B8B652FDEDB6A4F8",
                )
                setPropDesc(
                    s,
                    "timestamp",
                    "Timestamp of the send transaction (ms since epoch)",
                    1705517157478L,
                )
                setPropDesc(s, "receiverAlgorithm", "Algorithm used by the receiver", "V1")
                setPropDesc(
                    s,
                    "receiverPublicKey",
                    "Public key of the receiver (hex)",
                    "0C400961629D759176F009249A33899440900ABCE275F6C5C01C6F7F37A2C59A",
                )
                setPropDesc(
                    s,
                    "amount",
                    "Amount (raw uint64)",
                    "18000000000000000000",
                )
            }

            findSchema(AccountSearch::class)?.let { s ->
                setPropDesc(
                    s,
                    "addresses",
                    "List of addresses. Example item shown.",
                    listOf("atto://adwmbykpqs3mgbqogizzwm6arokkcmuxium7rbh343drwd2q5om6vj3jrfiyk"),
                )
            }

            findSchema(AttoAccount::class)?.let { s ->
                setPropDesc(
                    s,
                    "publicKey",
                    "The public key of the account (hex)",
                    "45B3B58C26181580EEAFC1791046D54EEC2854BF550A211E2362761077D6590C",
                )
                setPropDesc(s, "network", "Network type", "LIVE")
                setPropDesc(s, "version", "Version", "0")
                setPropDesc(s, "algorithm", "Type", "V1")
                setPropDesc(s, "height", "Height (raw uint64)", "1")
                setPropDesc(s, "balance", "Balance (raw uint64)", "180000000000")
                setPropDesc(
                    s,
                    "lastTransactionHash",
                    "Last transaction hash (hex)",
                    "70F9406609BCB2E3E18F22BD0839C95E5540E95489DC6F24DBF6A1F7CFD83A92",
                )
                setPropDesc(
                    s,
                    "lastTransactionTimestamp",
                    "Timestamp of the last transaction (ms since epoch)",
                    1705517157478L,
                )
                setPropDesc(s, "representativeAlgorithm", "Representative algorithm", "V1")
                setPropDesc(
                    s,
                    "representativePublicKey",
                    "Public key of the representative (hex)",
                    "99E439410A4DDD2A3A8D0B667C7A090286B8553378CF3C7AA806C3E60B6C4CBE",
                )
            }

            findSchema(AttoAccountEntry::class)?.let { s ->
                setPropDesc(
                    s,
                    "hash",
                    "Unique hash of the block (hex)",
                    "68BA42CDD87328380BE32D5AA6DBB86E905B50273D37AF1DE12F47B83A001154",
                )
                setPropDesc(s, "algorithm", "Block algorithm", "V1")
                setPropDesc(
                    s,
                    "publicKey",
                    "Public key of the account (hex)",
                    "FD595851104FDDB2FEBF3739C8006C8AAE9B8A2B1BC390D5FDF07EBDD8583FA1",
                )
                setPropDesc(s, "height", "Block height (raw uint64)", "0")
                setPropDesc(s, "blockType", "Type of block in the account chain", "RECEIVE")
                setPropDesc(s, "subjectAlgorithm", "Algorithm of the subject involved in the transaction", "V1")
                setPropDesc(
                    s,
                    "subjectPublicKey",
                    "Public key of the subject involved in the transaction (hex)",
                    "2EB21717813E7A0E0A7E308B8E2FD8A051F8724F5C5F0047E92E19310C582E3A",
                )
                setPropDesc(s, "previousBalance", "Balance before this block (raw uint64)", "0")
                setPropDesc(s, "balance", "Balance after this block (raw uint64)", "100")
                setPropDesc(
                    s,
                    "timestamp",
                    "Timestamp of the block (ms since epoch)",
                    1704616009211L,
                )
            }

            findSchema(AccountHeightSearch::class)?.let { s ->
                setPropDesc(
                    s,
                    "address",
                    "Address of the account",
                    "atto://adwmbykpqs3mgbqogizzwm6arokkcmuxium7rbh343drwd2q5om6vj3jrfiyk",
                )
                setPropDesc(
                    s,
                    "fromHeight",
                    "From height (inclusive), normally last seen height + 1",
                    "1",
                )
                setPropDesc(
                    s,
                    "toHeight",
                    "To height (inclusive)",
                    "2",
                )
            }

            findSchema(HeightSearch::class)?.let { s ->
                setPropDesc(
                    s,
                    "search",
                    "List of account heights to be searched",
                )
            }

            findSchema(TimeDifferenceResponse::class)?.let { s ->
                setPropDesc(
                    s,
                    "clientInstant",
                    "Client instant (iso)",
                    AttoInstant.now().toString(),
                )
                setPropDesc(
                    s,
                    "serverInstant",
                    "Server instant (iso)",
                    AttoInstant.now().toString(),
                )
                setPropDesc(
                    s,
                    "differenceMillis",
                    "Time difference between server and client instants",
                    100,
                )
            }
        }
}
