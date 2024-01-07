package cash.atto.commons.serialiazers.protobuf

import cash.atto.commons.AttoHash
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoWork
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.ProtoBuf


val attoProtobufSerializersModule = SerializersModule {
    contextual(AttoHash::class, AttoHashProtobufSerializer)
    contextual(AttoPublicKey::class, AttoPublicKeyProtobufSerializer)
    contextual(AttoSignature::class, AttoSignatureProtobufSerializer)
    contextual(AttoWork::class, AttoWorkProtobufSerializer)
}

@OptIn(ExperimentalSerializationApi::class)
val AttoProtobuf = ProtoBuf {
    serializersModule = attoProtobufSerializersModule
}