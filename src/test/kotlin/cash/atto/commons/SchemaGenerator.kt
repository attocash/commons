package cash.atto.commons

import cash.atto.commons.serialiazers.AttoTransactionAsByteArraySerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.schema.ProtoBufSchemaGenerator

val descriptors =
    arrayListOf(
        TransactionHolder.serializer().descriptor,
    )

@Serializable
private data class TransactionHolder(
    @Serializable(with = AttoTransactionAsByteArraySerializer::class) val transaction: AttoTransaction,
)

@OptIn(ExperimentalSerializationApi::class)
fun main() {
    println(ProtoBufSchemaGenerator.generateSchemaText(descriptors))
}
