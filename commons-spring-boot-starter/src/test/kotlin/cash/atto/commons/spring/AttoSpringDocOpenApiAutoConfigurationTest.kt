package cash.atto.commons.spring

import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChangeBlock
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoSendBlock
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoVersion
import cash.atto.commons.spring.schema.AttoSchemas
import cash.atto.commons.spring.schema.AttoSpringDocOpenApiAutoConfiguration
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AttoSpringDocOpenApiAutoConfigurationTest {
    @Test
    fun `should describe large numeric values as decimal strings`() {
        // Given
        val schemas = AttoSchemas.primitiveMap

        // When
        val amount = schemas.getValue(AttoAmount::class)
        val height = schemas.getValue(AttoHeight::class)
        val instant = schemas.getValue(AttoInstant::class)
        val version = schemas.getValue(AttoVersion::class)

        // Then
        assertThat(amount.type).isEqualTo("string")
        assertThat(amount.pattern).isEqualTo("^[0-9]{1,20}$")
        assertThat(height.type).isEqualTo("string")
        assertThat(height.pattern).isEqualTo("^[0-9]{1,20}$")
        assertThat(instant.type).isEqualTo("string")
        assertThat(instant.pattern).isEqualTo("^-?[0-9]{1,19}$")
        assertThat(Regex(instant.pattern).matches(Long.MIN_VALUE.toString())).isTrue()
        assertThat(Regex(instant.pattern).matches(Long.MAX_VALUE.toString())).isTrue()
        assertThat(version.type).isEqualTo("integer")
    }

    @Test
    fun `should map block discriminator values to block schemas`() {
        // Given
        val openApi =
            OpenAPI().components(
                Components().schemas(
                    mutableMapOf(
                        AttoBlock::class.simpleName!! to ComposedSchema(),
                        AttoSendBlock::class.simpleName!! to Schema<Any>(),
                        AttoReceiveBlock::class.simpleName!! to Schema<Any>(),
                        AttoOpenBlock::class.simpleName!! to Schema<Any>(),
                        AttoChangeBlock::class.simpleName!! to Schema<Any>(),
                    ),
                ),
            )

        val customizer = AttoSpringDocOpenApiAutoConfiguration().attoOpenApiCustomizer()
        // When
        customizer.customise(openApi)

        // Then
        val blockSchema = openApi.components.schemas[AttoBlock::class.simpleName]!!
        val mapping = blockSchema.discriminator.mapping

        assertThat(mapping)
            .containsEntry("SEND", "#/components/schemas/${AttoSendBlock::class.simpleName}")
            .containsEntry("RECEIVE", "#/components/schemas/${AttoReceiveBlock::class.simpleName}")
            .containsEntry("OPEN", "#/components/schemas/${AttoOpenBlock::class.simpleName}")
            .containsEntry("CHANGE", "#/components/schemas/${AttoChangeBlock::class.simpleName}")
    }

    @Test
    fun `should update direct property descriptions and preserve unspecified examples`() {
        // Given
        val height = StringSchema()
        val previous = StringSchema().example("existing hash")
        val send =
            Schema<Any>().apply {
                properties = mutableMapOf("height" to height, "previous" to previous)
            }
        val openApi =
            OpenAPI().components(
                Components().schemas(mutableMapOf(AttoSendBlock::class.simpleName!! to send)),
            )

        // When
        AttoSpringDocOpenApiAutoConfiguration().attoOpenApiCustomizer().customise(openApi)

        // Then
        assertThat(height.description).isEqualTo("Height of the block (unsigned 64-bit)")
        assertThat(height.example).isEqualTo("2")
        assertThat(previous.description).isEqualTo("Hash of the previous block (hex)")
        assertThat(previous.example).isEqualTo("existing hash")
    }

    @Test
    fun `should update properties in referenced and inline allOf parts`() {
        // Given
        val height = StringSchema()
        val amount = StringSchema()
        val base = Schema<Any>().apply { properties = mutableMapOf("height" to height) }
        val inline = Schema<Any>().apply { properties = mutableMapOf("amount" to amount) }
        val send =
            ComposedSchema().apply {
                allOf = mutableListOf(Schema<Any>().`$ref`("#/components/schemas/Base"), inline)
            }
        val openApi =
            OpenAPI().components(
                Components().schemas(
                    mutableMapOf("Base" to base, AttoSendBlock::class.simpleName!! to send),
                ),
            )

        // When
        AttoSpringDocOpenApiAutoConfiguration().attoOpenApiCustomizer().customise(openApi)

        // Then
        assertThat(height.description).isEqualTo("Height of the block (unsigned 64-bit)")
        assertThat(height.example).isEqualTo("2")
        assertThat(amount.description).isEqualTo("Amount being sent (raw uint64)")
        assertThat(amount.example).isEqualTo("1")
    }

    @Test
    fun `should remove computed properties and requirements throughout composed schemas`() {
        // Given
        val referenced = transactionProperties()
        val alternative = transactionProperties()
        val nested = transactionProperties()
        val choice = ComposedSchema().apply { anyOf = mutableListOf(nested) }
        val transaction =
            ComposedSchema().apply {
                properties = transactionProperties().properties
                required = mutableListOf("height", "isValid", "hash", "totalSize", "block")
                allOf = mutableListOf(Schema<Any>().`$ref`("#/components/schemas/Base"))
                oneOf = mutableListOf(alternative, choice)
            }
        val openApi =
            OpenAPI().components(
                Components().schemas(
                    mutableMapOf("Base" to referenced, AttoTransaction::class.simpleName!! to transaction),
                ),
            )

        // When
        AttoSpringDocOpenApiAutoConfiguration().attoOpenApiCustomizer().customise(openApi)

        // Then
        listOf(transaction, referenced, alternative, nested).forEach { schema ->
            assertThat(schema.properties).containsOnlyKeys("block")
            assertThat(schema.required).containsExactly("block")
        }
    }

    @Test
    fun `should register primitives and create missing base block schema`() {
        // Given
        val originalAmount = Schema<Any>()
        val unrelated = Schema<Any>()
        val openApi =
            OpenAPI().components(
                Components().schemas(
                    mutableMapOf(AttoAmount::class.simpleName!! to originalAmount, "Unrelated" to unrelated),
                ),
            )

        // When
        AttoSpringDocOpenApiAutoConfiguration().attoOpenApiCustomizer().customise(openApi)

        // Then
        val schemas = openApi.components.schemas
        AttoSchemas.primitiveMap.forEach { (type, schema) ->
            assertThat(schemas[type.simpleName]).isSameAs(schema)
        }
        assertThat(schemas["Unrelated"]).isSameAs(unrelated)
        assertThat(schemas[AttoBlock::class.simpleName]?.description).isEqualTo("Base type for all block variants")
        assertThat(schemas[AttoBlock::class.simpleName]?.discriminator?.propertyName).isEqualTo("type")
    }

    @Test
    fun `should leave documents without component schemas unchanged`() {
        // Given
        val withoutComponents = OpenAPI()
        val withoutSchemas = OpenAPI().components(Components())
        val customizer = AttoSpringDocOpenApiAutoConfiguration().attoOpenApiCustomizer()

        // When
        customizer.customise(withoutComponents)
        customizer.customise(withoutSchemas)

        // Then
        assertThat(withoutComponents.components).isNull()
        assertThat(withoutSchemas.components.schemas).isNull()
    }

    private fun transactionProperties(): Schema<Any> =
        Schema<Any>().apply {
            properties =
                mutableMapOf(
                    "height" to StringSchema(),
                    "isValid" to StringSchema(),
                    "hash" to StringSchema(),
                    "totalSize" to StringSchema(),
                    "block" to Schema<Any>(),
                )
            required = mutableListOf("height", "isValid", "hash", "totalSize", "block")
        }
}
