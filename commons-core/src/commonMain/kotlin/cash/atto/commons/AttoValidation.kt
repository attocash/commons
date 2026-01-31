@file:OptIn(ExperimentalJsExport::class)

package cash.atto.commons

import cash.atto.commons.utils.JsExportForJs
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@JsExportForJs
sealed interface AttoValidation {
    val isValid: Boolean get() = this is Ok

    fun getError(): String?

    @JsExport.Ignore
    object Ok : AttoValidation {
        override fun getError(): String? = null
    }

    @JsExport.Ignore
    class Error(
        private val error: String,
    ) : AttoValidation {
        override fun getError(): String = error
    }
}
