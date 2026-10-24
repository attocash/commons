package cash.atto.commons

import cash.atto.commons.worker.AttoWorker
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AttoDerivedFieldSerializationTest {
    private val blockJsons = AttoBlockTest.validJsonBlocks.map { Json.parseToJsonElement(it).jsonObject }
    private val replacementAddress = AttoAddress(AttoAlgorithm.V1, AttoPublicKey(ByteArray(32)))
    private val addressProjectionFields =
        mapOf(
            "address" to listOf("algorithm", "publicKey"),
            "receiverAddress" to listOf("receiverAlgorithm", "receiverPublicKey"),
            "representativeAddress" to listOf("representativeAlgorithm", "representativePublicKey"),
            "subjectAddress" to listOf("subjectAlgorithm", "subjectPublicKey"),
        )

    @Test
    fun `should preserve matching block fields and JSON output`() {
        // Given
        val jsons = blockJsons

        // When
        val blocks = jsons.map { AttoBlock.fromJson(it.toString()) }
        val concreteBlocks = jsons.map { decodeConcreteBlock(it) }

        // Then
        jsons.zip(blocks).forEach { (expectedJson, block) ->
            assertEquals(expectedJson, Json.parseToJsonElement(block.toJson()))
            assertEquals(block, AttoBlock.fromByteArray(block.toByteArray()))
            assertEquals(AttoAddress(block.algorithm, block.publicKey), block.address)
        }
        assertEquals(blocks, concreteBlocks)
    }

    @Test
    fun `should accept legacy pairs without addresses and preserve bytes and hashes`() {
        // Given
        val expectedBlocks = blockJsons.map { AttoBlock.fromJson(it.toString()) }
        val legacyJsons = blockJsons.map { withoutAddresses(it) }

        // When
        val blocks = legacyJsons.map { AttoBlock.fromJson(it.toString()) }
        val concreteBlocks = legacyJsons.map { decodeConcreteBlock(it) }

        // Then
        expectedBlocks.zip(blocks).forEach { (expected, actual) ->
            assertEquals(expected, actual)
            assertContentEquals(expected.toByteArray(), actual.toByteArray())
            assertEquals(expected.hash, actual.hash)
            assertEquals(expected.toJson(), actual.toJson())
        }
        assertEquals(expectedBlocks, concreteBlocks)
    }

    @Test
    fun `should accept addresses without legacy pairs and preserve bytes and hashes`() {
        // Given
        val expectedBlocks = blockJsons.map { AttoBlock.fromJson(it.toString()) }
        val addressJsons = blockJsons.map { withoutLegacyPairs(it) }

        // When
        val blocks = addressJsons.map { AttoBlock.fromJson(it.toString()) }
        val concreteBlocks = addressJsons.map { decodeConcreteBlock(it) }

        // Then
        expectedBlocks.zip(blocks).forEach { (expected, actual) ->
            assertEquals(expected, actual)
            assertContentEquals(expected.toByteArray(), actual.toByteArray())
            assertEquals(expected.hash, actual.hash)
            assertEquals(expected.toJson(), actual.toJson())
        }
        assertEquals(expectedBlocks, concreteBlocks)
    }

    @Test
    fun `should ignore conflicting and malformed block projections when addresses are present`() {
        // Given
        val cases = blockJsons.flatMap { json -> ignoredProjectionJsons(json).map { json to it } }
        val expected = cases.map { (original, _) -> AttoBlock.fromJson(original.toString()) }

        // When
        val blocks = cases.map { (_, altered) -> AttoBlock.fromJson(altered.toString()) }
        val concreteBlocks = cases.map { (_, altered) -> decodeConcreteBlock(altered) }

        // Then
        assertEquals(expected, blocks)
        assertEquals(expected, concreteBlocks)
        expected.zip(blocks).forEach { (original, decoded) ->
            assertContentEquals(original.toByteArray(), decoded.toByteArray())
            assertEquals(original.hash, decoded.hash)
            assertEquals(original.toJson(), decoded.toJson())
        }
    }

    @Test
    fun `should use canonical addresses when legacy public keys disagree`() {
        // Given
        val cases =
            blockJsons.flatMap { json ->
                addressProjectionFields.keys.intersect(json.keys).map { field ->
                    field to JsonObject(json + (field to JsonPrimitive(replacementAddress.toString())))
                }
            }

        // When
        val blocks = cases.map { (_, json) -> AttoBlock.fromJson(json.toString()) }
        val concreteBlocks = cases.map { (_, json) -> decodeConcreteBlock(json) }

        // Then
        cases.zip(blocks).forEach { (case, block) ->
            val output = Json.parseToJsonElement(block.toJson()).jsonObject
            val publicKeyField = addressProjectionFields.getValue(case.first).last()
            assertEquals(replacementAddress.toString(), output.getValue(case.first).jsonPrimitive.content)
            assertEquals(replacementAddress.publicKey.toString(), output.getValue(publicKeyField).jsonPrimitive.content)
            assertEquals(block, AttoBlock.fromByteArray(block.toByteArray()))
        }
        assertEquals(blocks, concreteBlocks)
    }

    @Test
    fun `should ignore supplied OPEN height values and always emit one`() {
        // Given
        val openJson = blockJsons.single { it.getValue("type").jsonPrimitive.content == "OPEN" }
        val expected = AttoBlock.fromJson(openJson.toString())
        val heights =
            listOf(
                JsonPrimitive("0"),
                JsonPrimitive("2"),
                JsonPrimitive(2),
                JsonPrimitive(ULong.MAX_VALUE.toString()),
                JsonPrimitive("invalid"),
                JsonPrimitive(true),
                JsonNull,
                JsonObject(emptyMap()),
            )
        val jsons = heights.map { JsonObject(openJson + ("height" to it)) } + JsonObject(openJson - "height")

        // When
        val blocks = jsons.map { AttoBlock.fromJson(it.toString()) }
        val concreteBlocks = jsons.map { decodeConcreteBlock(it) }

        // Then
        (blocks + concreteBlocks).forEach { block ->
            assertEquals(expected, block)
            assertEquals(1U.toAttoHeight(), block.height)
            assertContentEquals(expected.toByteArray(), block.toByteArray())
            assertEquals(expected.hash, block.hash)
            assertEquals(JsonPrimitive("1"), Json.parseToJsonElement(block.toJson()).jsonObject.getValue("height"))
        }
    }

    @Test
    fun `should accept address-only and legacy-pair-only blocks in transactions`() {
        // Given
        val expected = blockJsons.map { AttoTransaction.fromJson(transactionJson(it).toString()) }
        val addressJsons = blockJsons.map { transactionJson(withoutLegacyPairs(it)) }
        val legacyJsons = blockJsons.map { transactionJson(withoutAddresses(it)) }

        // When
        val addressTransactions = addressJsons.map { AttoTransaction.fromJson(it.toString()) }
        val legacyTransactions = legacyJsons.map { AttoTransaction.fromJson(it.toString()) }

        // Then
        assertEquals(expected, addressTransactions)
        assertEquals(expected, legacyTransactions)
        expected.zip(addressTransactions).forEach { (original, decoded) ->
            assertContentEquals(original.toByteArray(), decoded.toByteArray())
            assertEquals(original.hash, decoded.hash)
        }
    }

    @Test
    fun `should ignore projected fields in nested transaction blocks`() {
        // Given
        val cases = blockJsons.flatMap { json -> ignoredProjectionJsons(json).map { json to it } }
        val expected = cases.map { (original, _) -> AttoTransaction.fromJson(transactionJson(original).toString()) }

        // When
        val transactions = cases.map { (_, altered) -> AttoTransaction.fromJson(transactionJson(altered).toString()) }

        // Then
        assertEquals(expected, transactions)
        expected.zip(transactions).forEach { (original, decoded) ->
            assertContentEquals(original.toByteArray(), decoded.toByteArray())
            assertEquals(original.hash, decoded.hash)
        }
    }

    @Test
    fun `should preserve signed OPEN transactions when only projections change`() =
        runTest {
            // Given
            val signer = AttoPrivateKey(ByteArray(32) { 1 }).toSigner()
            val block = AttoBlockTest.openBlock.copy(address = AttoAddress(AttoBlockTest.openBlock.algorithm, signer.publicKey))
            val work = AttoWorker.cpu(1U).use { it.work(block) }
            val expected = AttoTransaction(block, signer.sign(block), work)
            val json = Json.parseToJsonElement(expected.toJson()).jsonObject
            val blockJson = json.getValue("block").jsonObject
            val blockVariants =
                listOf(blockJson, withoutAddresses(blockJson), withoutLegacyPairs(blockJson)) +
                    ignoredProjectionJsons(blockJson) +
                    JsonObject(blockJson + ("height" to JsonObject(emptyMap())))
            val jsons = blockVariants.map { JsonObject(json + ("block" to it)) }

            // When
            val transactions = jsons.map { AttoTransaction.fromJson(it.toString()) }

            // Then
            assertTrue(expected.isValid())
            transactions.forEach { transaction ->
                assertEquals(expected, transaction)
                assertContentEquals(expected.toByteArray(), transaction.toByteArray())
                assertEquals(expected.hash, transaction.hash)
                assertTrue(transaction.isValid())
            }
        }

    @Test
    fun `should decode changed canonical addresses and invalidate the original signature`() =
        runTest {
            // Given
            val signer = AttoPrivateKey(ByteArray(32) { 1 }).toSigner()
            val block = AttoBlockTest.openBlock.copy(address = AttoAddress(AttoAlgorithm.V1, signer.publicKey))
            val work = AttoWorker.cpu(1U).use { it.work(block) }
            val original = AttoTransaction(block, signer.sign(block), work)
            val changedAddress = AttoAddress(AttoAlgorithm.V1, AttoPrivateKey(ByteArray(32) { 2 }).toPublicKey())
            val json = Json.parseToJsonElement(original.toJson()).jsonObject
            val blockJson = json.getValue("block").jsonObject
            val alteredBlock = JsonObject(blockJson + ("address" to JsonPrimitive(changedAddress.toString())))
            val alteredJson = JsonObject(json + ("block" to alteredBlock))

            // When
            val transaction = AttoTransaction.fromJson(alteredJson.toString())

            // Then
            assertEquals(changedAddress, transaction.address)
            assertEquals(changedAddress.publicKey, transaction.block.publicKey)
            assertNotEquals(original.hash, transaction.hash)
            assertEquals(original.signature, transaction.signature)
            assertFalse(transaction.signature.isValid(transaction.block.publicKey, transaction.hash))
            assertFalse(transaction.isValid())
        }

    @Test
    fun `should accept canonical and legacy account entry identities and preserve legacy output`() {
        // Given
        val expected = accountEntry()
        val json = Json.parseToJsonElement(expected.toJson()).jsonObject
        val canonicalJson =
            JsonObject(
                json +
                    mapOf(
                        "address" to JsonPrimitive(expected.address.toString()),
                        "subjectAddress" to JsonPrimitive(expected.subjectAddress.toString()),
                    ),
            )
        val jsons = listOf(json, canonicalJson, withoutLegacyPairs(canonicalJson)) + ignoredProjectionJsons(canonicalJson)

        // When
        val entries = jsons.map { AttoAccountEntry.fromJson(it.toString()) }
        val concreteEntries = jsons.map { Json.decodeFromString<AttoAccountEntry>(it.toString()) }

        // Then
        assertFalse("address" in json)
        assertFalse("subjectAddress" in json)
        (entries + concreteEntries).forEach { entry ->
            assertEquals(expected, entry)
            assertEquals(json, Json.parseToJsonElement(entry.toJson()))
        }
    }

    @Test
    fun `should accept canonical and legacy account identities and preserve legacy output`() {
        // Given
        val expected = account()
        val json = Json.parseToJsonElement(expected.toJson()).jsonObject
        val canonicalJson =
            JsonObject(
                json +
                    mapOf(
                        "address" to JsonPrimitive(expected.address.toString()),
                        "representativeAddress" to JsonPrimitive(expected.representativeAddress.toString()),
                    ),
            )
        val jsons = listOf(json, canonicalJson, withoutLegacyPairs(canonicalJson)) + ignoredProjectionJsons(canonicalJson)

        // When
        val accounts = jsons.map { AttoAccount.fromJson(it.toString()) }
        val concreteAccounts = jsons.map { Json.decodeFromString<AttoAccount>(it.toString()) }

        // Then
        assertFalse("address" in json)
        assertFalse("representativeAddress" in json)
        (accounts + concreteAccounts).forEach { account ->
            assertEquals(expected, account)
            assertEquals(json, Json.parseToJsonElement(account.toJson()))
        }
    }

    @Test
    fun `should accept canonical and legacy receivable identities and preserve legacy output`() {
        // Given
        val expected = AttoBlockTest.sendBlock.toReceivable()
        val json = Json.parseToJsonElement(expected.toJson()).jsonObject
        val canonicalJson =
            JsonObject(
                json +
                    mapOf(
                        "address" to JsonPrimitive(expected.address.toString()),
                        "receiverAddress" to JsonPrimitive(expected.receiverAddress.toString()),
                    ),
            )
        val jsons = listOf(json, canonicalJson, withoutLegacyPairs(canonicalJson)) + ignoredProjectionJsons(canonicalJson)

        // When
        val receivables = jsons.map { AttoReceivable.fromJson(it.toString()) }
        val concreteReceivables = jsons.map { Json.decodeFromString<AttoReceivable>(it.toString()) }

        // Then
        assertFalse("address" in json)
        assertFalse("receiverAddress" in json)
        (receivables + concreteReceivables).forEach { receivable ->
            assertEquals(expected, receivable)
            assertEquals(json, Json.parseToJsonElement(receivable.toJson()))
        }
    }

    private fun decodeConcreteBlock(json: JsonObject): AttoBlock {
        val concreteJson = JsonObject(json - "type").toString()
        return when (json.getValue("type").jsonPrimitive.content) {
            "SEND" -> Json.decodeFromString<AttoSendBlock>(concreteJson)
            "RECEIVE" -> Json.decodeFromString<AttoReceiveBlock>(concreteJson)
            "OPEN" -> Json.decodeFromString<AttoOpenBlock>(concreteJson)
            "CHANGE" -> Json.decodeFromString<AttoChangeBlock>(concreteJson)
            else -> error("Unsupported fixture block type")
        }
    }

    private fun withoutAddresses(json: JsonObject): JsonObject {
        val fields = addressProjectionFields.keys + if (json["type"] == JsonPrimitive("OPEN")) setOf("height") else emptySet()
        return JsonObject(json - fields)
    }

    private fun withoutLegacyPairs(json: JsonObject): JsonObject = JsonObject(json - addressProjectionFields.values.flatten().toSet())

    private fun ignoredProjectionJsons(json: JsonObject): List<JsonObject> =
        addressProjectionFields.keys.intersect(json.keys).flatMap { addressField ->
            addressProjectionFields.getValue(addressField).flatMap { field ->
                val conflictingValue =
                    if (field.endsWith(
                            "publicKey",
                            ignoreCase = true,
                        )
                    ) {
                        JsonPrimitive("00".repeat(32))
                    } else {
                        JsonPrimitive("UNKNOWN")
                    }
                val values: List<JsonElement> =
                    listOf(
                        conflictingValue,
                        JsonPrimitive("invalid"),
                        JsonPrimitive(1),
                        JsonPrimitive(true),
                        JsonNull,
                        JsonObject(emptyMap()),
                    )
                values.map { value -> JsonObject(json + (field to value)) }
            }
        }

    private fun transactionJson(block: JsonObject): JsonObject =
        JsonObject(
            mapOf(
                "block" to block,
                "signature" to JsonPrimitive("00".repeat(AttoSignature.SIZE)),
                "work" to JsonPrimitive("00".repeat(8)),
            ),
        )

    private fun accountEntry(): AttoAccountEntry {
        val block = AttoBlockTest.sendBlock
        return AttoAccountEntry(
            hash = block.hash,
            address = block.address,
            height = block.height,
            blockType = block.type,
            subjectAddress = block.receiverAddress,
            previousBalance = block.balance + block.amount,
            balance = block.balance,
            timestamp = block.timestamp,
        )
    }

    private fun account(): AttoAccount {
        val block = AttoBlockTest.sendBlock
        return AttoAccount(
            address = block.address,
            network = block.network,
            version = block.version,
            height = block.height,
            balance = block.balance,
            lastTransactionHash = block.hash,
            lastTransactionTimestamp = block.timestamp,
            representativeAddress = AttoBlockTest.changeBlock.representativeAddress,
        )
    }
}
