package cash.atto.commons.node

internal class AttoWorkerMockSettings {
    var name: String? = null
    var image: String? = null
    var pullImage: Boolean? = null
    var logOutput: Boolean? = null

    fun configuration(): AttoWorkerMockConfiguration {
        val defaults = AttoWorkerMockConfiguration()
        return defaults.copy(
            name = name ?: defaults.name,
            image = image ?: defaults.image,
            pullImage = pullImage ?: defaults.pullImage,
            logOutput = logOutput ?: defaults.logOutput,
        )
    }
}
