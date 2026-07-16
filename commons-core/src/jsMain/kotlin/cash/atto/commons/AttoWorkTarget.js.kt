@file:JsExport
@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons

fun attoBlockWorkTarget(block: AttoBlock): String = block.getTarget().toString()
