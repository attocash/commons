package cash.atto.commons.spring

import cash.atto.commons.AttoHeight
import cash.atto.commons.spring.conversion.AttoR2dbcCustomConversionsAutoConfiguration
import io.mockk.every
import io.mockk.mockk
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.convert.support.DefaultConversionService
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.H2Dialect
import java.math.BigInteger
import java.util.function.Supplier

class AttoR2dbcDialectTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(AttoR2dbcCustomConversionsAutoConfiguration::class.java)

    private fun h2(): ConnectionFactory = ConnectionFactories.get("r2dbc:h2:mem:///atto_dialect")

    @Test
    fun `H2 retains native booleans and the Atto height converter`() {
        runner.withBean(ConnectionFactory::class.java, Supplier { h2() }).run { context ->
            val conversions = context.getBean(R2dbcCustomConversions::class.java)
            assertThat(conversions.hasCustomWriteTarget(Boolean::class.javaObjectType, Byte::class.javaObjectType)).isFalse()

            val service = DefaultConversionService()
            conversions.registerConvertersIn(service)
            assertThat(service.convert(BigInteger("18446744073709551615"), AttoHeight::class.java)).isEqualTo(AttoHeight.MAX)
        }
    }

    @Test
    fun `MySQL keeps its boolean to byte converter`() {
        val mysql = mockk<ConnectionFactory>()
        every { mysql.metadata } returns ConnectionFactoryMetadata { "MySQL" }

        runner.withBean(ConnectionFactory::class.java, Supplier { mysql }).run { context ->
            val conversions = context.getBean(R2dbcCustomConversions::class.java)
            assertThat(conversions.hasCustomWriteTarget(Boolean::class.javaObjectType, Byte::class.javaObjectType)).isTrue()
        }
    }

    @Test
    fun `ambiguous factories leave conversion selection to the application`() {
        runner
            .withBean("first", ConnectionFactory::class.java, Supplier { h2() })
            .withBean("second", ConnectionFactory::class.java, Supplier { h2() })
            .run { context ->
                assertThat(context).doesNotHaveBean(R2dbcCustomConversions::class.java)
            }
    }

    @Test
    fun `application supplied conversions take precedence`() {
        val supplied = R2dbcCustomConversions.of(H2Dialect.INSTANCE, emptyList<Any>())
        runner
            .withBean(ConnectionFactory::class.java, Supplier { h2() })
            .withBean(R2dbcCustomConversions::class.java, Supplier { supplied })
            .run { context ->
                assertThat(context.getBean(R2dbcCustomConversions::class.java)).isSameAs(supplied)
            }
    }
}
