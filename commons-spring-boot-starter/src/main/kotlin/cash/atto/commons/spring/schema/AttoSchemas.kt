package cash.atto.commons.spring.schema

import cash.atto.commons.AttoAddress
import cash.atto.commons.AttoAlgorithm
import cash.atto.commons.AttoAmount
import cash.atto.commons.AttoHash
import cash.atto.commons.AttoHeight
import cash.atto.commons.AttoInstant
import cash.atto.commons.AttoPublicKey
import cash.atto.commons.AttoSignature
import cash.atto.commons.AttoVersion
import cash.atto.commons.AttoWork
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import java.math.BigDecimal
import kotlin.random.Random
import kotlin.reflect.KClass

object AttoSchemas {
    @JvmField
    val primitiveMap: Map<KClass<*>, Schema<*>> =
        mapOf(
            AttoAmount::class to
                StringSchema()
                    .description("Unsigned 64-bit amount in raw units (0..18000000000000000000U)")
                    .pattern("^[0-9]{1,20}$")
                    .example("1000000000000000000"),
            AttoHash::class to
                StringSchema()
                    .format("hex")
                    .minLength(64)
                    .maxLength(64)
                    .description("32-byte hash (hex)")
                    .example(AttoHash(Random.nextBytes(32)).toString()),
            AttoPublicKey::class to
                StringSchema()
                    .format("hex")
                    .minLength(64)
                    .maxLength(64)
                    .description("32-byte ed25519 public key (hex)")
                    .example(AttoPublicKey(Random.nextBytes(32)).toString()),
            AttoSignature::class to
                StringSchema()
                    .format("hex")
                    .minLength(128)
                    .maxLength(128)
                    .description("64-byte ed25519 signature (hex)")
                    .example(AttoSignature(Random.nextBytes(64)).toString()),
            AttoWork::class to
                StringSchema()
                    .format("hex")
                    .minLength(16)
                    .maxLength(16)
                    .description("Work nonce (hex)")
                    .example(AttoWork(Random.nextBytes(8)).toString()),
            AttoHeight::class to
                StringSchema()
                    .description("Block height as unsigned 64-bit integer (1..18446744073709551615)")
                    .pattern("^[0-9]{1,20}$")
                    .example("1"),
            AttoVersion::class to
                IntegerSchema()
                    .format("int32")
                    .minimum(BigDecimal.ZERO)
                    .maximum(BigDecimal.valueOf(0))
                    .description("Protocol version (0..65535). Currently always 0"),
            AttoAddress::class to
                StringSchema()
                    .description(
                        "Atto address URI. Format: `atto://` + 61 Base32 chars (lowercase, no padding). " +
                            "Encodes: 1 byte algorithm code + 32-byte public key + 5-byte checksum (total 38 bytes).",
                    ).pattern("^atto://[a-z2-7]{61}$")
                    .example(AttoAddress(AttoAlgorithm.V1, AttoPublicKey(Random.nextBytes(32))).toString()),
            AttoInstant::class to
                IntegerSchema()
                    .format("int64")
                    .minimum(BigDecimal.ZERO)
                    .description("Unix epoch time in milliseconds since 1970-01-01T00:00:00Z")
                    .example(AttoInstant.now().toEpochMilliseconds().toString()),
        )

    @JvmField
    val primitives = primitiveMap.values
}
