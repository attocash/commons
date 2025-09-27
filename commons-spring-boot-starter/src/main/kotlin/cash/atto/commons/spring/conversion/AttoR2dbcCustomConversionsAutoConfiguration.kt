package cash.atto.commons.spring.conversion

import io.r2dbc.spi.ConnectionFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions
import org.springframework.data.r2dbc.dialect.MySqlDialect

@Configuration
@ConditionalOnClass(R2dbcCustomConversions::class, ConnectionFactory::class)
class AttoR2dbcCustomConversionsAutoConfiguration {
    @Bean
    @ConditionalOnBean(ConnectionFactory::class)
    @ConditionalOnMissingBean(R2dbcCustomConversions::class)
    fun attoR2dbcCustomConversions(): R2dbcCustomConversions = R2dbcCustomConversions.of(MySqlDialect.INSTANCE, AttoConverters.all)
}
