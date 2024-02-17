package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.schema.ProtoBufSchemaGenerator

val descriptors = arrayListOf(
    AttoAccount.serializer().descriptor,
    AttoTransaction.serializer().descriptor,
    AttoReceivable.serializer().descriptor,
)

@OptIn(ExperimentalSerializationApi::class)
fun main() {
    println(ProtoBufSchemaGenerator.generateSchemaText(descriptors))
}