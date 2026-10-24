@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AttoAddressSerializationConfigurationTest {
    private val send = AttoBlockTest.sendBlock
    private val receivable = send.toReceivable()
    private val account =
        AttoAccount(
            address = send.address,
            network = send.network,
            version = send.version,
            height = send.height,
            balance = send.balance,
            lastTransactionHash = send.hash,
            lastTransactionTimestamp = send.timestamp,
            representativeAddress = send.receiverAddress,
        )
    private val entry =
        AttoAccountEntry(
            hash = send.hash,
            address = send.address,
            height = send.height,
            blockType = send.type,
            subjectAddress = send.receiverAddress,
            previousBalance = send.balance + send.amount,
            balance = send.balance,
            timestamp = send.timestamp,
        )

    @Test
    fun `should preserve legacy address omission with configured defaults`() {
        // Given
        val json = Json { encodeDefaults = true }

        // When
        val encodedAccount = json.encodeToJsonElement(AttoAccount.serializer(), account).jsonObject
        val encodedReceivable = json.encodeToJsonElement(AttoReceivable.serializer(), receivable).jsonObject
        val encodedEntry = json.encodeToJsonElement(AttoAccountEntry.serializer(), entry).jsonObject

        // Then
        assertFalse("address" in encodedAccount)
        assertFalse("representativeAddress" in encodedAccount)
        assertFalse("address" in encodedReceivable)
        assertFalse("receiverAddress" in encodedReceivable)
        assertEquals(JsonPrimitive(entry.address.toString()), encodedEntry["address"])
        assertEquals(JsonPrimitive(entry.subjectAddress.toString()), encodedEntry["subjectAddress"])
        assertEquals(account, json.decodeFromJsonElement(AttoAccount.serializer(), encodedAccount))
        assertEquals(receivable, json.decodeFromJsonElement(AttoReceivable.serializer(), encodedReceivable))
        assertEquals(entry, json.decodeFromJsonElement(AttoAccountEntry.serializer(), encodedEntry))
    }

    @Test
    fun `should retain actual fields that share the configured discriminator name`() {
        // Given
        val formats = listOf("height", "address").map { discriminator -> Json { classDiscriminator = discriminator } }
        val expected = listOf(send, account, receivable, entry)

        // When
        val decoded =
            formats.map { json ->
                listOf(
                    json.decodeFromString<AttoSendBlock>(json.encodeToString(send)),
                    json.decodeFromString<AttoAccount>(json.encodeToString(account)),
                    json.decodeFromString<AttoReceivable>(json.encodeToString(receivable)),
                    json.decodeFromString<AttoAccountEntry>(json.encodeToString(entry)),
                )
            }

        // Then
        assertEquals(formats.map { expected }, decoded)
    }

    @Test
    fun `should ignore malformed identity projections with snake case field names`() {
        // Given
        val json = Json { namingStrategy = JsonNamingStrategy.SnakeCase }
        val open = AttoBlockTest.openBlock
        val malformedSend =
            malformedProjections(json, send, "algorithm", "public_key", "receiver_algorithm", "receiver_public_key")
        val malformedOpen =
            malformedProjections(json, open, "algorithm", "public_key", "representative_algorithm", "representative_public_key")
        val malformedAccount =
            malformedProjections(json, account, "algorithm", "public_key", "representative_algorithm", "representative_public_key")
        val malformedReceivable =
            malformedProjections(json, receivable, "algorithm", "public_key", "receiver_algorithm", "receiver_public_key")
        val malformedEntry =
            malformedProjections(json, entry, "algorithm", "public_key", "subject_algorithm", "subject_public_key")

        // When
        val decodedSend = json.decodeFromJsonElement(AttoSendBlock.serializer(), malformedSend)
        val decodedOpen = json.decodeFromJsonElement(AttoOpenBlock.serializer(), malformedOpen)
        val decodedAccount = json.decodeFromJsonElement(AttoAccount.serializer(), malformedAccount)
        val decodedReceivable = json.decodeFromJsonElement(AttoReceivable.serializer(), malformedReceivable)
        val decodedEntry = json.decodeFromJsonElement(AttoAccountEntry.serializer(), malformedEntry)

        // Then
        assertEquals(send, decodedSend)
        assertEquals(open, decodedOpen)
        assertEquals(account, decodedAccount)
        assertEquals(receivable, decodedReceivable)
        assertEquals(entry, decodedEntry)
    }

    @Test
    fun `should roundtrip polymorphic blocks with a custom discriminator`() {
        // Given
        val json = Json { classDiscriminator = "kind" }
        val expected = listOf<AttoBlock>(send, AttoBlockTest.receiveBlock, AttoBlockTest.openBlock, AttoBlockTest.changeBlock)

        // When
        val encoded = expected.map { json.encodeToString(it) }
        val decoded = encoded.map { json.decodeFromString<AttoBlock>(it) }

        // Then
        assertEquals(expected, decoded)
        assertEquals(expected.map { JsonPrimitive(it.type.name) }, encoded.map { json.parseToJsonElement(it).jsonObject["kind"] })
    }

    private inline fun <reified T> malformedProjections(
        json: Json,
        value: T,
        vararg fields: String,
    ): JsonObject {
        val encoded = json.encodeToJsonElement(serializer<T>(), value).jsonObject
        val addresses =
            when (value) {
                is AttoAccount -> mapOf("address" to value.address, "representative_address" to value.representativeAddress)
                is AttoReceivable -> mapOf("address" to value.address, "receiver_address" to value.receiverAddress)
                is AttoAccountEntry -> mapOf("address" to value.address, "subject_address" to value.subjectAddress)
                else -> emptyMap()
            }
        return JsonObject(
            encoded + addresses.mapValues { JsonPrimitive(it.value.toString()) } +
                fields.associateWith { JsonObject(emptyMap()) },
        )
    }
}
