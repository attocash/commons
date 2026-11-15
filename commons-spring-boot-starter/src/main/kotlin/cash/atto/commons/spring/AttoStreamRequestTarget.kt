package cash.atto.commons.spring

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.util.UriUtils
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Parses a relative HTTP operation target and decodes each path segment exactly once. */
class AttoStreamRequestTarget(
    path: String,
) {
    val segments: List<String>
    private val queryValues: Map<String, List<String>>

    init {
        try {
            val uri = URI(path)
            require(!uri.isAbsolute && uri.rawAuthority == null && uri.rawFragment == null && path.startsWith('/'))
            segments =
                uri.rawPath
                    .removePrefix("/")
                    .split('/')
                    .map { UriUtils.decode(it, StandardCharsets.UTF_8) }
            queryValues =
                uri.rawQuery
                    ?.split('&')
                    ?.map { item ->
                        val parts = item.split('=', limit = 2)
                        URLDecoder.decode(parts[0], StandardCharsets.UTF_8) to
                            URLDecoder.decode(parts.getOrElse(1) { "" }, StandardCharsets.UTF_8)
                    }?.groupBy({ it.first }, { it.second }) ?: emptyMap()
        } catch (exception: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stream operation path", exception)
        } catch (exception: java.net.URISyntaxException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stream operation path", exception)
        }
    }

    fun query(name: String): String? = queryValues(name).firstOrNull()

    fun queryValues(name: String): List<String> = queryValues[name].orEmpty()
}
