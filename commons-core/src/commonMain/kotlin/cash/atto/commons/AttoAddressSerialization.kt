@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

internal fun resolveSerializedAddress(
    field: String,
    address: AttoAddress?,
    algorithm: AttoAlgorithm?,
    publicKey: AttoPublicKey?,
): AttoAddress {
    if (address != null) {
        return address
    }

    require(algorithm != null && publicKey != null) { "$field requires an address or both algorithm and publicKey" }
    return AttoAddress(algorithm, publicKey)
}

internal fun <T> decodeWithSerializedProjections(
    decoder: Decoder,
    serializer: KSerializer<T>,
    addressFields: Map<String, List<String>>,
    ignoredFields: Set<String> = emptySet(),
): T {
    if (decoder !is JsonDecoder) {
        return decoder.decodeSerializableValue(serializer)
    }

    val element = decoder.decodeJsonElement()
    if (element !is JsonObject) {
        return decoder.json.decodeFromJsonElement(serializer, element)
    }

    val descriptor = serializer.descriptor
    val namingStrategy = decoder.json.configuration.namingStrategy

    fun jsonFieldName(name: String): String = namingStrategy?.serialNameForJson(descriptor, descriptor.getElementIndex(name), name) ?: name

    val fields = element.toMutableMap()
    val declaredFields = (0 until descriptor.elementsCount).map { jsonFieldName(descriptor.getElementName(it)) }
    val discriminator = decoder.json.configuration.classDiscriminator
    if (discriminator !in declaredFields) {
        fields.remove(discriminator)
    }
    fields.keys.removeAll(ignoredFields.map(::jsonFieldName).toSet())
    // Discard projections before typed decoding can reject their contents.
    for ((addressField, projections) in addressFields) {
        if (jsonFieldName(addressField) in fields) {
            fields.keys.removeAll(projections.map(::jsonFieldName).toSet())
        }
    }
    return decoder.json.decodeFromJsonElement(serializer, JsonObject(fields))
}
