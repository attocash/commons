@file:Suppress("ktlint:standard:class-signature")

package cash.atto.commons.utils

@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FILE,
)
expect annotation class JsExportForJs() // keep the ()
