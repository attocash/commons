package cash.atto.commons.spring.conversion

import io.r2dbc.spi.ConnectionFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.support.ConfigurableConversionService
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions

@Configuration
@ConditionalOnClass(R2dbcCustomConversions::class, ConnectionFactory::class)
class AttoConversionsAutoConfiguration {
    @Bean
    fun attoConvertersRegistrar(conversionService: ObjectProvider<ConfigurableConversionService>) =
        SmartInitializingSingleton {
            conversionService.ifAvailable { service ->
                AttoConverters.all.forEach { converter ->
                    service.addConverter(converter)
                }
            }
        }
}
