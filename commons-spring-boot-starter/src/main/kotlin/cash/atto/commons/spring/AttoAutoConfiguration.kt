package cash.atto.commons.spring

import kotlinx.serialization.json.Json
import org.springframework.boot.web.codec.CodecCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.json.KotlinSerializationJsonDecoder
import org.springframework.http.codec.json.KotlinSerializationJsonEncoder

@Configuration
class AttoAutoConfiguration {

    @Bean
    fun kotlinxCodecCustomizer(): CodecCustomizer {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
        return CodecCustomizer { configurer ->
            configurer.defaultCodecs()
                .kotlinSerializationJsonDecoder(KotlinSerializationJsonDecoder(json))
            configurer.defaultCodecs()
                .kotlinSerializationJsonEncoder(KotlinSerializationJsonEncoder(json))
        }
    }
}
