package cash.atto.commons

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun String.canonicalIdentityJson(): String {
    val fields = Json.parseToJsonElement(this).jsonObject.toMutableMap()
    listOf("" to "address", "receiver" to "receiverAddress", "representative" to "representativeAddress", "subject" to "subjectAddress")
        .forEach { (prefix, addressField) ->
            val algorithmField = if (prefix.isEmpty()) "algorithm" else "${prefix}Algorithm"
            val publicKeyField = if (prefix.isEmpty()) "publicKey" else "${prefix}PublicKey"
            if (addressField !in fields && algorithmField in fields && publicKeyField in fields) {
                val algorithm = AttoAlgorithm.valueOf(fields.getValue(algorithmField).jsonPrimitive.content)
                val publicKey =
                    AttoPublicKey(
                        fields
                            .getValue(publicKeyField)
                            .jsonPrimitive.content
                            .fromHexToByteArray(),
                    )
                fields[addressField] = JsonPrimitive(AttoAddress(algorithm, publicKey).toString())
            }
            fields.remove(algorithmField)
            fields.remove(publicKeyField)
        }
    return JsonObject(fields.entries.sortedBy { it.key }.associate { it.toPair() }).toString()
}
