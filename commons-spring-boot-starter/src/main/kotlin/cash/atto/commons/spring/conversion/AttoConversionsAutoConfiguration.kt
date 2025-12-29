package cash.atto.commons.spring.conversion

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.support.ConfigurableConversionService

@Configuration
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
