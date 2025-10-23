package cash.atto.commons.node

interface AttoConsumer<T> {
    fun consume(value: T)
}
