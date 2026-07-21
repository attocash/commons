@file:JvmName("AttoTransactions")

package cash.atto.commons

import kotlinx.coroutines.runBlocking
import kotlin.jvm.JvmName

fun AttoTransaction.validateBlocking(): AttoValidation = runBlocking { validate() }

fun AttoTransaction.isValidBlocking(): Boolean = runBlocking { isValid() }
