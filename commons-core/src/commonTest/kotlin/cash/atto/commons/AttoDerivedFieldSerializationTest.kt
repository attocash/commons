package cash.atto.commons

import cash.atto.commons.worker.AttoWorker
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AttoDerivedFieldSerializationTest {
    private val tolerantJson = Json { ignoreUnknownKeys = true }
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
            assertEquals(withoutLegacyPairs(expectedJson), Json.parseToJsonElement(block.toJson()))
            assertEquals(block, AttoBlock.fromByteArray(block.toByteArray()))
            assertEquals(AttoAddress(block.address.algorithm, block.address.publicKey), block.address)
        }
        assertEquals(blocks, concreteBlocks)
    }

    @Test
    fun `should reject legacy block identities without required addresses`() {
        // Given
        val legacyJsons = blockJsons.map { withoutAddresses(it) }

        // When
        val failures = legacyJsons.map { runCatching { AttoBlock.fromJson(it.toString()) }.exceptionOrNull() }

        // Then
        assertTrue(failures.all { it is kotlinx.serialization.SerializationException })
        legacyJsons.forEach { json ->
            assertFailsWith<kotlinx.serialization.SerializationException> { decodeConcreteBlock(json) }
        }
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
            assertFalse(publicKeyField in output)
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
    fun `should accept canonical transaction blocks and reject missing addresses`() {
        // Given
        val expected = blockJsons.map { AttoTransaction.fromJson(transactionJson(it).toString()) }
        val addressJsons = blockJsons.map { transactionJson(withoutLegacyPairs(it)) }
        val legacyJsons = blockJsons.map { transactionJson(withoutAddresses(it)) }

        // When
        val addressTransactions = addressJsons.map { AttoTransaction.fromJson(it.toString()) }
        val legacyFailures = legacyJsons.map { runCatching { AttoTransaction.fromJson(it.toString()) }.exceptionOrNull() }

        // Then
        assertEquals(expected, addressTransactions)
        assertTrue(legacyFailures.all { it is kotlinx.serialization.SerializationException })
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
            val block = AttoBlockTest.openBlock.copy(address = AttoAddress(AttoBlockTest.openBlock.address.algorithm, signer.publicKey))
            val work = AttoWorker.cpu(1U).use { it.work(block) }
            val expected = AttoTransaction(block, signer.sign(block), work)
            val json = Json.parseToJsonElement(expected.toJson()).jsonObject
            val blockJson = json.getValue("block").jsonObject
            val blockVariants =
                listOf(blockJson, withoutLegacyPairs(blockJson)) +
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
            assertEquals(changedAddress.publicKey, transaction.block.address.publicKey)
            assertNotEquals(original.hash, transaction.hash)
            assertEquals(original.signature, transaction.signature)
            assertFalse(transaction.signature.isValid(transaction.block.address.publicKey, transaction.hash))
            assertFalse(transaction.isValid())
        }

    @Test
    fun `should accept canonical account entry identities and ignore unknown fields when configured`() {
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
        val jsons =
            listOf(canonicalJson, withoutLegacyPairs(canonicalJson)) +
                ignoredProjectionJsons(canonicalJson)

        // When
        val entries = jsons.map { AttoAccountEntry.fromJson(it.toString()) }
        val concreteEntries = jsons.map { tolerantJson.decodeFromString<AttoAccountEntry>(it.toString()) }

        // Then
        assertTrue("address" in json)
        assertTrue("subjectAddress" in json)
        (entries + concreteEntries).forEach { entry ->
            assertEquals(expected, entry)
            assertEquals(json, Json.parseToJsonElement(entry.toJson()))
        }
    }

    @Test
    fun `should accept canonical account identities and ignore unknown fields when configured`() {
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
        val jsons =
            listOf(canonicalJson, withoutLegacyPairs(canonicalJson)) +
                ignoredProjectionJsons(canonicalJson)

        // When
        val accounts = jsons.map { AttoAccount.fromJson(it.toString()) }
        val concreteAccounts = jsons.map { tolerantJson.decodeFromString<AttoAccount>(it.toString()) }

        // Then
        assertTrue("address" in json)
        assertTrue("representativeAddress" in json)
        (accounts + concreteAccounts).forEach { account ->
            assertEquals(expected, account)
            assertEquals(json, Json.parseToJsonElement(account.toJson()))
        }
    }

    @Test
    fun `should accept canonical receivable identities and ignore unknown fields when configured`() {
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
        val jsons =
            listOf(canonicalJson, withoutLegacyPairs(canonicalJson)) +
                ignoredProjectionJsons(canonicalJson)

        // When
        val receivables = jsons.map { AttoReceivable.fromJson(it.toString()) }
        val concreteReceivables = jsons.map { tolerantJson.decodeFromString<AttoReceivable>(it.toString()) }

        // Then
        assertTrue("address" in json)
        assertTrue("receiverAddress" in json)
        (receivables + concreteReceivables).forEach { receivable ->
            assertEquals(expected, receivable)
            assertEquals(json, Json.parseToJsonElement(receivable.toJson()))
        }
    }

    @Test
    fun `should require each canonical JSON address even when legacy keys are supplied`() {
        // Given
        val blocks = blockJsons.map { AttoBlock.fromJson(it.toString()) }
        val account = account()
        val entry = accountEntry()
        val receivable = AttoBlockTest.sendBlock.toReceivable()
        val vote = AttoVote(AttoVersion(0U), account.address, AttoAlgorithm.V1, blocks.first().hash, account.lastTransactionTimestamp)

        // When
        val failures =
            listOf(
                missingAddressFailures(AttoAccount.serializer(), account),
                missingAddressFailures(AttoAccountEntry.serializer(), entry),
                missingAddressFailures(AttoReceivable.serializer(), receivable),
                missingAddressFailures(AttoVote.serializer(), vote),
            ).flatten() +
                blocks.flatMap { block ->
                    val concrete =
                        when (block) {
                            is AttoSendBlock -> missingAddressFailures(AttoSendBlock.serializer(), block)
                            is AttoReceiveBlock -> missingAddressFailures(AttoReceiveBlock.serializer(), block)
                            is AttoOpenBlock -> missingAddressFailures(AttoOpenBlock.serializer(), block)
                            is AttoChangeBlock -> missingAddressFailures(AttoChangeBlock.serializer(), block)
                        }
                    val transaction = AttoTransaction(block, AttoSignature(ByteArray(AttoSignature.SIZE)), AttoWork(ByteArray(8)))
                    concrete + missingAddressFailures(AttoBlock.serializer(), block) +
                        missingAddressFailures(AttoTransaction.serializer(), transaction)
                }

        // Then
        assertTrue(failures.isNotEmpty())
        assertTrue(failures.all { it is kotlinx.serialization.SerializationException })
    }

    @Test
    fun `should apply configured unknown JSON field policy to extra legacy keys`() {
        // Given
        val blocks = blockJsons.map { AttoBlock.fromJson(it.toString()) }
        val account = account()
        val vote = AttoVote(AttoVersion(0U), account.address, AttoAlgorithm.V1, blocks.first().hash, account.lastTransactionTimestamp)

        // When
        val results =
            listOf(
                unknownIdentityFields(AttoAccount.serializer(), account),
                unknownIdentityFields(AttoAccountEntry.serializer(), accountEntry()),
                unknownIdentityFields(AttoReceivable.serializer(), AttoBlockTest.sendBlock.toReceivable()),
                unknownIdentityFields(AttoVote.serializer(), vote),
            ) +
                blocks.map { block ->
                    when (block) {
                        is AttoSendBlock -> unknownIdentityFields(AttoSendBlock.serializer(), block)
                        is AttoReceiveBlock -> unknownIdentityFields(AttoReceiveBlock.serializer(), block)
                        is AttoOpenBlock -> unknownIdentityFields(AttoOpenBlock.serializer(), block)
                        is AttoChangeBlock -> unknownIdentityFields(AttoChangeBlock.serializer(), block)
                    }
                }

        // Then
        results.forEach { (failure, unchanged) ->
            assertTrue(failure is kotlinx.serialization.SerializationException)
            assertTrue(unchanged)
        }
    }

    private fun <T> missingAddressFailures(
        serializer: KSerializer<T>,
        value: T,
    ): List<Throwable?> {
        val encoded = Json.encodeToJsonElement(serializer, value).jsonObject
        val fields = if ("block" in encoded) encoded.getValue("block").jsonObject else encoded
        return addressProjectionFields.keys.intersect(fields.keys).flatMap { addressField ->
            val address = AttoAddress.parse(fields.getValue(addressField).jsonPrimitive.content)
            val projections = addressProjectionFields.getValue(addressField)
            val legacy =
                fields +
                    mapOf(
                        projections[0] to JsonPrimitive(address.algorithm.name),
                        projections[1] to JsonPrimitive(address.publicKey.toString()),
                    )
            listOf(JsonObject(legacy - addressField), JsonObject(legacy + (addressField to JsonNull))).flatMap { altered ->
                val input = if ("block" in encoded) JsonObject(encoded + ("block" to altered)) else altered
                listOf(Json, tolerantJson).map { json ->
                    runCatching { json.decodeFromJsonElement(serializer, input) }.exceptionOrNull()
                }
            }
        }
    }

    private fun <T> unknownIdentityFields(
        serializer: KSerializer<T>,
        value: T,
    ): Pair<Throwable?, Boolean> {
        val encoded = Json.encodeToJsonElement(serializer, value).jsonObject
        val fields = JsonObject(encoded + mapOf("algorithm" to JsonObject(emptyMap()), "publicKey" to JsonPrimitive("ignored")))
        val strictFailure = runCatching { Json.decodeFromJsonElement(serializer, fields) }.exceptionOrNull()
        return strictFailure to (value == tolerantJson.decodeFromJsonElement(serializer, fields))
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `should emit canonical identities for every JSON configuration and nesting`() {
        // Given
        val blocks = blockJsons.map { AttoBlock.fromJson(it.toString()) }
        val account = account()
        val entry = accountEntry()
        val receivable = AttoBlockTest.sendBlock.toReceivable()
        val vote = AttoVote(AttoVersion(0U), account.address, AttoAlgorithm.V1, blocks.first().hash, account.lastTransactionTimestamp)
        val weight = AttoVoterWeight(account.address, account.balance, account.lastTransactionTimestamp)
        val configurations =
            listOf(false, true).flatMap { defaults ->
                listOf(false, true).flatMap { nulls ->
                    listOf(null, JsonNamingStrategy.SnakeCase).map { naming ->
                        Json {
                            encodeDefaults = defaults
                            explicitNulls = nulls
                            namingStrategy = naming
                        }
                    }
                }
            }

        // When
        configurations.forEach { json ->
            val values =
                listOf(
                    json.encodeToJsonElement(AttoAccount.serializer(), account),
                    json.encodeToJsonElement(AttoAccountEntry.serializer(), entry),
                    json.encodeToJsonElement(AttoReceivable.serializer(), receivable),
                    json.encodeToJsonElement(AttoVote.serializer(), vote),
                    json.encodeToJsonElement(AttoVoterWeight.serializer(), weight),
                ) +
                    blocks.flatMap { block ->
                        val concrete =
                            when (block) {
                                is AttoSendBlock -> json.encodeToJsonElement(AttoSendBlock.serializer(), block)
                                is AttoReceiveBlock -> json.encodeToJsonElement(AttoReceiveBlock.serializer(), block)
                                is AttoOpenBlock -> json.encodeToJsonElement(AttoOpenBlock.serializer(), block)
                                is AttoChangeBlock -> json.encodeToJsonElement(AttoChangeBlock.serializer(), block)
                            }
                        val transaction = AttoTransaction(block, AttoSignature(ByteArray(AttoSignature.SIZE)), AttoWork(ByteArray(8)))
                        listOf(
                            concrete,
                            json.encodeToJsonElement(AttoBlock.serializer(), block),
                            json.encodeToJsonElement(AttoTransaction.serializer(), transaction),
                        )
                    }

            // Then
            values.forEach { assertCanonicalIdentity(it) }
            assertEquals(account, json.decodeFromJsonElement(AttoAccount.serializer(), values[0]))
            assertEquals(entry, json.decodeFromJsonElement(AttoAccountEntry.serializer(), values[1]))
            assertEquals(receivable, json.decodeFromJsonElement(AttoReceivable.serializer(), values[2]))
            assertEquals(vote, json.decodeFromJsonElement(AttoVote.serializer(), values[3]))
            blocks.forEach { block ->
                val encoded = json.encodeToJsonElement(AttoBlock.serializer(), block)
                assertEquals(block, json.decodeFromJsonElement(AttoBlock.serializer(), encoded))
                if (block is ReceiveSupport) {
                    val field = if (json.configuration.namingStrategy == null) "sendHashAlgorithm" else "send_hash_algorithm"
                    assertEquals(JsonPrimitive(block.sendHashAlgorithm.name), encoded.jsonObject[field])
                }
            }
            val blockAlgorithm = if (json.configuration.namingStrategy == null) "blockAlgorithm" else "block_algorithm"
            assertEquals(JsonPrimitive(vote.blockAlgorithm.name), values[3].jsonObject[blockAlgorithm])
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `should preserve JSON naming strategies that inspect descriptor names and field positions`() {
        // Given
        val blocks = blockJsons.map { AttoBlock.fromJson(it.toString()) }
        val account = account()
        val entry = accountEntry()
        val receivable = AttoBlockTest.sendBlock.toReceivable()
        val vote = AttoVote(AttoVersion(0U), account.address, AttoAlgorithm.V1, blocks.first().hash, account.lastTransactionTimestamp)
        val strategies =
            listOf(
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
        val configurations = strategies.map { strategy -> Json { namingStrategy = strategy } }

        // When
        val roundTrips =
            configurations.flatMap { json ->
                listOf(
                    jsonRoundTrip(json, AttoAccount.serializer(), account),
                    jsonRoundTrip(json, AttoAccountEntry.serializer(), entry),
                    jsonRoundTrip(json, AttoReceivable.serializer(), receivable),
                    jsonRoundTrip(json, AttoVote.serializer(), vote),
                ) +
                    blocks.flatMap { block ->
                        val concrete =
                            when (block) {
                                is AttoSendBlock -> jsonRoundTrip(json, AttoSendBlock.serializer(), block)
                                is AttoReceiveBlock -> jsonRoundTrip(json, AttoReceiveBlock.serializer(), block)
                                is AttoOpenBlock -> jsonRoundTrip(json, AttoOpenBlock.serializer(), block)
                                is AttoChangeBlock -> jsonRoundTrip(json, AttoChangeBlock.serializer(), block)
                            }
                        val transaction = AttoTransaction(block, AttoSignature(ByteArray(AttoSignature.SIZE)), AttoWork(ByteArray(8)))
                        listOf(
                            concrete,
                            jsonRoundTrip(json, AttoBlock.serializer(), block),
                            jsonRoundTrip(json, AttoTransaction.serializer(), transaction),
                        )
                    }
            }

        // Then
        roundTrips.forEach { (expected, actual) -> assertEquals(expected, actual) }
    }

    private fun <T> jsonRoundTrip(
        json: Json,
        serializer: KSerializer<T>,
        value: T,
    ): Pair<T, T> = value to json.decodeFromString(serializer, json.encodeToString(serializer, value))

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `should round trip canonical model serializers outside JSON`() {
        // Given
        val blocks = blockJsons.map { AttoBlock.fromJson(it.toString()) }
        val account = account()
        val entry = accountEntry()
        val receivable = AttoBlockTest.sendBlock.toReceivable()
        val vote = AttoVote(AttoVersion(0U), account.address, AttoAlgorithm.V1, blocks.first().hash, account.lastTransactionTimestamp)

        val transactions =
            blocks.map { block ->
                AttoTransaction(block, AttoSignature(ByteArray(AttoSignature.SIZE)), AttoWork(ByteArray(8)))
            }

        // When
        val decodedAccount = protobufRoundTrip(AttoAccount.serializer(), account)
        val decodedEntry = protobufRoundTrip(AttoAccountEntry.serializer(), entry)
        val decodedReceivable = protobufRoundTrip(AttoReceivable.serializer(), receivable)
        val decodedVote = protobufRoundTrip(AttoVote.serializer(), vote)
        val decodedBlocks = blocks.map { protobufRoundTrip(AttoBlock.serializer(), it) }
        val decodedTransactions = transactions.map { protobufRoundTrip(AttoTransaction.serializer(), it) }

        // Then
        assertEquals(account, decodedAccount)
        assertEquals(entry, decodedEntry)
        assertEquals(receivable, decodedReceivable)
        assertEquals(vote, decodedVote)
        assertEquals(blocks, decodedBlocks)
        assertEquals(transactions, decodedTransactions)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> protobufRoundTrip(
        serializer: KSerializer<T>,
        value: T,
    ): T {
        val encoded = ProtoBuf.encodeToByteArray(serializer, value)
        return ProtoBuf.decodeFromByteArray(serializer, encoded)
    }

    private fun assertCanonicalIdentity(element: JsonElement) {
        val fields = element.jsonObject
        val forbidden =
            addressProjectionFields.values.flatten().toSet() +
                setOf(
                    "public_key",
                    "receiver_algorithm",
                    "receiver_public_key",
                    "representative_algorithm",
                    "representative_public_key",
                    "subject_algorithm",
                    "subject_public_key",
                )
        assertTrue(fields.keys.intersect(forbidden).isEmpty(), fields.toString())
        assertTrue(fields.values.none { it == JsonNull }, fields.toString())
        if ("block" in fields) {
            assertCanonicalIdentity(fields.getValue("block"))
        } else {
            assertTrue("address" in fields, fields.toString())
        }
    }

    private fun decodeConcreteBlock(json: JsonObject): AttoBlock {
        val concreteJson = JsonObject(json - "type").toString()
        return when (json.getValue("type").jsonPrimitive.content) {
            "SEND" -> tolerantJson.decodeFromString<AttoSendBlock>(concreteJson)
            "RECEIVE" -> tolerantJson.decodeFromString<AttoReceiveBlock>(concreteJson)
            "OPEN" -> tolerantJson.decodeFromString<AttoOpenBlock>(concreteJson)
            "CHANGE" -> tolerantJson.decodeFromString<AttoChangeBlock>(concreteJson)
            else -> error("Unsupported fixture block type")
        }
    }

    private fun withoutAddresses(json: JsonObject): JsonObject {
        val fields = addressProjectionFields.keys + if (json["type"] == JsonPrimitive("OPEN")) setOf("height") else emptySet()
        val legacy = json.toMutableMap()
        addressProjectionFields.forEach { (addressField, projections) ->
            json[addressField]?.jsonPrimitive?.content?.let { encoded ->
                val address = AttoAddress.parse(encoded)
                legacy[projections[0]] = JsonPrimitive(address.algorithm.name)
                legacy[projections[1]] = JsonPrimitive(address.publicKey.toString())
            }
        }
        return JsonObject(legacy - fields)
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
