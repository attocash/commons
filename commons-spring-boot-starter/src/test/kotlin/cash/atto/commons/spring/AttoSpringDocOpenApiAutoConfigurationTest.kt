package cash.atto.commons.spring

import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChangeBlock
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoSendBlock
import cash.atto.commons.spring.schema.AttoSpringDocOpenApiAutoConfiguration
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AttoSpringDocOpenApiAutoConfigurationTest {
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
