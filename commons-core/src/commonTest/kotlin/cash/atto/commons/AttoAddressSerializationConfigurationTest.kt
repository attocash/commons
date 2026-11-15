@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `should emit canonical addresses with configured defaults`() {
        // Given
        val json = Json { encodeDefaults = true }

        // When
        val encodedAccount = json.encodeToJsonElement(AttoAccount.serializer(), account).jsonObject
        val encodedReceivable = json.encodeToJsonElement(AttoReceivable.serializer(), receivable).jsonObject
        val encodedEntry = json.encodeToJsonElement(AttoAccountEntry.serializer(), entry).jsonObject

        // Then
        assertEquals(JsonPrimitive(account.address.toString()), encodedAccount["address"])
        assertEquals(JsonPrimitive(account.representativeAddress.toString()), encodedAccount["representativeAddress"])
        assertEquals(JsonPrimitive(receivable.address.toString()), encodedReceivable["address"])
        assertEquals(JsonPrimitive(receivable.receiverAddress.toString()), encodedReceivable["receiverAddress"])
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
    fun `should ignore unknown identity fields with configured snake case JSON`() {
        // Given
        val json =
            Json {
                namingStrategy = JsonNamingStrategy.SnakeCase
                ignoreUnknownKeys = true
            }
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

    @Test
    fun `should decode sealed block JSON with strict concrete serializers`() {
        // Given
        val blocks = listOf(send, AttoBlockTest.receiveBlock, AttoBlockTest.openBlock, AttoBlockTest.changeBlock)
        val formats = concreteFormats()

        // When
        val decoded =
            formats.map { json ->
                blocks.map { block ->
                    decodeConcrete(json, json.encodeToString(AttoBlock.serializer(), block), block)
                }
            }

        // Then
        assertEquals(formats.map { blocks }, decoded)
    }

    @Test
    fun `should reject other unknown fields when decoding sealed JSON as concrete blocks`() {
        // Given
        val blocks = listOf(send, AttoBlockTest.receiveBlock, AttoBlockTest.openBlock, AttoBlockTest.changeBlock)
        val formats = concreteFormats()

        // When
        val failures =
            formats.flatMap { json ->
                blocks.flatMap { block ->
                    val encoded = json.encodeToJsonElement(AttoBlock.serializer(), block).jsonObject
                    listOf("unexpected", "publicKey").map { field ->
                        val input = JsonObject(encoded + (field to JsonPrimitive(true)))
                        runCatching { decodeConcrete(json, input.toString(), block) }.exceptionOrNull()
                    }
                }
            }

        // Then
        assertEquals(formats.size * blocks.size * 2, failures.size)
        assertTrue(failures.all { it is SerializationException })
    }

    private fun concreteFormats(): List<Json> {
        val namingStrategies =
            listOf(
                null,
                JsonNamingStrategy.SnakeCase,
                object : JsonNamingStrategy {
                    override fun serialNameForJson(
                        descriptor: SerialDescriptor,
                        elementIndex: Int,
                        serialName: String,
                    ): String = "${descriptor.serialName}_$serialName"
                },
                object : JsonNamingStrategy {
                    override fun serialNameForJson(
                        descriptor: SerialDescriptor,
                        elementIndex: Int,
                        serialName: String,
                    ): String = "${elementIndex}_$serialName"
                },
            )
        return listOf("type", "kind").flatMap { discriminator ->
            namingStrategies.map { naming ->
                Json {
                    classDiscriminator = discriminator
                    namingStrategy = naming
                }
            }
        }
    }

    private fun decodeConcrete(
        json: Json,
        input: String,
        block: AttoBlock,
    ): AttoBlock =
        when (block) {
            is AttoSendBlock -> json.decodeFromString<AttoSendBlock>(input)
            is AttoReceiveBlock -> json.decodeFromString<AttoReceiveBlock>(input)
            is AttoOpenBlock -> json.decodeFromString<AttoOpenBlock>(input)
            is AttoChangeBlock -> json.decodeFromString<AttoChangeBlock>(input)
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
