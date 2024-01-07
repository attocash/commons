package cash.atto.commons.serialiazers.json

import cash.atto.commons.AttoHash
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoWork
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule


val attoJsonSerializersModule = SerializersModule {
    contextual(AttoHash::class, AttoHashJsonSerializer)
    contextual(AttoPublicKey::class, AttoPublicKeyJsonSerializer)
    contextual(AttoSignature::class, AttoSignatureJsonSerializer)
    contextual(AttoWork::class, AttoWorkJsonSerializer)
}

val AttoJson = Json {
    serializersModule = attoJsonSerializersModule
}