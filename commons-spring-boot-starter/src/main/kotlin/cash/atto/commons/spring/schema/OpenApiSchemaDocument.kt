package cash.atto.commons.spring.schema

import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Schema
import kotlin.reflect.KClass

internal class OpenApiSchemaDocument(
    private val schemas: MutableMap<String, Schema<*>>,
) {
    fun findSchema(vararg candidates: KClass<*>): Schema<*>? = candidates.firstNotNullOfOrNull { schemas[it.simpleName!!] }

    fun ensureSchema(
        clazz: KClass<*>,
        fallback: () -> Schema<*>,
    ): Schema<*> = schemas[clazz.simpleName!!] ?: fallback().also { schemas[clazz.simpleName!!] = it }

    fun putOrReplace(
        clazz: KClass<*>,
        schema: Schema<*>,
    ) {
        schemas[clazz.simpleName!!] = schema
    }

    fun setPropDesc(
        schema: Schema<*>,
        prop: String,
        desc: String,
        example: Any? = null,
    ) {
        schema.properties?.get(prop)?.let { s ->
            s.description = desc
            if (example != null) s.example = example
            return
        }
        schema.allOf?.forEach { part ->
            val refName = part.`$ref`?.substringAfterLast('/')?.let { schemas[it] }
            val target = (refName ?: part).properties?.get(prop)
            if (target != null) {
                target.description = desc
                if (example != null) target.example = example
                return
            }
        }
    }

    fun removeProps(
        schema: Schema<*>,
        vararg props: String,
    ) {
        val names = props.toSet()

        fun deref(s: Schema<*>): Schema<*> {
            val ref = s.`$ref`?.substringAfterLast('/')
            return if (ref != null) (schemas[ref] ?: s) else s
        }

        fun prune(target: Schema<*>) {
            target.properties?.keys?.removeAll(names)
            target.required = target.required?.filterNot { it in names }?.toMutableList()

            if (target is ComposedSchema) {
                target.allOf?.forEach { part -> prune(deref(part)) }
                target.oneOf?.forEach { part -> prune(deref(part)) }
                target.anyOf?.forEach { part -> prune(deref(part)) }
            }
        }

        prune(schema)
    }
}
