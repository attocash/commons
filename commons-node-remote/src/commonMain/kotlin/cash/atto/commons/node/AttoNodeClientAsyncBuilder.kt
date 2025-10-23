package cash.atto.commons.node

expect class AttoNodeClientAsyncBuilder private constructor(
    url: String,
) {
    val url: String
    var headers: Map<String, String>

    companion object {
        fun remote(url: String): AttoNodeClientAsyncBuilder
    }

    fun headers(value: Map<String, String>): AttoNodeClientAsyncBuilder

    fun header(
        name: String,
        value: String,
    ): AttoNodeClientAsyncBuilder

    fun build(): AttoNodeClientAsync
}
