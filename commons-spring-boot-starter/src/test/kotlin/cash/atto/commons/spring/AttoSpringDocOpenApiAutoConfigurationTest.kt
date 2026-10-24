package cash.atto.commons.spring

import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChangeBlock
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoSendBlock
import cash.atto.commons.AttoVersion
import cash.atto.commons.spring.schema.AttoSchemas
import cash.atto.commons.spring.schema.AttoSpringDocOpenApiAutoConfiguration
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
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
        customizer.customise(openApi)

        val blockSchema = openApi.components.schemas[AttoBlock::class.simpleName]!!
        val mapping = blockSchema.discriminator.mapping

        assertThat(mapping)
            .containsEntry("SEND", "#/components/schemas/${AttoSendBlock::class.simpleName}")
            .containsEntry("RECEIVE", "#/components/schemas/${AttoReceiveBlock::class.simpleName}")
            .containsEntry("OPEN", "#/components/schemas/${AttoOpenBlock::class.simpleName}")
            .containsEntry("CHANGE", "#/components/schemas/${AttoChangeBlock::class.simpleName}")
    }
}
