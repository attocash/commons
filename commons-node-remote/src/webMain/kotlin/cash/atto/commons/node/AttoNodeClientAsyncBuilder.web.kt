package cash.atto.commons.node

actual class AttoNodeClientAsyncBuilder actual constructor(
    private val url: String,
) {
    private var headers: Map<String, String> = emptyMap()

    actual companion object {
        actual fun remote(url: String): AttoNodeClientAsyncBuilder = AttoNodeClientAsyncBuilder(url)
    }

    actual fun headers(value: Map<String, String>) = apply { headers = value }

    actual fun header(
        name: String,
        value: String,
    ) = apply { headers = headers + (name to value) }

    actual fun build(): AttoNodeClientAsync = AttoNodeClient.remote(url, { headers }).toAsync()
}
