@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons.node

import cash.atto.commons.AttoReceivable
import kotlinx.serialization.json.Json
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName


@JsExport
@JsName("receivableToJson")
fun AttoReceivable.toJson(): String {
    return Json.encodeToString(this)
}

@JsExport
@JsName("receivableFromJson")
fun String.toReceivable(): AttoReceivable {
    return Json.decodeFromString<AttoReceivable>(this)
}

