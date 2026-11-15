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
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.ParameterCustomizer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.core.ResolvableType

@AutoConfiguration
@ConditionalOnClass(OpenApiCustomizer::class)
class AttoSpringDocOpenApiAutoConfiguration {
    @Bean
    fun attoParameterCustomizer(): ParameterCustomizer =
        ParameterCustomizer { parameter, resolved ->
            val param = resolved ?: return@ParameterCustomizer parameter

            val rawType = param.parameterType
            val schema: Schema<*>? =
                if (Collection::class.java.isAssignableFrom(rawType)) {
                    val itemType =
                        ResolvableType
                            .forMethodParameter(param)
                            .asCollection()
                            .generics
                            .firstOrNull()
                            ?.resolve()
                    AttoSchemas.primitiveMap[itemType?.kotlin]?.let { itemSchema ->
                        val arraySchema =
                            parameter.schema?.takeIf {
                                it is ArraySchema || it.type == "array" || it.types?.contains("array") == true
                            } ?: ArraySchema()
                        arraySchema.apply { items = itemSchema }
                    }
                } else {
                    AttoSchemas.primitiveMap[rawType.kotlin]
                }
            schema?.let { parameter.schema = it }

            parameter
        }

    @Bean
    fun attoOpenApiCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            val schemas = openApi.components?.schemas ?: return@OpenApiCustomizer

            with(OpenApiSchemaDocument(schemas)) {
                AttoSchemas.primitiveMap.forEach { (clazz, schema) ->
                    putOrReplace(clazz, schema)
                }

                val attoBlock =
                    ensureSchema(AttoBlock::class) { ComposedSchema().description("Base type for all block variants") }

                AttoSerializedModelSchemas(schemas).customize()

                attoBlock.discriminator =
                    Discriminator()
                        .propertyName("type")
                        .mapping(
                            mapOf(
                                "SEND" to "#/components/schemas/${AttoSendBlock::class.simpleName}",
                                "RECEIVE" to "#/components/schemas/${AttoReceiveBlock::class.simpleName}",
                                "OPEN" to "#/components/schemas/${AttoOpenBlock::class.simpleName}",
                                "CHANGE" to "#/components/schemas/${AttoChangeBlock::class.simpleName}",
                            ),
                        )

                findSchema(AttoSendBlock::class)?.let { s ->
                    setPropDesc(s, "height", "Height of the block (unsigned 64-bit)", "2")
                    setPropDesc(s, "previous", "Hash of the previous block (hex)")
                    setPropDesc(s, "receiverAddress", "Receiver address")
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
                    setPropDesc(s, "representativeAddress", "Representative address")
                    removeProps(s, "isValid", "hash")
                }

                findSchema(AttoChangeBlock::class)?.let { s ->
                    setPropDesc(s, "height", "Height of the block (unsigned 64-bit)", "2")
                    setPropDesc(s, "previous", "Hash of the previous block (hex)")
                    setPropDesc(s, "representativeAddress", "Representative address")
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
                    setPropDesc(s, "address", "Account address")
                    setPropDesc(
                        s,
                        "timestamp",
                        "Timestamp of the send transaction (ms since epoch)",
                        1705517157478L,
                    )
                    setPropDesc(s, "receiverAddress", "Receiver address")
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
                    setPropDesc(s, "address", "Account address")
                    setPropDesc(s, "network", "Network type", "LIVE")
                    setPropDesc(s, "version", "Version", "0")
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
                    setPropDesc(s, "representativeAddress", "Representative address")
                }

                findSchema(AttoAccountEntry::class)?.let { s ->
                    setPropDesc(
                        s,
                        "hash",
                        "Unique hash of the block (hex)",
                        "68BA42CDD87328380BE32D5AA6DBB86E905B50273D37AF1DE12F47B83A001154",
                    )
                    setPropDesc(s, "address", "Account address")
                    setPropDesc(s, "height", "Block height (raw uint64)", "0")
                    setPropDesc(s, "blockType", "Type of block in the account chain", "RECEIVE")
                    setPropDesc(s, "subjectAddress", "Subject address")
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
}
