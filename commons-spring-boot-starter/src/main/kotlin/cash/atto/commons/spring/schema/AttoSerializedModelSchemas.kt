@file:OptIn(ExperimentalSerializationApi::class)

package cash.atto.commons.spring.schema

import cash.atto.commons.AttoAccount
import cash.atto.commons.AttoAccountEntry
import cash.atto.commons.AttoBlock
import cash.atto.commons.AttoChangeBlock
import cash.atto.commons.AttoOpenBlock
import cash.atto.commons.AttoReceivable
import cash.atto.commons.AttoReceiveBlock
import cash.atto.commons.AttoSendBlock
import cash.atto.commons.AttoTransaction
import cash.atto.commons.AttoVote
import cash.atto.commons.AttoVoterWeight
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.reflect.KClass

internal class AttoSerializedModelSchemas(
    private val schemas: MutableMap<String, Schema<*>>,
) {
    private val blockModels =
        linkedMapOf(
            AttoSendBlock::class to AttoSendBlock.serializer().descriptor,
            AttoReceiveBlock::class to AttoReceiveBlock.serializer().descriptor,
            AttoOpenBlock::class to AttoOpenBlock.serializer().descriptor,
            AttoChangeBlock::class to AttoChangeBlock.serializer().descriptor,
        )
    private val models =
        blockModels +
            mapOf(
                AttoAccount::class to AttoAccount.serializer().descriptor,
                AttoAccountEntry::class to AttoAccountEntry.serializer().descriptor,
                AttoReceivable::class to AttoReceivable.serializer().descriptor,
                AttoTransaction::class to AttoTransaction.serializer().descriptor,
                AttoVote::class to AttoVote.serializer().descriptor,
                AttoVoterWeight::class to AttoVoterWeight.serializer().descriptor,
            )

    fun customize() {
        val neededModels = models.filterKeys { it in blockModels || it.simpleName in schemas }
        val resolved = mutableMapOf<String, Schema<*>>()
        (neededModels.keys + AttoBlock::class).forEach { model ->
            ModelConverters.getInstance().readAll(AnnotatedType(model.java)).forEach { (name, schema) ->
                resolved[name] = schema
                schemas.putIfAbsent(name, schema)
            }
        }

        val blockFields = blockModels.values.map(::fieldNames).reduce { left, right -> left.intersect(right) }
        canonicalize(AttoBlock::class, blockFields, blockFields, resolved, blockModels.values.map { it.serialName })
        neededModels.forEach { (model, descriptor) ->
            val required =
                (0 until descriptor.elementsCount)
                    .filterNot { descriptor.isElementOptional(it) || descriptor.getElementDescriptor(it).isNullable }
                    .map { descriptor.getElementName(it) }
                    .toSet()
            canonicalize(
                model,
                fieldNames(descriptor),
                required,
                resolved,
                if (model in
                    blockModels
                ) {
                    listOf(descriptor.serialName)
                } else {
                    null
                },
            )
        }
    }

    private fun fieldNames(descriptor: SerialDescriptor): Set<String> =
        (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()

    private fun canonicalize(
        model: KClass<*>,
        fields: Set<String>,
        required: Set<String>,
        resolved: Map<String, Schema<*>>,
        blockTypes: List<String>?,
    ) {
        val name = model.simpleName!!
        val schema = schemas.getValue(name)
        val allowed = if (blockTypes == null) fields else fields + "type"
        val properties = flattenProperties(resolved.getValue(name)) + flattenProperties(schema)
        pruneProperties(schema, allowed)
        schema.properties =
            allowed
                .associateWith { field ->
                    if (field == "type" && blockTypes != null) {
                        StringSchema()._enum(blockTypes)
                    } else {
                        requireNotNull(properties[field]) { "Missing serialized property $name.$field in generated OpenAPI model" }
                    }
                }.toMutableMap()
        schema.required = (if (blockTypes == null) required else required + "type").toMutableList()
        // Canonical models describe complete records. Flattening avoids base/variant composition cycles.
        schema.allOf = null
        schema.oneOf = null
        schema.anyOf = null
        schema.`$ref` = null
        schema.type = "object"
        schema.types = setOf("object")
    }

    private fun flattenProperties(schema: Schema<*>): Map<String, Schema<*>> {
        val properties = linkedMapOf<String, Schema<*>>()
        val visited = Collections.newSetFromMap(IdentityHashMap<Schema<*>, Boolean>())

        fun collect(part: Schema<*>) {
            if (!visited.add(part)) return
            part.`$ref`
                ?.substringAfterLast('/')
                ?.let { schemas[it] }
                ?.let(::collect)
            part.allOf?.forEach(::collect)
            part.properties?.let(properties::putAll)
        }
        collect(schema)
        return properties
    }

    private fun pruneProperties(
        schema: Schema<*>,
        allowed: Set<String>,
    ) {
        val visited = Collections.newSetFromMap(IdentityHashMap<Schema<*>, Boolean>())

        fun prune(part: Schema<*>) {
            if (!visited.add(part)) return
            part.properties?.keys?.retainAll(allowed)
            part.required = part.required?.filter { it in allowed }?.toMutableList()
            part.`$ref`
                ?.substringAfterLast('/')
                ?.let { schemas[it] }
                ?.let(::prune)
            part.allOf?.forEach(::prune)
            part.oneOf?.forEach(::prune)
            part.anyOf?.forEach(::prune)
        }
        prune(schema)
    }
}
