package cash.atto.commons.spring

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoVote
import cash.atto.commons.AttoVoterWeight
import cash.atto.commons.spring.schema.AttoSpringDocOpenApiAutoConfiguration
import com.fasterxml.jackson.databind.JsonNode
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.Json31
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AttoGeneratedOpenApiTest {
    @Test
    fun `generated canonical models match serialized records and resolve every reference`() {
        // Given
        val schemas = mutableMapOf<String, Schema<*>>()
        listOf(
            AttoTransaction::class,
            AttoAccount::class,
            AttoAccountEntry::class,
            AttoReceivable::class,
            AttoVote::class,
            AttoVoterWeight::class,
        ).forEach { model ->
            schemas.putAll(ModelConverters.getInstance().readAll(AnnotatedType(model.java)))
        }
        val openApi = OpenAPI().components(Components().schemas(schemas))
        val commonBlockFields = setOf("type", "network", "version", "height", "balance", "timestamp", "address")
        val variantFields =
            mapOf(
                "AttoSendBlock" to setOf("previous", "receiverAddress", "amount"),
                "AttoReceiveBlock" to setOf("previous", "sendHashAlgorithm", "sendHash"),
                "AttoOpenBlock" to setOf("sendHashAlgorithm", "sendHash", "representativeAddress"),
                "AttoChangeBlock" to setOf("previous", "representativeAddress"),
            )

        // When
        AttoSpringDocOpenApiAutoConfiguration().attoOpenApiCustomizer().customise(openApi)
        val document = Json.mapper().valueToTree<JsonNode>(openApi)
        val document31 = Json31.mapper().valueToTree<JsonNode>(openApi)

        // Then
        assertThat(schemas.getValue("AttoBlock").properties.keys).containsExactlyInAnyOrderElementsOf(commonBlockFields)
        assertThat(schemas.getValue("AttoBlock").required).containsExactlyInAnyOrderElementsOf(commonBlockFields)
        assertThat(
            schemas
                .getValue("AttoBlock")
                .properties
                .getValue("type")
                .enum
                .map { it.toString() },
        ).containsExactlyInAnyOrder("SEND", "RECEIVE", "OPEN", "CHANGE")
        variantFields.forEach { (name, extraFields) ->
            val schema = schemas.getValue(name)
            assertThat(schema.properties.keys).containsExactlyInAnyOrderElementsOf(commonBlockFields + extraFields)
            assertThat(schema.required).containsExactlyInAnyOrderElementsOf(commonBlockFields + extraFields)
            assertThat(
                schema.properties.getValue("type").enum.map {
                    it.toString()
                },
            ).containsExactly(name.removePrefix("Atto").removeSuffix("Block").uppercase())
            assertThat(schema.allOf).isNullOrEmpty()
            assertThat(schema.oneOf).isNullOrEmpty()
        }
        assertThat(schemas.getValue("AttoTransaction").properties).containsOnlyKeys("block", "signature", "work")
        assertThat(schemas.getValue("AttoTransaction").required).containsExactlyInAnyOrder("block", "signature", "work")
        assertThat(
            schemas.getValue("AttoVote").properties,
        ).containsOnlyKeys("version", "address", "blockAlgorithm", "blockHash", "timestamp")
        assertThat(
            schemas.getValue("AttoVote").required,
        ).containsExactlyInAnyOrder("version", "address", "blockAlgorithm", "blockHash", "timestamp")
        assertThat(schemas.getValue("AttoBlock").oneOf).isNullOrEmpty()
        document.findValues("\$ref").forEach { reference ->
            assertThat(schemas).containsKey(reference.asText().substringAfterLast('/'))
        }
        schemas.getValue("AttoBlock").discriminator.mapping.values.forEach { reference ->
            assertThat(schemas).containsKey(reference.substringAfterLast('/'))
        }
        val canonicalNames =
            variantFields.keys +
                setOf(
                    "AttoBlock",
                    "AttoAccount",
                    "AttoAccountEntry",
                    "AttoReceivable",
                    "AttoTransaction",
                    "AttoVote",
                    "AttoVoterWeight",
                )
        canonicalNames.forEach { name ->
            assertThat(
                document
                    .path("components")
                    .path("schemas")
                    .path(name)
                    .path("type")
                    .asText(),
            ).describedAs("OpenAPI 3.0 object type for %s", name)
                .isEqualTo("object")
            assertThat(
                document31
                    .path("components")
                    .path("schemas")
                    .path(name)
                    .path("type")
                    .asText(),
            ).describedAs("OpenAPI 3.1 object type for %s", name)
                .isEqualTo("object")
            assertThat(schemas.getValue(name).properties.keys).doesNotContain(
                "algorithm",
                "publicKey",
                "receiverAlgorithm",
                "receiverPublicKey",
                "representativeAlgorithm",
                "representativePublicKey",
                "subjectAlgorithm",
                "subjectPublicKey",
                "valid",
                "isValid",
                "target",
                "final",
                "totalSize",
            )
        }
    }
}
