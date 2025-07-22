@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons.node

import cash.atto.commons.AttoReceivable
import cash.atto.commons.utils.JsExportForJs
import kotlinx.serialization.json.Json
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@JsExportForJs
@JsName("receivableToJson")
fun AttoReceivable.toJson(): String = Json.encodeToString(this)

@JsExportForJs
@JsName("receivableFromJson")
fun String.toReceivable(): AttoReceivable = Json.decodeFromString<AttoReceivable>(this)
