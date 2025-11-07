package cash.atto.commons.node

import cash.atto.commons.utils.JsExportForJs

@JsExportForJs
expect class AttoNodeClientAsyncBuilder private constructor(
    url: String,
) {
    fun headers(value: Map<String, String>): AttoNodeClientAsyncBuilder

    fun header(
        name: String,
        value: String,
    ): AttoNodeClientAsyncBuilder

    fun build(): AttoNodeClientAsync
}
