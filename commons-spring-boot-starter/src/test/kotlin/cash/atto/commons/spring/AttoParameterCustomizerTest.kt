package cash.atto.commons.spring

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAmount
import cash.atto.commons.spring.schema.AttoSchemas
import cash.atto.commons.spring.schema.AttoSpringDocOpenApiAutoConfiguration
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter

class AttoParameterCustomizerTest {
    private val customizer = AttoSpringDocOpenApiAutoConfiguration().attoParameterCustomizer()

    @Test
    fun `should use Atto primitive schema for scalar parameters`() {
        // Given
        val parameter = Parameter().schema(StringSchema().description("original"))

        // When
        val customized = customizer.customize(parameter, methodParameter("amount"))

        // Then
        assertThat(customized).isSameAs(parameter)
        assertThat(customized.schema).isSameAs(AttoSchemas.primitiveMap.getValue(AttoAmount::class))
    }

    @Test
    fun `should describe collections with Atto primitive item schemas`() {
        // Given
        val parameter = Parameter().schema(StringSchema().description("original"))

        // When
        val customized = customizer.customize(parameter, methodParameter("amounts"))

        // Then
        assertThat(customized).isSameAs(parameter)
        assertThat(customized.schema).isInstanceOf(ArraySchema::class.java)
        assertThat(customized.schema.items).isSameAs(AttoSchemas.primitiveMap.getValue(AttoAmount::class))
    }

    @Test
    fun `should preserve existing constraints and annotations on Atto collection schemas`() {
        // Given
        val originalSchema =
            ArraySchema().apply {
                items = IntegerSchema()
                minItems = 1
                maxItems = 3
                uniqueItems = true
                description = "annotated collection"
                setDefault(listOf("1"))
                extensions = mutableMapOf("x-collection" to "annotation value")
            }
        val parameter = Parameter().schema(originalSchema)

        // When
        val customized = customizer.customize(parameter, methodParameter("amounts"))

        // Then
        assertThat(customized.schema).isSameAs(originalSchema)
        assertThat(customized.schema.items).isSameAs(AttoSchemas.primitiveMap.getValue(AttoAmount::class))
        assertThat(customized.schema.minItems).isEqualTo(1)
        assertThat(customized.schema.maxItems).isEqualTo(3)
        assertThat(customized.schema.uniqueItems).isTrue()
        assertThat(customized.schema.description).isEqualTo("annotated collection")
        assertThat(customized.schema.default).isEqualTo(listOf("1"))
        assertThat(customized.schema.extensions).containsEntry("x-collection", "annotation value")
    }

    @Test
    fun `should preserve uniqueness of Atto set schemas`() {
        // Given
        val originalSchema = ArraySchema().items(StringSchema()).uniqueItems(true)
        val parameter = Parameter().schema(originalSchema)

        // When
        val customized = customizer.customize(parameter, methodParameter("addresses"))

        // Then
        assertThat(customized.schema).isSameAs(originalSchema)
        assertThat(customized.schema.uniqueItems).isTrue()
        assertThat(customized.schema.items).isSameAs(AttoSchemas.primitiveMap.getValue(AttoAddress::class))
    }

    @Test
    fun `should preserve nullable OpenAPI 3_1 array schemas for Atto collections`() {
        // Given
        val originalSchema =
            Schema<Any>().apply {
                types = setOf("array", "null")
                items = IntegerSchema()
                maxItems = 3
                description = "nullable collection"
            }
        val parameter = Parameter().schema(originalSchema)

        // When
        val customized = customizer.customize(parameter, methodParameter("amounts"))

        // Then
        assertThat(customized.schema).isSameAs(originalSchema)
        assertThat(customized.schema.types).containsExactlyInAnyOrder("array", "null")
        assertThat(customized.schema.items).isSameAs(AttoSchemas.primitiveMap.getValue(AttoAmount::class))
        assertThat(customized.schema.maxItems).isEqualTo(3)
        assertThat(customized.schema.description).isEqualTo("nullable collection")
    }

    @Test
    fun `should preserve numeric collection schemas`() {
        // Given
        val originalSchema = ArraySchema().items(IntegerSchema())
        val parameter = Parameter().schema(originalSchema)

        // When
        val customized = customizer.customize(parameter, methodParameter("numbers"))

        // Then
        assertThat(customized.schema).isSameAs(originalSchema)
        assertThat(customized.schema.items).isInstanceOf(IntegerSchema::class.java)
    }

    @Test
    fun `should preserve custom schemas for unrelated collection items`() {
        // Given
        val originalSchema =
            ArraySchema()
                .items(StringSchema()._enum(listOf("custom option")))
                .description("custom description")
                .minItems(2)
        val parameter = Parameter().schema(originalSchema)

        // When
        val customized = customizer.customize(parameter, methodParameter("choices"))

        // Then
        assertThat(customized.schema).isSameAs(originalSchema)
    }

    @Test
    fun `should preserve schemas when collection item type is unknown`() {
        // Given
        val originalSchema = ArraySchema().items(IntegerSchema())
        val parameter = Parameter().schema(originalSchema)

        // When
        val customized = customizer.customize(parameter, methodParameter("unknownItems"))

        // Then
        assertThat(customized.schema).isSameAs(originalSchema)
    }

    @Test
    fun `should preserve schemas of unrelated scalar parameters`() {
        // Given
        val originalSchema = StringSchema().description("custom description").example("custom example")
        val parameter = Parameter().schema(originalSchema)

        // When
        val customized = customizer.customize(parameter, methodParameter("string"))

        // Then
        assertThat(customized).isSameAs(parameter)
        assertThat(customized.schema).isSameAs(originalSchema)
    }

    @Test
    fun `should preserve parameters without resolved method metadata`() {
        // Given
        val originalSchema = StringSchema().description("custom description")
        val parameter = Parameter().schema(originalSchema)

        // When
        val customized = customizer.customize(parameter, null)

        // Then
        assertThat(customized).isSameAs(parameter)
        assertThat(customized.schema).isSameAs(originalSchema)
    }

    private fun methodParameter(methodName: String): MethodParameter =
        MethodParameter(ParameterTypes::class.java.declaredMethods.single { it.name == methodName }, 0)

    private class ParameterTypes {
        fun amount(value: AttoAmount): AttoAmount = value

        fun amounts(values: List<AttoAmount>): List<AttoAmount> = values

        fun addresses(values: Set<AttoAddress>): Set<AttoAddress> = values

        fun numbers(values: List<Int>): List<Int> = values

        fun choices(values: Collection<Choice>): Collection<Choice> = values

        fun unknownItems(values: Collection<*>): Collection<*> = values

        fun string(value: String): String = value
    }

    private enum class Choice {
        FIRST,
        SECOND,
    }
}
